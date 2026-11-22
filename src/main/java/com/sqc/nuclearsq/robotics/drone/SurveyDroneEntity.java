package com.sqc.nuclearsq.robotics.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

public class SurveyDroneEntity extends Mob {
    public static final int BATTERY = 40_000, FLY_COST = 4, CHARGE_PER_SECOND = 4_000, ZONE_MAX = 64, HEIGHT = 4, RESERVE = BATTERY * 3 / 10;
    public static final int RANGE = 48, SCAN = 16, SCAN_MAX = 512, MANUAL = 4;
    private static final double SPEED = 0.3, FLY_SPEED = 0.35, CLIMB = 0.25;
    private static final int INPUT_TIMEOUT = 40, MEASURE_EVERY = 5, SCAN_EVERY = 10;
    private static final Map<UUID, UUID> CONTROL = new HashMap<>();

    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(SurveyDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(SurveyDroneEntity.class, EntityDataSerializers.BOOLEAN);

    private int phase, leg;
    private List<Vec3> route = List.of();
    @Nullable private SurveyMap map;
    private Vec3 home = Vec3.ZERO;
    public float rotor, rotorO;
    @Nullable private UUID controller;
    @Nullable private ServerPlayer pilot;
    private int inForward, inStrafe, inUp;
    private long inTick;
    @Nullable public java.util.function.DoubleUnaryOperator viewYaw, viewPitch;

    public SurveyDroneEntity(EntityType<? extends SurveyDroneEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        noPhysics = true;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENERGY, BATTERY).define(FLYING, false);
    }

