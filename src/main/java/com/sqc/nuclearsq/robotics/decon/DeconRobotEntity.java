package com.sqc.nuclearsq.robotics.decon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DeconSprayerItem;
import com.sqc.nuclearsq.radiation.ItemContamination;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeconRobotEntity extends Mob {
    public static final int BATTERY = 50_000, WASTE_MAX = 200, RANGE = 48;
    public static final int AGENT_MAX = 1000, AGENT_COST = 5;
    public static final int ARM_TICKS = 10;
    public static final int MOVE_COST = 10, CLEAN_COST = 60;
    public static final int CHARGE_PER_SECOND = 10_000;
    public static final int CLEAN_PERIOD = 10;
    public static final double DAMAGE_FROM = 0.02, NOISE_FULL = 0.5;
    public static final float PLATED_FACTOR = 0.1f;
    public static final float BROKEN_AT = 2f, REPAIR_HEALTH = 10f;
    public static final int RUBBLE_PER_ITEM = 20;
    public static final float RUBBLE_SV_H = 0.002f;
    private static final double SPEED = 0.14;
    private static final float TURN_DEG = 5f;
    private static final int INPUT_TIMEOUT = 40;
    public static final int ZONE_MAX = 32, LANE = 3, RESERVE = BATTERY / 4, STUCK_TICKS = 60;

    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WASTE = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NOISE = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIATION = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> PLATED = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGENT = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARM_DOWN = SynchedEntityData.defineId(DeconRobotEntity.class, EntityDataSerializers.BOOLEAN);

    private static final Map<UUID, UUID> CONTROL = new HashMap<>();

    @Nullable private UUID controller;
    private int inForward, inTurn;
    public static final float PITCH_MAX = 60f;
    private long inTick;
    @Nullable public java.util.function.DoubleUnaryOperator orbitYaw;
    private double wear;
    private int phase, leg, stuck;
    private double best = 1e9;
    private List<Vec3> route = List.of();
    @Nullable private BlockPos zoneA, zoneB;
    private Vec3 home = Vec3.ZERO;
    private float homeYaw;
    public int arm, armO;

    public DeconRobotEntity(EntityType<? extends DeconRobotEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setXRot(8f);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENERGY, 0).define(WASTE, 0).define(NOISE, 0).define(RADIATION, 0f).define(PLATED, false).define(AGENT, 0).define(ARM_DOWN, false);
    }

    public int energy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int fe) {
        entityData.set(ENERGY, Mth.clamp(fe, 0, BATTERY));
    }

    public int waste() {
        return entityData.get(WASTE);
    }

    public void setWaste(int w) {
        entityData.set(WASTE, Mth.clamp(w, 0, WASTE_MAX));
    }

    public int agent() {
        return entityData.get(AGENT);
    }

    public void setAgent(int mb) {
        entityData.set(AGENT, Mth.clamp(mb, 0, AGENT_MAX));
    }

    public boolean armDown() {
        return entityData.get(ARM_DOWN);
    }

    public void toggleArm() {
        entityData.set(ARM_DOWN, !armDown());
        playSound(armDown() ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, 0.5f, 1.4f);
    }

    public int noise() {
        return entityData.get(NOISE);
    }

    public float radiation() {
        return entityData.get(RADIATION);
    }

    public boolean plated() {
        return entityData.get(PLATED);
    }

    public boolean broken() {
        return getHealth() <= BROKEN_AT;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Nullable
    public static DeconRobotEntity controlledBy(ServerPlayer p) {
        UUID id = CONTROL.get(p.getUUID());
        return id != null && p.serverLevel().getEntity(id) instanceof DeconRobotEntity r ? r : null;
    }

    public void startControl(ServerPlayer p) {
        if (broken()) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.broken"), true);
            return;
        }
        if (energy() < MOVE_COST) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.no_power"), true);
            return;
        }
        if (p.distanceToSqr(this) > (double) RANGE * RANGE) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.out_of_range"), true);
            return;
        }
        if (controller != null && !controller.equals(p.getUUID())) stopControl("taken");
        phase = 0;
        controller = p.getUUID();
        CONTROL.put(controller, getUUID());
        inForward = 0;
        inTurn = 0;
        inTick = level().getGameTime();
        PacketDistributor.sendToPlayer(p, new RobotPayloads.ControlState(getId()));
        p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.started"), true);
    }

    public void stopControl(String reason) {
        if (controller == null) return;
        UUID old = controller;
        controller = null;
        CONTROL.remove(old, getUUID());
        inForward = 0;
        inTurn = 0;
        entityData.set(ARM_DOWN, false);
        setXRot(8f);
        if (level() instanceof ServerLevel sl && sl.getPlayerByUUID(old) instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new RobotPayloads.ControlState(-1));
            if (!reason.isEmpty()) sp.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.stop." + reason), true);
        }
    }

    public void input(int forward, int turn, float pitch) {
        inForward = Mth.clamp(forward, -1, 1);
        inTurn = Mth.clamp(turn, -1, 1);
        if (Float.isFinite(pitch)) setXRot(Mth.clamp(pitch, -PITCH_MAX, PITCH_MAX));
        inTick = level().getGameTime();
    }

    @Override
    public float getViewYRot(float partial) {
        return orbitYaw != null ? (float) orbitYaw.applyAsDouble(partial) : super.getViewYRot(partial);
    }

    public boolean controlled() {
        return controller != null;
    }

    private boolean canWork() {
        return !broken() && energy() >= MOVE_COST;
    }

    public static int zoneSize(int a, int b) {
        return Math.abs(a - b) + 1;
    }

    public boolean zoneJob() {
        return phase > 0;
    }

    public void startZone(ServerPlayer p, List<BlockPos> zone) {
        String err = zone.size() != 2 ? "no_zone" : broken() ? "broken" : energy() < RESERVE ? "no_power"
                : Math.max(zoneSize(zone.get(0).getX(), zone.get(1).getX()), zoneSize(zone.get(0).getZ(), zone.get(1).getZ())) > ZONE_MAX ? "zone_too_big" : "";
        if (!err.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot." + err, ZONE_MAX), true);
            return;
        }
        stopControl("");
        zoneA = zone.get(0);
        zoneB = zone.get(1);
        home = position();
        homeYaw = getYRot();
        route = route(zoneA, zoneB);
        phase = 1;
        leg = 0;
        best = 1e9;
        entityData.set(ARM_DOWN, true);
        p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.zone_start"), true);
    }

    public void stopZone(ServerPlayer p) {
        goHome();
        p.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.zone_return"), true);
    }

    private void goHome() {
        phase = 2;
        best = 1e9;
        entityData.set(ARM_DOWN, false);
    }

    public static List<Vec3> route(BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX()) - 1, x1 = Math.max(a.getX(), b.getX()) + 1, z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        List<Vec3> out = new java.util.ArrayList<>();
        for (int z = z0 + 1, i = 0; z - 1 <= z1; z += LANE, i++) {
            double zc = Math.min(z, z1) + 0.5;
            out.add(new Vec3((i % 2 == 0 ? x0 : x1) + 0.5, 0, zc));
            out.add(new Vec3((i % 2 == 0 ? x1 : x0) + 0.5, 0, zc));
        }
        return out;
    }

    private void autoTick(long now) {
        if (phase == 1 && (leg >= route.size() || waste() >= WASTE_MAX || agent() < AGENT_COST || energy() < RESERVE)) goHome();
        Vec3 t = phase == 1 ? route.get(leg) : phase == 2 ? home.add(Vec3.directionFromRotation(0, homeYaw).scale(2.5)) : home;
        double dx = t.x - getX(), dz = t.z - getZ(), d = Math.sqrt(dx * dx + dz * dz);
        float diff = Mth.wrapDegrees((phase == 3 ? homeYaw : (float) Math.toDegrees(Math.atan2(-dx, dz))) - getYRot());
        int fwd = 0, turn = Math.abs(diff) > TURN_DEG ? (int) Math.signum(diff) : 0;
        if (phase == 3) {
            if (turn == 0) setYRot(homeYaw);
            fwd = turn == 0 && d > 0.3 ? -1 : 0;
            if (turn == 0 && fwd == 0) phase = 0;
        } else if (d < 0.7) {
            if (phase == 1) leg++;
            else phase = 3;
            best = 1e9;
        } else if (Math.abs(diff) < 30) fwd = 1;
        if (d < best - 0.05 || turn != 0) {
            best = Math.min(best, d);
            stuck = 0;
        } else if (++stuck > STUCK_TICKS) {
            if (phase == 1) leg++;
            else phase = (phase + 1) % 4;
            best = 1e9;
            stuck = 0;
        }
        inForward = fwd;
        inTurn = turn;
        inTick = now;
    }

    public float trackL, trackR, trackLo, trackRo;

    private void clientTick() {
        trackLo = trackL;
        trackRo = trackR;
        double yaw = Math.toRadians(getYRot());
        double fwd = (getX() - xo) * -Math.sin(yaw) + (getZ() - zo) * Math.cos(yaw);
        double turn = Math.toRadians(Mth.wrapDegrees(getYRot() - yRotO));
        trackL += (float) (fwd * 16 + turn * 7.5);
        trackR += (float) (fwd * 16 - turn * 7.5);
        if (broken() && tickCount % 8 == 0 && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.5, getZ() + (random.nextDouble() - 0.5) * 0.5,
                    0, 0.03, 0);
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void tick() {
        super.tick();
        armO = arm;
        arm = Mth.clamp(arm + (armDown() ? 1 : -1), 0, ARM_TICKS);
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!(level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        boolean driving = false;
        if (controller != null) {
            Player p = sl.getPlayerByUUID(controller);
            if (p == null || !p.isAlive() || p.level() != level()) stopControl("lost");
            else if (p.distanceToSqr(this) > (double) RANGE * RANGE) stopControl("range");
            else if (now - inTick > INPUT_TIMEOUT) stopControl("no_signal");
            else if (broken()) stopControl("broken");
            else if (energy() < MOVE_COST) stopControl("no_power");
        }
        if (phase > 0 && controller == null && canWork()) autoTick(now);
        double vy = onGround() ? -0.1 : (getDeltaMovement().y - 0.08) * 0.98;
        double mx = 0, mz = 0;
        if ((controller != null || phase > 0) && canWork() && now - inTick <= 4) {
            if (inTurn != 0) {
                setYRot(getYRot() + inTurn * TURN_DEG);
                driving = true;
            }
            if (inForward != 0) {
                Vec3 dir = Vec3.directionFromRotation(0, getYRot());
                mx = dir.x * SPEED * inForward;
                mz = dir.z * SPEED * inForward;
                driving = true;
            }
            if (driving) setEnergy(energy() - MOVE_COST);
        }
        yBodyRot = yHeadRot = getYRot();
        setDeltaMovement(mx, vy, mz);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(0, 0, 0));
        if ((controller != null || phase == 1) && canWork() && arm == ARM_TICKS && now % CLEAN_PERIOD == 0) sweep(sl);
        if (now % 20 == 0) {
            charge(sl);
            radiation(sl);
        }
    }

    public int sweep(ServerLevel sl) {
        if (waste() >= WASTE_MAX || energy() < CLEAN_COST || agent() < AGENT_COST) return 0;
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        BlockPos front = BlockPos.containing(getX() + dir.x * 1.3, getY() + 0.1, getZ() + dir.z * 1.3);
        int n = Contamination.clean(sl, front, 1, DeconSprayerItem.CLEAN_SV_H);
        for (BlockPos p : BlockPos.betweenClosed(front.offset(-1, -1, -1), front.offset(1, 1, 1))) {
            if (sl.getBlockState(p).is(CatalogRegistry.FALLOUT.get())) {
                sl.removeBlock(p, false);
                n++;
            }
        }
        if (n > 0) {
            com.sqc.nuclearsq.progression.Titles.mark(sl, controller, "rank:decon");
            setWaste(waste() + n);
            setEnergy(energy() - CLEAN_COST);
            setAgent(agent() - AGENT_COST);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, front.getX() + 0.5, front.getY() + 0.3, front.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
        }
        return n;
    }

    public void charge(ServerLevel sl) {
        int room = BATTERY - energy();
        if (room <= 0) return;
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            if (p.equals(c)) continue;
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

    public void radiation(ServerLevel sl) {
        double rate = RadiationManager.rateAt(sl, position().add(0, 0.5, 0));
        entityData.set(RADIATION, (float) rate);
        entityData.set(NOISE, (int) Mth.clamp(rate / NOISE_FULL * 100, 0, 100));
        if (rate < DAMAGE_FROM || getHealth() <= 1f) return;
        double dmg = Math.min(4, 0.5 + rate * 2) * (plated() ? PLATED_FACTOR : 1.0);
        wear += dmg;
        if (wear >= 1) {
            int d = (int) wear;
            wear -= d;
            setHealth(Math.max(1f, getHealth() - d));
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ProtectionRegistry.LEAD_BLOCK_ITEM.get())) {
            if (plated()) return InteractionResult.PASS;
            if (!level().isClientSide) {
                entityData.set(PLATED, true);
                if (!player.getAbilities().instabuild) held.shrink(1);
                playSound(SoundEvents.ANVIL_USE, 0.6f, 1.2f);
                player.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.plated"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        Item sheet = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("nuclearsq", "steel_sheet_sqc"));
        if (held.is(sheet) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                setHealth(Math.min(getMaxHealth(), getHealth() + REPAIR_HEALTH));
                if (!player.getAbilities().instabuild) held.shrink(1);
                playSound(SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            if (waste() > 0) {
                player.displayClientMessage(Component.translatable("message.nuclearsq.decon_robot.empty_first"), true);
                return InteractionResult.CONSUME;
            }
            stopControl("picked");
            spawnAtLocation(DeconRegistry.ROBOT_ITEM.get());
            discard();
            return InteractionResult.CONSUME;
        }
        emptyTank();
        return InteractionResult.CONSUME;
    }

    public int emptyTank() {
        int items = (waste() + RUBBLE_PER_ITEM - 1) / RUBBLE_PER_ITEM;
        for (int i = 0; i < items; i++) {
            ItemStack st = new ItemStack(DeconRegistry.RUBBLE.get());
            ItemContamination.set(st, RUBBLE_SV_H);
            ItemEntity e = new ItemEntity(level(), getX(), getY() + 0.8, getZ(), st);
            level().addFreshEntity(e);
        }
        if (items > 0) level().playSound(null, blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 0.8f, 0.8f);
        setWaste(0);
        return items;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) stopControl("");
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("energy", energy());
        tag.putInt("waste", waste());
        tag.putInt("agent", agent());
        tag.putBoolean("plated", plated());
        tag.putDouble("wear", wear);
        if (phase > 0 && zoneA != null && zoneB != null) {
            tag.putLongArray("zone", new long[] {zoneA.asLong(), zoneB.asLong()});
            tag.putIntArray("job", new int[] {phase, leg});
            tag.putDouble("home_x", home.x);
            tag.putDouble("home_y", home.y);
            tag.putDouble("home_z", home.z);
            tag.putFloat("home_yaw", homeYaw);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setEnergy(tag.getInt("energy"));
        setWaste(tag.getInt("waste"));
        setAgent(tag.getInt("agent"));
        entityData.set(PLATED, tag.getBoolean("plated"));
        wear = tag.getDouble("wear");
        long[] zone = tag.getLongArray("zone");
        int[] job = tag.getIntArray("job");
        if (zone.length == 2 && job.length == 2) {
            zoneA = BlockPos.of(zone[0]);
            zoneB = BlockPos.of(zone[1]);
            route = route(zoneA, zoneB);
            phase = job[0];
            leg = job[1];
            home = new Vec3(tag.getDouble("home_x"), tag.getDouble("home_y"), tag.getDouble("home_z"));
            homeYaw = tag.getFloat("home_yaw");
        }
    }
}
