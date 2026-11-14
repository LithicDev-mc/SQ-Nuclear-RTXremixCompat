package com.sqc.nuclearsq.nanobot.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SwarmFx {
    private SwarmFx() {}

    public static final int MAX_BEAMS = 0;
    private static final double PARTICLE_RANGE_SQR = 48 * 48;

    public static int lightAt(net.minecraft.world.level.Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p.x, p.y + 0.1, p.z);
        for (int k = 0; k < 3 && level.getBlockState(pos).isSolidRender(level, pos); k++) pos = pos.above();
        int c = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(net.minecraft.client.renderer.LightTexture.block(c), 4), net.minecraft.client.renderer.LightTexture.sky(c));
    }

    @Nullable
    public static Vec3 ownerPoint(ClientSwarm s, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Player p = mc.level.getPlayerByUUID(s.owner);
        return p == null ? null : p.getPosition(partialTick).add(0, p.getBbHeight() * 0.7, 0);
    }

    public static int beams(ClientSwarm s, long time, float pt, @Nullable Vec3 owner, Vec3[] from, Vec3[] to, float[] width) {
        return 0;
    }

    public static void tick(ClientSwarm s, long time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || s.count() == 0) return;
        Vec3 c = s.center(time, 0f);
        if (mc.player.distanceToSqr(c) > PARTICLE_RANGE_SQR) return;
        var rnd = mc.level.random;
        switch (s.state()) {
            case 3 -> {
                boolean crowd = s.count() > 24;
                if (time % (crowd ? 6 : 3) == 0) {
                    for (int i = 0; i < s.count(); i += crowd ? 2 : 1) {
                        BlockPos t = s.target(i);
                        if (t == null || !s.isWorking(i)) continue;
                        BlockState st = mc.level.getBlockState(t);
                        if (st.isAir()) continue;
                        Vec3 b = s.bot(i, time, 0f);
                        double cx = t.getX() + 0.5, cz = t.getZ() + 0.5;
                        double dx = b.x - cx, dz = b.z - cz, d = Math.sqrt(dx * dx + dz * dz);
                        if (d < 1e-3) continue;
                        dx /= d;
                        dz /= d;
                        for (int k = 0; k < 2; k++) {
                            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), cx + dx * 0.52 - dz * (rnd.nextDouble() - 0.5) * 0.4,
                                    b.y + 0.05 + (rnd.nextDouble() - 0.3) * 0.12, cz + dz * 0.52 + dx * (rnd.nextDouble() - 0.5) * 0.4,
                                    dx * 0.05 + rnd.nextGaussian() * 0.02, 0.03, dz * 0.05 + rnd.nextGaussian() * 0.02);
                        }
                    }
                }
            }
            default -> { }
        }
    }
}
