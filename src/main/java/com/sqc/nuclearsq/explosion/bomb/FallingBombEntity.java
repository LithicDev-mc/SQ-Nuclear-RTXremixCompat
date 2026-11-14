package com.sqc.nuclearsq.explosion.bomb;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.registry.BombRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FallingBombEntity extends FallingBlockEntity {
    private boolean detonated;

    public FallingBombEntity(EntityType<? extends FallingBombEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static FallingBombEntity drop(ServerLevel level, Vec3 pos, BlockState state, Vec3 motion) {
        FallingBombEntity e = new FallingBombEntity(BombRegistry.FALLING_BOMB.get(), level);
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        tag.putInt("Time", 1);
        tag.putBoolean("DropItem", true);
        tag.putBoolean("HurtEntities", false);
        e.readAdditionalSaveData(tag);
        e.refreshDimensions();
        e.setPos(pos.x, pos.y, pos.z);
        e.xo = pos.x;
        e.yo = pos.y;
        e.zo = pos.z;
        e.setDeltaMovement(motion);
        e.setStartPos(BlockPos.containing(pos));
        level.addFreshEntity(e);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 2.0f, 0.5f);
        return e;
    }

    public NuclearBombBlock bomb() {
        NuclearBombBlock b = NuclearBombBlock.of(getBlockState());
        return b != null ? b : BombRegistry.BOMB.get();
    }

    public boolean isLarge() {
        return bomb().width() > 1;
    }

    public int bombHeight() {
        return bomb() instanceof MultiPartBombBlock m ? m.height() : 1;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isLarge() ? EntityDimensions.fixed(bomb().width() - 0.02f, bombHeight() - 0.02f) : super.getDimensions(pose);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0 * 512.0;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        refreshDimensions();
    }

    @Override
    public void tick() {
        Vec3 before = getDeltaMovement();
        super.tick();
        Level lv = level();
        double top = bombHeight();
        if (lv.isClientSide) {
            lv.addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + top, getZ(), 0, 0.05, 0);
            if (tickCount % 2 == 0) lv.addParticle(ParticleTypes.FLAME, getX(), getY() + top, getZ(), 0, 0.02, 0);
            return;
        }
        if (detonated || !(lv instanceof ServerLevel sl)) return;
        boolean hit = onGround() || horizontalCollision || verticalCollision || isRemoved();
        if (!hit) return;
        double speed = horizontalCollision ? before.length() : Math.max(-before.y, before.horizontalDistance());
        if (speed < NuclearConfig.BOMB_IMPACT_SPEED.get()) {
            if (isRemoved() && isLarge()) settleLarge(sl);
            return;
        }
        detonated = true;
        Vec3 at = position().add(0, 0.5, 0);
        BlockPos p = blockPosition();
        BlockState placed = sl.getBlockState(p);
        if (placed.getBlock() instanceof NuclearBombBlock b) b.removeParts(sl, b.core(p, placed));
        discard();
        NuclearExplosion.detonate(sl, at, bomb().blast());
    }

    private void settleLarge(ServerLevel sl) {
        BlockPos p = blockPosition();
        BlockState placed = sl.getBlockState(p);
        NuclearBombBlock b = bomb();
        if (!placed.is(b)) return;
        sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
        BlockPos core = b.coreAt(position());
        if (b.fits(sl, core)) b.placeParts(sl, core, placed, Block.UPDATE_ALL);
        else spawnAtLocation(b);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Detonated", detonated);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        detonated = tag.getBoolean("Detonated");
    }
}
