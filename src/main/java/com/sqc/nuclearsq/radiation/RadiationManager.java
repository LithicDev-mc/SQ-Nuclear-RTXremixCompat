package com.sqc.nuclearsq.radiation;

import com.sqc.nuclearsq.network.RadiationDosePayload;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class RadiationManager {
    private RadiationManager() {}

    public static final double CORE_SV_H = RadiationValues.EXPLODED_CORE_SV_H;
    public static final int CORE_LIFE = 20 * 60 * 20;
    public static final double FALLOUT_SV_H = RadiationValues.FALLOUT_SV_H;
    public static final double AMBIENT_SV_H = RadiationValues.AMBIENT_SV_H;
    public static final double DOSE_TIME_SCALE = RadiationValues.DOSE_TIME_SCALE;
    public static final double SICK_DOSE_SV = RadiationValues.SV_NAUSEA;
    public static final double FULL_DAMAGE_SV = RadiationValues.SV_LETHAL;
    public static final int FALLOUT_LIFE = 20 * 60 * 15;
    public static final int FALLOUT_RANGE = 90;
    public static final int FALLOUT_COUNT = 700;
    public static final int FALLOUT_MAX_COUNT = 20000;
    public static final double REACH = 130.0;
    private static final double REACH2 = REACH * REACH;
    private static final double DECAY = RadiationValues.DOSE_DECAY;

    private static final int EMITTER_LIFE = 50;
    public static final double EMITTER_REACH = 32.0;
    public static final double RESIDUE_CARRY_SV_H = RadiationValues.RESIDUE_CARRY_SV_H;
    private static final Set<UUID> SENT = new HashSet<>();
    private static final Map<UUID, double[]> RECEIVED = new HashMap<>();

    public static void addCore(ServerLevel lv, Vec3 c, double scale) {
        long now = lv.getGameTime();
        RadiationWorldData.get(lv).addCore(new RadiationWorldData.Core(c, CORE_SV_H * scale, now, now + CORE_LIFE));
    }

    public static void scatterFallout(ServerLevel lv, Vec3 c, int craterRadius, double scale, int falloutRadius) {
        int inner = craterRadius + 2;
        int range = Math.max(inner + 10, falloutRadius);
        double grow = range / Math.max(1.0, FALLOUT_RANGE * scale);
        int count = (int) Math.min(FALLOUT_MAX_COUNT, Math.round(FALLOUT_COUNT * scale * scale * grow * grow));
        com.sqc.nuclearsq.explosion.BlastJobs.add(lv, new com.sqc.nuclearsq.explosion.FalloutScatterJob(c, inner, range, count));
    }

    public static boolean falloutPending() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && com.sqc.nuclearsq.explosion.BlastJobs.pending(server, com.sqc.nuclearsq.explosion.FalloutScatterJob.class);
    }

    public static void addFallout(ServerLevel lv, BlockPos pos) {
        RadiationWorldData.get(lv).addFallout(pos.asLong());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 20 != 0) return;

        Set<UUID> seen = new HashSet<>();
        for (ServerLevel lv : server.getAllLevels()) {
            RadiationWorldData data = RadiationWorldData.get(lv);
            data.pruneCores(lv.getGameTime());
            data.emitters.values().removeIf(em -> now >= em.until());
            for (var en : data.emitters.long2ObjectEntrySet()) {
                if (lv.random.nextDouble() < RadiationValues.SOURCE_DEPOSIT_CHANCE) depositAround(lv, BlockPos.of(en.getLongKey()), en.getValue().svH());
            }
            boolean any = data.hasSources() || lv.players().stream().anyMatch(p -> carried(p) > 0);
            if (any) {
                for (LivingEntity e : candidates(lv, data)) {
                    if (!e.isAlive()) continue;
                    if (e instanceof Player pl && pl.isSpectator()) continue;
                    seen.add(e.getUUID());
                    tickEntity(lv, e, now);
                }
            }
            for (var it = data.dosed.iterator(); it.hasNext();) {
                UUID id = it.next();
                if (seen.contains(id)) continue;
                if (!(lv.getEntity(id) instanceof LivingEntity e) || !e.isAlive()) {
                    it.remove();
                    continue;
                }
                double d = e instanceof Player pl && pl.isSpectator() ? 0.0 : doseOf(e) * DECAY;
                storeDose(e, d);
                if (d < 1e-9) it.remove();
                if (e instanceof ServerPlayer sp) sendDose(sp, d);
            }
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            double[] r = RECEIVED.remove(sp.getUUID());
            if (RadiationDebug.enabled(sp)) sendDebug(sp);
            else if (DEBUG_SENT.remove(sp.getUUID())) clearDebug(sp);
            if (sp.isSpectator()) continue;
            if (r == null) DosimeterItem.tick(sp, 0, AMBIENT_SV_H);
            else DosimeterItem.tick(sp, r[0], r[1] + AMBIENT_SV_H, r[2] + AMBIENT_SV_H, r[3]);
        }
    }

    @SubscribeEvent
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel lv && event.getEntity() instanceof LivingEntity e && e.hasData(RadiationAttachments.DOSE)) {
            RadiationWorldData.get(lv).dosed.add(e.getUUID());
        }
    }

    private static void sendDose(ServerPlayer sp, double dose) {
        if (dose > 1e-4) {
            SENT.add(sp.getUUID());
            PacketDistributor.sendToPlayer(sp, new RadiationDosePayload((float) dose));
        } else if (SENT.remove(sp.getUUID())) {
            PacketDistributor.sendToPlayer(sp, new RadiationDosePayload(0f));
        }
    }

    public static final int DEBUG_RANGE = 32, DEBUG_MAX = 1024;

    private static void sendDebug(ServerPlayer sp) {
        if (sp instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        ServerLevel lv = sp.serverLevel();
        Vec3 p = sp.position();
        it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap found = debugSources(lv, p, DEBUG_RANGE);
        long[] pos = found.keySet().toLongArray();
        if (pos.length > DEBUG_MAX) {
            pos = java.util.Arrays.stream(pos).boxed()
                    .sorted((a, b) -> Float.compare(found.get((long) b), found.get((long) a)))
                    .limit(DEBUG_MAX).mapToLong(Long::longValue).toArray();
        }
        float[] sv = new float[pos.length];
        byte[] kind = new byte[pos.length];
        RadiationWorldData data = RadiationWorldData.get(lv);
        for (int i = 0; i < pos.length; i++) {
            sv[i] = found.get(pos[i]);
            kind[i] = debugKind(lv, data, pos[i]);
        }
        if (pos.length == 0 && !DEBUG_SENT.remove(sp.getUUID())) return;
        if (pos.length > 0) DEBUG_SENT.add(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new com.sqc.nuclearsq.network.RadiationDebugPayload(pos, sv, kind));
    }

    private static void clearDebug(ServerPlayer sp) {
        if (sp instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        PacketDistributor.sendToPlayer(sp, new com.sqc.nuclearsq.network.RadiationDebugPayload(new long[0], new float[0], new byte[0]));
    }

    private static final Set<UUID> DEBUG_SENT = new HashSet<>();

    public static byte debugKind(ServerLevel lv, RadiationWorldData data, long pos) {
        if (isActiveSource(lv, BlockPos.of(pos))) return com.sqc.nuclearsq.network.RadiationDebugPayload.KIND_SOURCE;
        if (data.hasFallout(pos)) return com.sqc.nuclearsq.network.RadiationDebugPayload.KIND_FALLOUT;
        return com.sqc.nuclearsq.network.RadiationDebugPayload.KIND_CONTAMINATION;
    }

    public static it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap debugSources(ServerLevel lv, Vec3 p, int range) {
        it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap out = new it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap();
        double r2 = (double) range * range;
        RadiationWorldData data = RadiationWorldData.get(lv);
        long now = lv.getServer().getTickCount();
        for (var en : data.emitters.long2ObjectEntrySet()) {
            if (en.getValue().until() < now) continue;
            if (Vec3.atCenterOf(BlockPos.of(en.getLongKey())).distanceToSqr(p) <= r2) out.addTo(en.getLongKey(), (float) en.getValue().svH());
        }
        Contamination.Zones z = data.zones;
        z.forNear(p, range, l -> {
            if (Vec3.atCenterOf(BlockPos.of(l)).distanceToSqr(p) <= r2) out.addTo(l, z.get(BlockPos.of(l)));
        });
        data.fallout.forNear(p, range, l -> {
            if (Vec3.atCenterOf(BlockPos.of(l)).distanceToSqr(p) <= r2) out.addTo(l, (float) FALLOUT_SV_H);
        });
        return out;
    }

    private static final int CELL = 64;

    private static Iterable<LivingEntity> candidates(ServerLevel lv, RadiationWorldData data) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet cells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (long c : data.zones.index.chunks()) addCells(cells, ChunkPos.getX(c) * 16.0, ChunkPos.getZ(c) * 16.0, 16, Contamination.REACH);
        for (long p : data.emitters.keySet()) addCells(cells, BlockPos.getX(p), BlockPos.getZ(p), 1, EMITTER_REACH);
        for (long c : data.fallout.chunks()) addCells(cells, ChunkPos.getX(c) * 16.0, ChunkPos.getZ(c) * 16.0, 16, REACH);
        for (RadiationWorldData.Core k : data.cores) addCells(cells, k.pos().x, k.pos().z, 0, REACH);
        int minY = lv.getMinBuildHeight(), maxY = lv.getMaxBuildHeight();
        java.util.Map<UUID, LivingEntity> out = new java.util.LinkedHashMap<>();
        for (long cell : cells) {
            double x = ChunkPos.getX(cell) * (double) CELL, z = ChunkPos.getZ(cell) * (double) CELL;
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(x, minY, z, x + CELL, maxY, z + CELL))) {
                out.putIfAbsent(e.getUUID(), e);
            }
        }
        for (ServerPlayer p : lv.players()) out.putIfAbsent(p.getUUID(), p);
        for (UUID id : data.dosed) {
            if (!out.containsKey(id) && lv.getEntity(id) instanceof LivingEntity e) out.put(id, e);
        }
        return out.values();
    }

    private static void addCells(it.unimi.dsi.fastutil.longs.LongOpenHashSet cells, double x, double z, double size, double reach) {
        int x0 = Mth.floor((x - reach) / CELL), x1 = Mth.floor((x + size + reach) / CELL);
        int z0 = Mth.floor((z - reach) / CELL), z1 = Mth.floor((z + size + reach) / CELL);
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) cells.add(ChunkPos.asLong(cx, cz));
    }

    public static void tickOnce(ServerLevel lv, LivingEntity e) {
        tickEntity(lv, e, lv.getServer().getTickCount());
    }

    public static double[] takeReceived(Player p) {
        return RECEIVED.remove(p.getUUID());
    }

    private static void tickEntity(ServerLevel lv, LivingEntity e, long now) {
        Vec3 ep = e.position().add(0, e.getBbHeight() * 0.5, 0);
        double outside = rateAt(lv, ep);
        double shield = HazmatArmorItem.shielding(e);
        if (shield > 0) HazmatArmorItem.wear(e, outside);
        if (e instanceof Player p) {
            Contamination.tickBody(lv, p, shield);
            RespiratorItem.tick(lv, p, outside);
            ItemContamination.tickPlayer(lv, p);
        }
        double rate = (outside * (1 - shield) + (e instanceof Player p ? carried(p) : 0)) * medicineFactor(e);
        if (e instanceof ServerPlayer sp) RECEIVED.put(sp.getUUID(), new double[] {rate / 3600.0 * DOSE_TIME_SCALE, rate, outside, shield});

        double dose = doseOf(e) * DECAY + rate / 3600.0 * DOSE_TIME_SCALE;
        storeDose(e, dose);
        if (dose >= 1e-9) RadiationWorldData.get(lv).dosed.add(e.getUUID());
        if (e instanceof ServerPlayer sp) sendDose(sp, dose < 1e-9 ? 0 : dose);
        if (dose < 1e-9) return;

        RadiationSickness.tick(lv, e, dose, now);
    }

    public static void reduceDose(LivingEntity e, double sv) {
        if (!e.hasData(RadiationAttachments.DOSE)) return;
        double nd = Math.max(0.0, doseOf(e) - sv);
        storeDose(e, nd);
        if (e instanceof ServerPlayer sp) {
            if (nd > 1e-4) PacketDistributor.sendToPlayer(sp, new RadiationDosePayload((float) nd));
            else if (SENT.remove(sp.getUUID())) PacketDistributor.sendToPlayer(sp, new RadiationDosePayload(0f));
        }
    }

    public static double doseOf(LivingEntity e) {
        Double d = e.getExistingDataOrNull(RadiationAttachments.DOSE);
        return d == null ? 0.0 : d;
    }

    private static void storeDose(LivingEntity e, double sv) {
        if (sv < 1e-9) e.removeData(RadiationAttachments.DOSE);
        else e.setData(RadiationAttachments.DOSE, sv);
    }

    public static void emit(ServerLevel lv, BlockPos pos, double svH) {
        if (svH <= 0) return;
        pos = com.sqc.nuclearsq.compat.sable.SableCompat.toWorld(lv, pos);
        RadiationWorldData.get(lv).emitters.put(pos.asLong(), new RadiationWorldData.Emitter(svH, lv.getServer().getTickCount() + EMITTER_LIFE));
    }

    public static void leak(ServerLevel lv, BlockPos pos, double svH) {
        pos = com.sqc.nuclearsq.compat.sable.SableCompat.toWorld(lv, pos);
        emit(lv, pos, svH);
        Contamination.depositNear(lv, pos, RadiationValues.LEAK_DEPOSIT_RANGE, RadiationValues.LEAK_DEPOSIT_SV_H);
        for (var ie : lv.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(pos).inflate(RadiationValues.LEAK_DEPOSIT_RANGE))) {
            ItemContamination.add(ie.getItem(), RadiationValues.LEAK_DEPOSIT_SV_H);
        }
        double r2 = RadiationValues.LEAK_DEPOSIT_RANGE * RadiationValues.LEAK_DEPOSIT_RANGE;
        for (ServerPlayer p : lv.players()) {
            if (p.isSpectator() || p.position().distanceToSqr(Vec3.atCenterOf(pos)) > r2) continue;
            breatheLeak(p);
        }
    }

    public static void breatheLeak(Player p) {
        double shield = HazmatArmorItem.shielding(p);
        Contamination.setBody(p, Contamination.body(p) + RadiationValues.LEAK_AIR_BODY_SV_H * (1 - shield) * iodineFactor(p) * RespiratorItem.airFactor(p));
        RespiratorItem.leakWear(p);
    }

    public static void depositAround(ServerLevel lv, BlockPos pos, double svH) {
        if (svH < RadiationValues.SOURCE_DEPOSIT_MIN_SV_H) return;
        float amount = (float) (svH * RadiationValues.SOURCE_DEPOSIT_FRACTION);
        Contamination.depositNear(lv, pos, RadiationValues.SOURCE_DEPOSIT_RANGE, amount);
    }

    public static void setDose(LivingEntity e, double sv) {
        storeDose(e, sv);
        if (sv >= 1e-9 && e.level() instanceof ServerLevel lv) RadiationWorldData.get(lv).dosed.add(e.getUUID());
    }

    public static boolean isActiveSource(ServerLevel lv, BlockPos pos) {
        return emitted(lv, pos) > 0;
    }

    public static double emitted(ServerLevel lv, BlockPos pos) {
        pos = com.sqc.nuclearsq.compat.sable.SableCompat.toWorld(lv, pos);
        RadiationWorldData.Emitter e = RadiationWorldData.get(lv).emitters.get(pos.asLong());
        return e == null || e.until() < lv.getServer().getTickCount() ? 0 : e.svH();
    }

    public static double carried(Player p) {
        var residue = CatalogRegistry.ITEM_MAP.get("filter_residue_sqc");
        int n = residue == null ? 0 : p.getInventory().countItem(residue.get());
        return n * RESIDUE_CARRY_SV_H + com.sqc.nuclearsq.spent.SpentFuel.carried(p) + ItemContamination.carried(p)
                + Contamination.body(p) * iodineFactor(p);
    }

    private static long until(LivingEntity e, java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Long>> type) {
        Long t = e.getExistingDataOrNull(type);
        return t == null ? 0 : t;
    }

    public static void takeIodine(Player p) {
        p.setData(RadiationAttachments.IODINE_UNTIL, p.level().getGameTime() + RadiationValues.IODINE_TICKS);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.sqc.nuclearsq.registry.ModEffects.IODINE, RadiationValues.IODINE_TICKS, 0, false, false, true));
        if (p instanceof ServerPlayer sp && !(sp instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.iodine", RadiationValues.IODINE_TICKS / 1200), true);
        }
    }

    public static boolean iodineActive(Player p) {
        return until(p, RadiationAttachments.IODINE_UNTIL) > p.level().getGameTime();
    }


    public static void takeMedicine(LivingEntity e) {
        e.setData(RadiationAttachments.MEDICINE_UNTIL, e.level().getGameTime() + RadiationValues.MEDICINE_PROTECT_TICKS);
        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.sqc.nuclearsq.registry.ModEffects.RADIATION_PROTECTION,
                RadiationValues.MEDICINE_PROTECT_TICKS, 0, false, false, true));
    }

    public static int medicineTicksLeft(LivingEntity e) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, until(e, RadiationAttachments.MEDICINE_UNTIL) - e.level().getGameTime()));
    }

    public static int iodineTicksLeft(Player p) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, until(p, RadiationAttachments.IODINE_UNTIL) - p.level().getGameTime()));
    }

    public static double medicineFactor(LivingEntity e) {
        return until(e, RadiationAttachments.MEDICINE_UNTIL) > e.level().getGameTime() ? RadiationValues.MEDICINE_PROTECT_FACTOR : 1.0;
    }

    public static double iodineFactor(Player p) {
        return iodineActive(p) || RespiratorItem.comboActive(p) ? RadiationValues.IODINE_FACTOR : 1.0;
    }

    public static double rateAt(ServerLevel lv, Vec3 ep) {
        RadiationWorldData data = RadiationWorldData.get(lv);
        double machines = Contamination.rateAt(lv, ep);
        for (var en : data.emitters.long2ObjectEntrySet()) {
            double d2 = dist2(en.getLongKey(), ep);
            if (d2 > EMITTER_REACH * EMITTER_REACH) continue;
            machines += sourceRate(lv, BlockPos.of(en.getLongKey()), en.getValue().svH(), ep);
        }
        long now = lv.getGameTime();
        double rate = 0;
        for (RadiationWorldData.Core k : data.cores) {
            double d2 = k.pos().distanceToSqr(ep);
            if (d2 > REACH2) continue;
            double s = k.rateAt(now);
            rate += shielded(lv, BlockPos.containing(k.pos()), s, s / Math.max(d2, 1.0), Shielding.factor(lv, k.pos(), ep), ep);
        }
        double[] sum = {0};
        data.fallout.forNear(ep, REACH, l -> {
            double d2 = dist2(l, ep);
            if (d2 <= REACH2) sum[0] += FALLOUT_SV_H / Math.max(d2, 1.0);
        });
        if (sum[0] > 0 && !lv.canSeeSky(BlockPos.containing(ep))) sum[0] *= RadiationValues.FALLOUT_ROOF_FACTOR;
        return rate + machines + sum[0];
    }

    public static double sourceRate(ServerLevel lv, BlockPos source, double svH, Vec3 at) {
        double q = svH / Math.max(Vec3.atCenterOf(source).distanceToSqr(at), 1.0);
        if (q * 100 < AMBIENT_SV_H) return 0;
        return shielded(lv, source, svH, q, Shielding.factor(lv, source, at), at);
    }

    private static double shielded(ServerLevel lv, BlockPos source, double svH, double q, double factor, Vec3 at) {
        double direct = q * factor;
        if (factor >= 0.5) return direct;
        return direct + svH * RadiationValues.SCATTER_FACTOR * Scatter.share(lv, source, at);
    }

    private static double dist2(long packed, Vec3 p) {
        double dx = BlockPos.getX(packed) + 0.5 - p.x;
        if (dx > REACH || dx < -REACH) return Double.MAX_VALUE;
        double dz = BlockPos.getZ(packed) + 0.5 - p.z;
        if (dz > REACH || dz < -REACH) return Double.MAX_VALUE;
        double dy = BlockPos.getY(packed) + 0.5 - p.y;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double nearestFallout(ServerLevel lv, Vec3 ep) {
        double[] best = {-1};
        RadiationWorldData.get(lv).fallout.forNear(ep, REACH, l -> {
            double d2 = dist2(l, ep);
            if (d2 <= REACH2 && (best[0] < 0 || d2 < best[0] * best[0])) best[0] = Math.sqrt(d2);
        });
        return best[0];
    }

    public static String fmtRate(double svH) {
        if (svH * 1000.0 >= 1.0) return String.format(Locale.ROOT, "%.2f mSv/h", svH * 1000.0);
        return String.format(Locale.ROOT, "%.2f \u00B5Sv/h", svH * 1e6);
    }

    public static void forgetFallout(ServerLevel lv, BlockPos pos) {
        RadiationWorldData.get(lv).removeFallout(pos.asLong());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Shielding.clear();
        Scatter.clear();
        RadiationSickness.clear();
        SENT.clear();
        RECEIVED.clear();
        DEBUG_SENT.clear();
    }

    @SubscribeEvent
    public static void onBlockChanged(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) Scatter.markDirty(sl, event.getPos());
    }
}