    public int energy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int fe) {
        entityData.set(ENERGY, Mth.clamp(fe, 0, BATTERY));
    }

    public boolean flying() {
        return entityData.get(FLYING);
    }

    @Nullable
    public SurveyMap map() {
        return map;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public void travel(Vec3 input) {
    }

    public String start(List<BlockPos> zone) {
        if (zone.size() != 2) return "no_zone";
        BlockPos a = zone.get(0), b = zone.get(1);
        int w = Math.abs(a.getX() - b.getX()) + 1, h = Math.abs(a.getZ() - b.getZ()) + 1;
        if (Math.max(w, h) > ZONE_MAX) return "zone_too_big";
        if (energy() < RESERVE * 2) return "no_power";
        if (phase == 0) home = position();
        int x0 = Math.min(a.getX(), b.getX()), z0 = Math.min(a.getZ(), b.getZ());
        int cw = (w + SurveyMap.CELL - 1) / SurveyMap.CELL, ch = (h + SurveyMap.CELL - 1) / SurveyMap.CELL;
        map = SurveyMap.empty(x0, z0, cw, ch);
        route = route(map);
        leg = 0;
        phase = 1;
        entityData.set(FLYING, true);
        return "started";
    }

    public void recall() {
        if (phase == 1) phase = 2;
        else if (phase == MANUAL) stopControl("");
    }

    @Nullable
    public static SurveyDroneEntity controlledBy(ServerPlayer p) {
        UUID id = CONTROL.get(p.getUUID());
        return id != null && p.serverLevel().getEntity(id) instanceof SurveyDroneEntity d ? d : null;
    }

    public boolean controlled() {
        return controller != null;
    }

    public void startControl(ServerPlayer p, ItemStack tablet) {
        if (energy() <= RESERVE) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone.no_power"), true);
            return;
        }
        if (p.distanceToSqr(this) > (double) RANGE * RANGE) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone.out_of_range", RANGE), true);
            return;
        }
        if (controller != null && !controller.equals(p.getUUID())) stopControl("taken");
        if (phase == 0) home = position();
        List<BlockPos> zone = tablet.getOrDefault(DroneRegistry.ZONE.get(), List.of());
        if (zone.size() == 2 && Math.max(Math.abs(zone.get(0).getX() - zone.get(1).getX()), Math.abs(zone.get(0).getZ() - zone.get(1).getZ())) < ZONE_MAX) {
            BlockPos a = zone.get(0), b = zone.get(1);
            int x0 = Math.min(a.getX(), b.getX()), z0 = Math.min(a.getZ(), b.getZ());
            int w = Math.abs(a.getX() - b.getX()) + 1, h = Math.abs(a.getZ() - b.getZ()) + 1;
            if (map == null || map.x0() != x0 || map.z0() != z0) map = SurveyMap.empty(x0, z0, (w + SurveyMap.CELL - 1) / SurveyMap.CELL, (h + SurveyMap.CELL - 1) / SurveyMap.CELL);
        } else if (map == null || !inside(map, getX(), getZ())) {
            int x0 = Mth.floor(getX()) - ZONE_MAX / 2, z0 = Mth.floor(getZ()) - ZONE_MAX / 2;
            map = SurveyMap.empty(x0, z0, ZONE_MAX / SurveyMap.CELL, ZONE_MAX / SurveyMap.CELL);
        }
        phase = MANUAL;
        controller = p.getUUID();
        pilot = p;
        CONTROL.put(controller, getUUID());
        inForward = inStrafe = inUp = 0;
        inTick = level().getGameTime();
        entityData.set(FLYING, true);
        PacketDistributor.sendToPlayer(p, new DronePayloads.State(getId()));
        p.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone.fly_started"), true);
    }

    static boolean inside(SurveyMap m, double x, double z) {
        return x >= m.x0() && z >= m.z0() && x < m.x0() + m.w() * SurveyMap.CELL && z < m.z0() + m.h() * SurveyMap.CELL;
    }

    public void stopControl(String reason) {
        if (controller == null) return;
        UUID old = controller;
        ServerPlayer sp = pilot;
        controller = null;
        pilot = null;
        CONTROL.remove(old, getUUID());
        inForward = inStrafe = inUp = 0;
        if (phase == MANUAL) phase = 2;
        setXRot(0);
        if (sp != null && !sp.isRemoved()) {
            if (map != null) {
                for (ItemStack st : sp.getInventory().items) storeMap(st);
                for (ItemStack st : sp.getInventory().offhand) storeMap(st);
            }
            PacketDistributor.sendToPlayer(sp, new DronePayloads.State(-1));
            if (!reason.isEmpty()) sp.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone.stop." + reason), true);
        }
    }

    private void storeMap(ItemStack st) {
        if (st.is(DroneRegistry.TABLET.get()) && getUUID().equals(st.get(DroneRegistry.BOUND.get())))
            st.set(DroneRegistry.MAP.get(), new SurveyMap(map.x0(), map.z0(), map.w(), map.h(), map.cells().clone()));
    }

    public void input(int forward, int strafe, int up, float yaw, float pitch) {
        inForward = Mth.clamp(forward, -1, 1);
        inStrafe = Mth.clamp(strafe, -1, 1);
        inUp = Mth.clamp(up, -1, 1);
        if (Float.isFinite(yaw)) setYRot(Mth.wrapDegrees(yaw));
        if (Float.isFinite(pitch)) setXRot(Mth.clamp(pitch, -90f, 90f));
        yBodyRot = yHeadRot = getYRot();
        inTick = level().getGameTime();
    }

    @Override
    public float getViewYRot(float partial) {
        return viewYaw != null ? (float) viewYaw.applyAsDouble(partial) : super.getViewYRot(partial);
    }

    @Override
    public float getViewXRot(float partial) {
        return viewPitch != null ? (float) viewPitch.applyAsDouble(partial) : super.getViewXRot(partial);
    }

    private void manualTick(ServerLevel sl) {
        long now = sl.getGameTime();
        ServerPlayer p = pilot;
        if (p == null || p.isRemoved() || !p.isAlive() || p.level() != level()) stopControl("lost");
        else if (p.distanceToSqr(this) > (double) RANGE * RANGE) stopControl("range");
        else if (now - inTick > INPUT_TIMEOUT) stopControl("no_signal");
        else if (energy() <= RESERVE) stopControl("no_power");
        if (phase != MANUAL) return;
        step(sl, now % MEASURE_EVERY == 0, now - inTick <= 4);
        if (now % SCAN_EVERY == 0) sendScan(sl, p);
    }

    public void step(ServerLevel sl, boolean measure, boolean steer) {
        setEnergy(energy() - FLY_COST);
        Vec3 v = Vec3.ZERO;
        if (steer) {
            Vec3 fwd = Vec3.directionFromRotation(0, getYRot()), right = new Vec3(-fwd.z, 0, fwd.x);
            v = fwd.scale(inForward).add(right.scale(inStrafe));
            if (v.lengthSqr() > 1) v = v.normalize();
            v = new Vec3(v.x * FLY_SPEED, inUp * CLIMB, v.z * FLY_SPEED);
            if (getY() + v.y > sl.getMaxBuildHeight() - 1) v = new Vec3(v.x, 0, v.z);
        }
        noPhysics = false;
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        setDeltaMovement(Vec3.ZERO);
        noPhysics = true;
        if (map != null && measure && inside(map, getX(), getZ())) {
            int ci = (int) ((getX() - map.x0()) / SurveyMap.CELL), cj = (int) ((getZ() - map.z0()) / SurveyMap.CELL);
            for (int j = Math.max(0, cj - 2); j <= Math.min(map.h() - 1, cj + 2); j++) for (int i = Math.max(0, ci - 2); i <= Math.min(map.w() - 1, ci + 2); i++) {
                int gx = map.x0() + i * SurveyMap.CELL + SurveyMap.CELL / 2, gz = map.z0() + j * SurveyMap.CELL + SurveyMap.CELL / 2;
                BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos(gx, Mth.floor(getY()), gz);
                for (int k = 0; k < 64 && g.getY() > sl.getMinBuildHeight() && !sl.getBlockState(g.below()).blocksMotion(); k++) g.move(Direction.DOWN);
                map.set(i, j, RadiationManager.rateAt(sl, new Vec3(gx + 0.5, g.getY() + 1, gz + 0.5)));
            }
            com.sqc.nuclearsq.progression.Titles.mark(sl, controller, "rank:survey");
        }
    }

    private void sendScan(ServerLevel sl, ServerPlayer sp) {
        it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap found = RadiationManager.debugSources(sl, position(), SCAN);
        long[] pos = found.keySet().toLongArray();
        if (pos.length > SCAN_MAX) {
            pos = java.util.Arrays.stream(pos).boxed().sorted((a, b) -> Float.compare(found.get((long) b), found.get((long) a)))
                    .limit(SCAN_MAX).mapToLong(Long::longValue).toArray();
        }
        float[] sv = new float[pos.length];
        for (int i = 0; i < pos.length; i++) sv[i] = found.get(pos[i]);
        PacketDistributor.sendToPlayer(sp, new DronePayloads.Scan((float) RadiationManager.rateAt(sl, position()), pos, sv, map));
    }

    public boolean busy() {
        return phase != 0;
    }

    static List<Vec3> route(SurveyMap m) {
        List<Vec3> out = new ArrayList<>();
        for (int j = 0; j < m.h(); j++) {
            for (int k = 0; k < m.w(); k++) {
                int i = j % 2 == 0 ? k : m.w() - 1 - k;
                out.add(new Vec3(m.x0() + i * SurveyMap.CELL + SurveyMap.CELL / 2.0, 0, m.z0() + j * SurveyMap.CELL + SurveyMap.CELL / 2.0));
            }
        }
        return out;
    }

    private double cruise(ServerLevel sl, double x, double z) {
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + HEIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            rotorO = rotor;
            if (flying()) rotor += 1.2f;
            return;
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (phase == 0) {
            if (tickCount % 20 == 0) charge(sl);
            return;
        }
        if (phase == MANUAL) {
            manualTick(sl);
            return;
        }
        if (energy() < FLY_COST) return;
        setEnergy(energy() - FLY_COST);
        if (phase == 1 && (leg >= route.size() || energy() < RESERVE)) phase = 2;
        Vec3 t = switch (phase) {
            case 1 -> {
                Vec3 p = route.get(leg);
                yield new Vec3(p.x, cruise(sl, p.x, p.z), p.z);
            }
            case 2 -> new Vec3(home.x, Math.max(home.y + HEIGHT, cruise(sl, home.x, home.z)), home.z);
            default -> home;
        };
        Vec3 d = t.subtract(position());
        if (d.length() <= SPEED) {
            setPos(t);
            if (phase == 1) {
                BlockPos ground = BlockPos.containing(t.x, cruise(sl, t.x, t.z) - HEIGHT, t.z);
                map.set((int) ((t.x - map.x0()) / SurveyMap.CELL), (int) ((t.z - map.z0()) / SurveyMap.CELL),
                        RadiationManager.rateAt(sl, Vec3.atBottomCenterOf(ground).add(0, 1, 0)));
                leg++;
            } else if (phase == 2) phase = 3;
            else {
                phase = 0;
                entityData.set(FLYING, false);
            }
        } else {
            setPos(position().add(d.normalize().scale(SPEED)));
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            yBodyRot = yHeadRot = getYRot();
        }
    }

    public void charge(ServerLevel sl) {
        int room = BATTERY - energy();
        if (room <= 0) return;
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            for (Direction d : Direction.values()) {
                IEnergyStorage s = sl.getCapability(Capabilities.EnergyStorage.BLOCK, p, d);
                if (s == null || !s.canExtract()) continue;
                int got = s.extractEnergy(Math.min(room, CHARGE_PER_SECOND), false);
                if (got > 0) {
                    setEnergy(energy() + got);
                    return;
                }
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (busy()) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone.busy"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack st = new ItemStack(DroneRegistry.DRONE_ITEM.get());
        st.set(ModDataComponents.STORED_ENERGY.get(), energy());
        spawnAtLocation(st);
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("energy", energy());
        tag.putIntArray("job", new int[] {phase == MANUAL ? 2 : phase, leg});
        tag.putDouble("home_x", home.x);
        tag.putDouble("home_y", home.y);
        tag.putDouble("home_z", home.z);
        if (map != null) {
            tag.putIntArray("map", new int[] {map.x0(), map.z0(), map.w(), map.h()});
            tag.putByteArray("cells", map.cells());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setEnergy(tag.contains("energy") ? tag.getInt("energy") : BATTERY);
        home = new Vec3(tag.getDouble("home_x"), tag.getDouble("home_y"), tag.getDouble("home_z"));
        int[] m = tag.getIntArray("map");
        byte[] cells = tag.getByteArray("cells");
        map = m.length == 4 && cells.length == m[2] * m[3] ? new SurveyMap(m[0], m[1], m[2], m[3], cells) : null;
        int[] job = tag.getIntArray("job");
        phase = job.length == 2 && (map != null || job[0] > 1) ? job[0] : 0;
        leg = job.length == 2 ? job[1] : 0;
        route = map != null ? route(map) : List.of();
        if (phase == 1 && map == null) phase = 2;
        if (phase == MANUAL) phase = 2;
        entityData.set(FLYING, phase != 0);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) stopControl("");
        super.remove(reason);
    }
}
