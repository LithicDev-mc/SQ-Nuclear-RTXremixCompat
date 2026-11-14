package com.sqc.nuclearsq.explosion.bomb;

import org.joml.Vector3f;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.compat.sable.SableCompat;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.registry.BombRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class NuclearBombBlockEntity extends BlockEntity {
    private static final DustParticleOptions RED_SPARK = new DustParticleOptions(new Vector3f(1f, 0.1f, 0.05f), 1.6f);

    private Vec3 lastWorld;
    private Vec3 motion = Vec3.ZERO;
    private double peak;
    private boolean ownBody;
    private int fuse = -1;
    private boolean fuseDone;

    public NuclearBombBlockEntity(BlockPos pos, BlockState state) {
        super(BombRegistry.BOMB_BE.get(), pos, state);
    }

    public Vec3 worldMotion() {
        return motion;
    }

    public boolean isOwnBody() {
        return ownBody;
    }

    public void markOwnBody() {
        ownBody = true;
        setChanged();
    }

    public boolean fuseDone() {
        return fuseDone;
    }

    public void resetFuse() {
        fuse = -1;
        fuseDone = false;
        setChanged();
    }

    public void finishFuse() {
        fuse = 0;
        fuseDone = true;
        setChanged();
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockState state = getBlockState();
        NuclearBombBlock bomb = NuclearBombBlock.of(state);
        if (bomb == null) return;
        boolean armed = state.getValue(NuclearBombBlock.ARMED);
        if (!armed && (fuse >= 0 || fuseDone)) resetFuse();
        if (armed && !fuseDone) tickFuse(sl, bomb, state);

        if (!SableCompat.isOnSubLevel(sl, worldPosition)) return;
        Vec3 now = SableCompat.toWorld(sl, Vec3.atCenterOf(worldPosition));
        Vec3 before = motion;
        if (lastWorld != null) motion = now.subtract(lastWorld);
        lastWorld = now;

        if (!armed) return;
        if (fuseDone && NuclearBombBlock.tryRelease(sl, worldPosition, state)) return;
        double is = motion.length();
        peak = Math.max(peak * 0.8, before.length());
        if (peak >= NuclearConfig.BOMB_IMPACT_SPEED.get() && is < peak * 0.4) {
            bomb.removeParts(sl, worldPosition);
            NuclearExplosion.detonate(sl, now, bomb.blast());
        }
    }

    private void tickFuse(ServerLevel sl, NuclearBombBlock bomb, BlockState state) {
        if (fuse < 0) fuse = NuclearConfig.fuseSeconds() * 20;
        if (fuse > 0) {
            fuse--;
            fuseEffects(sl, bomb);
        }
        if (fuse <= 0) {
            fuseDone = true;
            setChanged();
            NuclearBombBlock.tryRelease(sl, worldPosition, state);
        }
    }

    private void fuseEffects(ServerLevel sl, NuclearBombBlock bomb) {
        int w = bomb.width();
        Vec3 c = SableCompat.toWorld(sl, bomb.bottomCenter(worldPosition).add(0, w, 0));
        boolean last = fuse < 60;
        if (fuse % (last ? 5 : 20) == 0) {
            float pitch = last ? 2.0f : 1.0f + 0.5f * (1f - fuse / (float) Math.max(1, NuclearConfig.fuseSeconds() * 20));
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, pitch);
        }
        if (fuse % 10 < 5) sl.sendParticles(RED_SPARK, c.x, c.y, c.z, 3 * w, 0.25 * w, 0.4 * w, 0.25 * w, 0.0);
        if (fuse % 20 == 0) {
            int secs = (fuse + 19) / 20;
            Component msg = Component.translatable("message.nuclearsq.bomb_countdown", secs).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            double range = bomb.blast().flashRange();
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(c) <= range * range) p.displayClientMessage(msg, true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownBody) tag.putBoolean("OwnBody", true);
        if (fuse >= 0) tag.putInt("Fuse", fuse);
        if (fuseDone) tag.putBoolean("FuseDone", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownBody = tag.getBoolean("OwnBody");
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
        fuseDone = tag.getBoolean("FuseDone");
    }
}
