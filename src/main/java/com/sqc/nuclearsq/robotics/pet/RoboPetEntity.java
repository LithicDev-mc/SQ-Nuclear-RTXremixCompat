package com.sqc.nuclearsq.robotics.pet;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.radiation.RadiationCompass;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.transport.WrenchItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class RoboPetEntity extends TamableAnimal {
    public static final int BATTERY = 200_000, WALK_COST = 5, CHARGE_CLICK = 50_000, CHARGE_PER_SECOND = 10_000;
    public static final double FETCH_RADIUS = 5, BALL_RADIUS = 24, HAND_OVER = 2.5;
    private static final int WARN_PERIOD = 200, PET_TICKS = 40, PLAY_TICKS = 60;
    private static final float TURN_STEP = 0.07f;

    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(RoboPetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SNIFF = SynchedEntityData.defineId(RoboPetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> CARRIED = SynchedEntityData.defineId(RoboPetEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> PET = SynchedEntityData.defineId(RoboPetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLLAR = SynchedEntityData.defineId(RoboPetEntity.class, EntityDataSerializers.INT);
    public static final int POINT_TICKS = 100, POINT_PAUSE = 600, LAMP_LIGHT = 12;
    private static final int[] DOG_DUR = {0, 200, 200, 60, 50, 90, 35, 60}, CAT_DUR = {0, 200, 200, 80, 60, 40, 50, 0};

    private int warnCooldown, play, pointPause;
    @Nullable private BlockPos chest, light;
    @Nullable private net.minecraft.world.phys.Vec3 source;
    private int still, act;
    private float actW, actWo, petW, petWo;
    public float gait, gaitPos, turnW, ampW, ampWo;

    public RoboPetEntity(EntityType<? extends RoboPetEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENERGY, BATTERY).define(SNIFF, 0).define(CARRIED, ItemStack.EMPTY).define(PET, 0).define(COLLAR, 0);
    }

    public boolean isCat() {
        return getType() == RoboPetRegistry.CAT.get();
    }

    public int energy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int fe) {
        entityData.set(ENERGY, Mth.clamp(fe, 0, BATTERY));
    }

    public boolean lying() {
        return energy() <= 0;
    }

    public int sniff() {
        return entityData.get(SNIFF);
    }

    public ItemStack carried() {
        return entityData.get(CARRIED);
    }

    public int pet() {
        return entityData.get(PET);
    }

    public int collar() {
        return entityData.get(COLLAR);
    }

    @Nullable
    public BlockPos chest() {
        return chest;
    }

    public void playWith() {
        play = PLAY_TICKS;
    }

    public boolean playing() {
        return play > 0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new FetchGoal());
        goalSelector.addGoal(3, new PointGoal());
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.1, 5f, 2f) {
            @Override
            public void tick() {
                super.tick();
                if (getOwner() != null && distanceToSqr(getOwner()) > 100) getNavigation().setSpeedModifier(1.6);
            }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || lying();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            idleTick();
            return;
        }
        double dx = getX() - xo, dz = getZ() - zo;
        if (energy() > 0 && dx * dx + dz * dz > 1e-4) setEnergy(energy() - WALK_COST);
        if (warnCooldown > 0) warnCooldown--;
        if (play > 0) play--;
        if (pet() > 0) {
            entityData.set(PET, pet() - 1);
            if (pet() % 20 == 10) sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.6, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        }
        if (tickCount % 10 == 0) lamp(sl);
        if (pointPause > 0) pointPause--;
        if (tickCount % 40 == 0) {
            charge(sl);
            if (isCat()) return;
            sniff(sl);
        }
    }

    public int act() {
        return act;
    }

    public float actWeight(float partialTick) {
        return Mth.lerp(partialTick, actWo, actW);
    }

    public float petWeight(float partialTick) {
        return Mth.lerp(partialTick, petWo, petW);
    }

    private void idleTick() {
        double dx = getX() - xo, dz = getZ() - zo;
        boolean moving = dx * dx + dz * dz > 1e-4;
        still = moving || !onGround() ? 0 : still + 1;
        ampWo = ampW;
        ampW += ((moving ? 1f : 0f) - ampW) * 0.3f;
        float turn = Mth.degreesDifferenceAbs(yBodyRot, yBodyRotO);
        gait += turn * TURN_STEP;
        turnW += (Math.min(turn / 6f, 1f) * 0.5f - turnW) * 0.2f;
        int[] dur = isCat() ? CAT_DUR : DOG_DUR;
        int t = still - 200, r = Math.floorMod((t / 200 * 0x9E3779B1) ^ (getId() * 0x85EBCA6B), dur.length);
        int want = lying() ? 2 : isInSittingPose() ? 1 : t < 0 || t % 200 >= dur[r] ? 0 : r;
        actWo = actW;
        if (want != act) {
            actW = Math.max(0f, actW - 0.1f);
            if (actW == 0f) act = want;
        } else if (act != 0) actW = Math.min(1f, actW + 0.08f);
        petWo = petW;
        petW = Mth.clamp(petW + (pet() > 0 ? 0.12f : -0.08f), 0f, 1f);
    }

    public void charge(ServerLevel sl) {
        int room = BATTERY - energy();
        if (room <= 0) return;
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            int got = 0;
            if (sl.getBlockEntity(p) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity st && st.exo() != null) got = st.exo().drain(Math.min(room, 2 * CHARGE_PER_SECOND));
            else {
                for (var d : Direction.values()) {
                    IEnergyStorage s = sl.getCapability(Capabilities.EnergyStorage.BLOCK, p, d);
                    if (s != null && s.canExtract()) got = s.extractEnergy(Math.min(room, 2 * CHARGE_PER_SECOND), false);
                    if (got > 0) break;
                }
            }
            if (got > 0) {
                setEnergy(energy() + got);
                return;
            }
        }
    }

    public void sniff(ServerLevel sl) {
        var t = lying() ? null : RadiationCompass.scan(sl, position());
        int lv = t == null ? 0 : t.level();
        entityData.set(SNIFF, lv);
        source = t == null ? null : t.pos();
        if (lv < 2 || warnCooldown > 0 || !(getOwner() instanceof ServerPlayer p) || p.level() != level()) return;
        double dx = t.pos().x - p.getX(), dz = t.pos().z - p.getZ();
        int dir = Math.floorMod(Math.round(RadiationCompass.bearingTurns(p.getYRot(), dx, dz) * 4), 4);
        p.displayClientMessage(Component.translatable("message.nuclearsq.robo_dog.warn",
                Component.translatable("message.nuclearsq.robo_dog.dir." + dir), Math.round(t.distance())), true);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 1.0f, 1.8f);
        warnCooldown = WARN_PERIOD;
    }

    private void lamp(ServerLevel sl) {
        BlockPos want = collar() == RoboCollarItem.LAMP && !lying() && (sl.getBlockState(blockPosition()).isAir() || blockPosition().equals(light)) ? blockPosition() : null;
        if (java.util.Objects.equals(want, light)) return;
        clearLight();
        if (want != null) sl.setBlock(want, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, LAMP_LIGHT), 3);
        light = want;
    }

    private void clearLight() {
        if (light != null && level().getBlockState(light).is(net.minecraft.world.level.block.Blocks.LIGHT)) level().removeBlock(light, false);
        light = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) clearLight();
        super.remove(reason);
    }

    public ItemStack kitStack() {
        ItemStack kit = new ItemStack(isCat() ? RoboPetRegistry.CAT_KIT.get() : RoboPetRegistry.DOG_KIT.get());
        kit.set(ModDataComponents.STORED_ENERGY.get(), energy());
        return kit;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isOwnedBy(player)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                if (!carried().isEmpty()) spawnAtLocation(carried());
                if (collar() != 0) spawnAtLocation(RoboPetRegistry.collarStack(collar()));
                spawnAtLocation(kitStack());
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.getItem() instanceof RoboCollarItem c) {
            if (!c.fits(this) || c.kind == collar()) return InteractionResult.PASS;
            if (!level().isClientSide) {
                if (collar() != 0) spawnAtLocation(RoboPetRegistry.collarStack(collar()));
                entityData.set(COLLAR, c.kind);
                chest = held.get(RoboPetRegistry.CHEST.get());
                if (!player.getAbilities().instabuild) held.shrink(1);
                playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8f, 1.3f);
                player.displayClientMessage(Component.translatable("message.nuclearsq.robo_pet.collar", held.getHoverName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty()) {
            IEnergyStorage src = held.getCapability(Capabilities.EnergyStorage.ITEM);
            if (src == null || !src.canExtract() || energy() >= BATTERY) return InteractionResult.PASS;
            if (!level().isClientSide) {
                int got = src.extractEnergy(Math.min(BATTERY - energy(), CHARGE_CLICK), false);
                if (got > 0) {
                    setEnergy(energy() + got);
                    playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                    player.displayClientMessage(Component.translatable("message.nuclearsq.robo_pet.charged", energy() * 100 / BATTERY), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            setInSittingPose(sit);
            getNavigation().stop();
            player.displayClientMessage(Component.translatable("message.nuclearsq." + (isCat() ? "robo_cat" : "robo_dog") + (sit ? ".stay" : ".follow")), true);
        } else if (!lying()) {
            if (pet() < PET_TICKS / 2) playSound(isCat() ? SoundEvents.CAT_PURR : SoundEvents.WOLF_PANT, isCat() ? 1.0f : 0.5f, 1.3f);
            entityData.set(PET, PET_TICKS);
        }
        return InteractionResult.CONSUME;
    }

    private class FetchGoal extends Goal {
        @Nullable private Entity target;
        private int crouch;

        FetchGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private Player owner() {
            return getOwner() instanceof Player o && o.level() == level() && distanceToSqr(o) < 32 * 32 ? o : null;
        }

        @Override
        public boolean canUse() {
            if (lying() || isOrderedToSit() || owner() == null) return false;
            if (!carried().isEmpty()) return true;
            if (isCat() && play > 0) {
                target = owner();
                return true;
            }
            if ((tickCount + getId()) % 10 != 0) return false;
            double r = isCat() ? FETCH_RADIUS : BALL_RADIUS;
            Predicate<ItemEntity> wants = isCat() ? e -> !e.hasPickUpDelay() && e.getOwner() != getOwner() && !e.getItem().is(RoboPetRegistry.BALL)
                    : e -> e.getItem().is(RoboPetRegistry.BALL);
            target = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(r, 2, r), e -> e.isAlive() && wants.test(e))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(RoboPetEntity.this))).orElse(null);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !lying() && owner() != null && (!carried().isEmpty() || (target != null && target.isAlive() && (target instanceof ItemEntity || play > 0)));
        }

        @Override
        public void tick() {
            Player o = owner();
            if (o == null) return;
            if (!carried().isEmpty() && toChest()) return;
            if (!carried().isEmpty()) {
                getNavigation().moveTo(o, isCat() ? 1.1 : 1.4);
                if (distanceToSqr(o) < HAND_OVER * HAND_OVER) {
                    if (!o.getInventory().add(carried())) o.drop(carried(), false);
                    entityData.set(CARRIED, ItemStack.EMPTY);
                    playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.4f);
                }
            } else if (target instanceof ItemEntity it) {
                double d = distanceToSqr(it);
                entityData.set(SNIFF, isCat() && d < 16 ? 1 : 0);
                getLookControl().setLookAt(it);
                if (d < 1.5) {
                    entityData.set(CARRIED, it.getItem().copy());
                    it.discard();
                    target = null;
                    entityData.set(SNIFF, 0);
                    playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.0f);
                } else if (!isCat() || d > 6.25) getNavigation().moveTo(it, !isCat() ? 1.5 : d < 16 ? 0.5 : 1.1);
                else if (++crouch > 15 && onGround()) {
                    crouch = 0;
                    setDeltaMovement(it.position().subtract(position()).multiply(0.15, 0, 0.15).add(0, 0.4, 0));
                } else getNavigation().stop();
            } else if (target != null) {
                double d = distanceToSqr(target);
                entityData.set(SNIFF, d < 16 ? 1 : 0);
                getLookControl().setLookAt(target);
                if (d > 9) getNavigation().moveTo(target, 1.2);
                else if (!onGround()) return;
                else if (d > 2.25 && ++crouch > 12) {
                    crouch = -8;
                    setDeltaMovement(target.position().subtract(position()).multiply(0.18, 0, 0.18).add(0, 0.42, 0));
                    playSound(SoundEvents.CAT_PURREOW, 0.5f, 1.4f);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 0.5, getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                } else if (d <= 2.25 && ++crouch > 10) {
                    crouch = 0;
                    setDeltaMovement(position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.3).add(0, 0.3, 0));
                } else getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            target = null;
            crouch = 0;
            entityData.set(SNIFF, 0);
        }

        private boolean toChest() {
            if (collar() != RoboCollarItem.TIDY || chest == null || distanceToSqr(Vec3.atCenterOf(chest)) > 32 * 32 || !level().isLoaded(chest)) return false;
            var inv = level().getCapability(Capabilities.ItemHandler.BLOCK, chest, Direction.UP);
            if (inv == null || !net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(inv, carried(), true).isEmpty()) return false;
            if (distanceToSqr(Vec3.atCenterOf(chest)) > HAND_OVER * HAND_OVER) {
                getNavigation().moveTo(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 1.1);
                return true;
            }
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(inv, carried(), false);
            entityData.set(CARRIED, ItemStack.EMPTY);
            playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.4f);
            return true;
        }
    }

    private class PointGoal extends Goal {
        private int left;

        PointGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return collar() == RoboCollarItem.SNIFFER && !lying() && !isOrderedToSit() && carried().isEmpty() && pointPause == 0 && sniff() > 0 && source != null;
        }

        @Override
        public void start() {
            left = POINT_TICKS;
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !lying() && !isOrderedToSit() && source != null;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(source.x, source.y, source.z);
            if (distanceToSqr(source) > 2.5 * 2.5) {
                getNavigation().moveTo(source.x, source.y, source.z, 1.3);
                return;
            }
            getNavigation().stop();
            if (left-- % 20 == 0) playSound(SoundEvents.WOLF_AMBIENT, 0.8f, 1.5f);
        }

        @Override
        public void stop() {
            pointPause = POINT_PAUSE;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("energy", energy());
        if (!carried().isEmpty()) tag.put("carried", carried().save(registryAccess()));
        tag.putInt("collar", collar());
        if (chest != null) tag.putLong("chest", chest.asLong());
        if (light != null) tag.putLong("light", light.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setEnergy(tag.contains("energy") ? tag.getInt("energy") : BATTERY);
        entityData.set(CARRIED, tag.contains("carried") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("carried")) : ItemStack.EMPTY);
        entityData.set(COLLAR, tag.getInt("collar"));
        chest = tag.contains("chest") ? BlockPos.of(tag.getLong("chest")) : null;
        light = tag.contains("light") ? BlockPos.of(tag.getLong("light")) : null;
    }
}
