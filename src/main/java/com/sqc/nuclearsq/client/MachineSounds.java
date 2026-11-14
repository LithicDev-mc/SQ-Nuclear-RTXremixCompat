package com.sqc.nuclearsq.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.ModSounds;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class MachineSounds {
    private MachineSounds() {}

    private static final int CHUNK_RADIUS = 2;
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();
    private static int ticks;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.values().removeIf(l -> l.isStopped() || ticks - l.started > 20 && !mc.getSoundManager().isActive(l));
        if (ticks++ % 10 != 0) return;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk ch = mc.level.getChunkSource().getChunk(c.x + dx, c.z + dz, false);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) consider(mc, be);
            }
        }
    }

    private static void consider(Minecraft mc, BlockEntity be) {
        SoundEvent ev;
        ToDoubleFunction<BlockEntity> level;
        float volume, pitch;
        if (be instanceof TurbineControllerBlockEntity) {
            ev = ModSounds.TURBINE.get();
            level = MachineSounds::turbine;
            volume = 1.6f;
            pitch = 0.6f;
        } else if (be instanceof BigMachineBlockEntity m && m.machine().isPump()) {
            ev = ModSounds.PUMP.get();
            level = b -> ((BigMachineBlockEntity) b).isFormed() && ((BigMachineBlockEntity) b).isRunning() ? 1 : 0;
            volume = 1.4f;
            pitch = 0.7f;
        } else {
            return;
        }
        if (PLAYING.containsKey(be.getBlockPos()) || level.applyAsDouble(be) <= 0) return;
        Loop loop = new Loop(ev, be, level, volume, pitch);
        loop.started = ticks;
        PLAYING.put(be.getBlockPos().immutable(), loop);
        mc.getSoundManager().play(loop);
    }

    private static double turbine(BlockEntity b) {
        TurbineControllerBlockEntity t = (TurbineControllerBlockEntity) b;
        return t.isFormed() ? t.spin() / (double) TurbineControllerBlockEntity.SPIN_STEPS : 0;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final BlockEntity be;
        private final ToDoubleFunction<BlockEntity> level;
        private final float maxVolume, basePitch;
        private float shown;
        private int started;

        Loop(SoundEvent ev, BlockEntity be, ToDoubleFunction<BlockEntity> level, float maxVolume, float basePitch) {
            super(ev, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.level = level;
            this.maxVolume = maxVolume;
            this.basePitch = basePitch;
            this.looping = true;
            this.delay = 0;
            BlockPos p = be.getBlockPos();
            this.x = p.getX() + 0.5;
            this.y = p.getY() + 0.5;
            this.z = p.getZ() + 0.5;
            this.volume = 0.01f;
            this.pitch = basePitch;
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            Level lv = be.getLevel();
            boolean gone = be.isRemoved() || lv == null || lv != Minecraft.getInstance().level;
            float want = gone ? 0f : (float) Math.max(0, Math.min(1, level.applyAsDouble(be)));
            shown += (want - shown) * 0.08f;
            volume = Math.max(0.001f, shown * maxVolume);
            pitch = basePitch * (0.5f + 0.5f * shown);
            if ((gone || want <= 0) && shown < 0.02f) stop();
        }
    }
}
