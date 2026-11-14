package com.sqc.nuclearsq.explosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class BlastJobs extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "nuclearsq_blast_jobs";
    private static final SavedData.Factory<BlastJobs> FACTORY = new SavedData.Factory<>(BlastJobs::new, BlastJobs::load, null);

    private static final List<BlastJob.Type<?>> TYPES = List.of(
            NuclearExplosion.ShockJob.TYPE,
            NuclearExplosion.BurnJob.TYPE,
            NuclearExplosion.CraterJob.TYPE,
            BlastAftermath.ShatterJob.TYPE,
            BlastAftermath.FlattenJob.TYPE,
            BlastAftermath.GlowJob.TYPE,
            AshFall.Job.TYPE,
            FalloutScatterJob.TYPE);
    private static final Map<String, BlastJob.Type<?>> BY_ID = new LinkedHashMap<>();
    static {
        for (BlastJob.Type<?> t : TYPES) BY_ID.put(t.id(), t);
    }

    private static final Codec<BlastJob.Type<?>> TYPE_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                BlastJob.Type<?> t = BY_ID.get(id);
                return t != null ? DataResult.success(t) : DataResult.error(() -> "unknown blast job type " + id);
            }, BlastJob.Type::id);
    public static final Codec<BlastJob> CODEC = TYPE_CODEC.dispatch("type", BlastJob::type, BlastJob.Type::codec);

    public static final class Budget {
        private long blastStart;
        int craterBlocks;
        boolean craterStopped;
        boolean burnStopped;
        private long shatterDeadline, glowDeadline, flattenDeadline;
        boolean shatterStopped, flattenStopped;
        int ash = AshFall.PER_TICK;
        int fallout = FalloutScatterJob.PER_TICK;

        Budget() {}

        private long blastStart() {
            if (blastStart == 0) blastStart = System.nanoTime();
            return blastStart;
        }

        long burnDeadline() { return blastStart() + NuclearExplosion.BURN_BUDGET_NS; }
        static final int SHARED_CRATER_BLOCKS = 80000, SHARED_TICK_MS = 22;

        private static boolean sharedCpu() {
            MinecraftServer s = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            return s != null && s.isSingleplayer();
        }

        long craterDeadline() {
            int ms = NuclearConfig.TICK_BUDGET_MS.get();
            return blastStart() + (sharedCpu() ? Math.min(ms, SHARED_TICK_MS) : ms) * 1_000_000L;
        }

        int craterPerTick() {
            int n = NuclearConfig.CRATER_BLOCKS_PER_TICK.get();
            return sharedCpu() ? Math.min(n, SHARED_CRATER_BLOCKS) : n;
        }

        long shatterDeadline() {
            if (shatterDeadline == 0) shatterDeadline = System.nanoTime() + BlastAftermath.SHATTER_BUDGET_NS;
            return shatterDeadline;
        }

        long flattenDeadline() {
            if (flattenDeadline == 0) flattenDeadline = System.nanoTime() + BlastAftermath.FLATTEN_BUDGET_NS;
            return flattenDeadline;
        }

        long glowDeadline() {
            if (glowDeadline == 0) glowDeadline = System.nanoTime() + BlastAftermath.GLOW_BUDGET_NS;
            return glowDeadline;
        }
    }

    private final List<BlastJob> jobs = new ArrayList<>();

    private static BlastJobs get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    public static void add(ServerLevel level, BlastJob job) {
        BlastJobs d = get(level);
        d.jobs.add(job);
        d.setDirty();
    }

    public static boolean pending(MinecraftServer server, Class<? extends BlastJob> type) {
        for (ServerLevel lv : server.getAllLevels())
            for (BlastJob j : get(lv).jobs) if (type.isInstance(j)) return true;
        return false;
    }

    static boolean changingBlocks(MinecraftServer server) {
        for (ServerLevel lv : server.getAllLevels())
            for (BlastJob j : get(lv).jobs) if (j.changesBlocks()) return true;
        return false;
    }

    static <T extends BlastJob> List<T> jobs(ServerLevel level, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (BlastJob j : get(level).jobs) if (type.isInstance(j)) out.add(type.cast(j));
        return out;
    }

    public int size() {
        return jobs.size();
    }

    public List<BlastJob> jobs() {
        return Collections.unmodifiableList(jobs);
    }

    public static BlastJobs load(CompoundTag tag, HolderLookup.Provider registries) {
        BlastJobs d = new BlastJobs();
        ListTag list = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Tag t = list.get(i);
            CODEC.parse(NbtOps.INSTANCE, t)
                    .resultOrPartial(err -> LOGGER.warn("Skipping blast job {}: {}", t, err))
                    .ifPresent(d.jobs::add);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlastJob j : jobs)
            CODEC.encodeStart(NbtOps.INSTANCE, j)
                    .resultOrPartial(err -> LOGGER.warn("Could not save blast job {}: {}", j.type().id(), err))
                    .ifPresent(list::add);
        tag.put("jobs", list);
        return tag;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<ServerLevel> levels = new ArrayList<>();
        List<BlastJobs> data = new ArrayList<>();
        for (ServerLevel lv : event.getServer().getAllLevels()) {
            BlastJobs d = get(lv);
            if (d.jobs.isEmpty()) continue;
            levels.add(lv);
            data.add(d);
        }
        if (data.isEmpty()) {
            BlastShield.clear();
            return;
        }
        Budget budget = new Budget();
        for (BlastJob.Type<?> type : TYPES)
            for (int k = 0; k < data.size(); k++) data.get(k).tick(levels.get(k), type, budget);
        for (BlastJobs d : data) d.setDirty();
    }

    private void tick(ServerLevel level, BlastJob.Type<?> type, Budget budget) {
        for (int i = 0; i < jobs.size(); i++) {
            BlastJob j = jobs.get(i);
            if (j.type() != type) continue;
            if (j.tick(level, budget)) jobs.remove(i--);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel lv : event.getServer().getAllLevels())
            for (BlastJob j : get(lv).jobs) j.onStopping(lv);
    }
}
