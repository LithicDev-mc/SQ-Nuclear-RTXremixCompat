package com.sqc.nuclearsq.explosion.client;

import net.minecraft.world.inventory.InventoryMenu;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NukeDebris {
    private NukeDebris() {}

    private static final BlockState[] ROCKS = {
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
    };
    private static final double GRAVITY = 0.06, DRAG = 0.985;

    private static final class Chunk {
        final BlockState state;
        final float size;
        final float ax, ay, az, spin;
        double x, y, z, ox, oy, oz, vx, vy, vz;
        float angle, oAngle;
        int age, bounces;
        final int life;

        Chunk(BlockState state, float size, double x, double y, double z, double vx, double vy, double vz, RandomSource r) {
            this.state = state; this.size = size;
            this.x = ox = x; this.y = oy = y; this.z = oz = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            float a = r.nextFloat() * 2 - 1, b = r.nextFloat() * 2 - 1, c = r.nextFloat() * 2 - 1;
            float len = Math.max(0.1f, (float) Math.sqrt(a * a + b * b + c * c));
            this.ax = a / len; this.ay = b / len; this.az = c / len;
            this.spin = (8f + r.nextFloat() * 22f) * (r.nextBoolean() ? 1 : -1);
            this.life = 160 + r.nextInt(120);
        }
    }

    private static final List<Chunk> CHUNKS = new ArrayList<>();

    static int max() {
        return NuclearClientConfig.DEBRIS.get();
    }

    public static void throwChunk(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float big) {
        if (CHUNKS.size() >= max()) return;
        RandomSource r = level.random;
        BlockState state = ROCKS[r.nextInt(ROCKS.length)];
        if (r.nextFloat() < 0.6f) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, y, z)).below();
            BlockState s = level.getBlockState(ground);
            if (!s.isAir() && s.getRenderShape() == RenderShape.MODEL && s.getFluidState().isEmpty() && !s.hasBlockEntity()) state = s;
        }
        float size = 0.25f + big * (0.5f + r.nextFloat() * 0.55f);
        CHUNKS.add(new Chunk(state, size, x, y, z, vx, vy, vz, r));
    }

    public static void throwTop(ClientLevel level, double x, double z, double vx, double vy, double vz, float big) {
        if (CHUNKS.size() >= max()) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z)).below();
        BlockState s = level.getBlockState(top);
        if (s.isAir() || s.getRenderShape() != RenderShape.MODEL || !s.getFluidState().isEmpty() || s.hasBlockEntity()) return;
        RandomSource r = level.random;
        CHUNKS.add(new Chunk(s, 0.25f + big * (0.5f + r.nextFloat() * 0.55f), x, top.getY() + 1, z, vx, vy, vz, r));
    }

    public static double groundY(ClientLevel level, double x, double z, double fallback) {
        BlockPos p = BlockPos.containing(x, level.getMinBuildHeight(), z);
        if (!level.isLoaded(p)) return fallback;
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        return h <= level.getMinBuildHeight() ? fallback : h;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { CHUNKS.clear(); return; }
        if (mc.isPaused() || CHUNKS.isEmpty()) return;
        int over = CHUNKS.size() - max();
        if (over > 0) CHUNKS.subList(0, over).clear();
        for (int i = CHUNKS.size() - 1; i >= 0; i--) {
            Chunk c = CHUNKS.get(i);
            c.ox = c.x; c.oy = c.y; c.oz = c.z; c.oAngle = c.angle;
            c.vy -= GRAVITY;
            c.vx *= DRAG; c.vy *= DRAG; c.vz *= DRAG;
            double nx = c.x + c.vx, ny = c.y + c.vy, nz = c.z + c.vz;
            BlockPos at = BlockPos.containing(nx, ny - c.size * 0.5, nz);
            if (c.vy < 0 && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                ny = at.getY() + 1 + c.size * 0.5;
                c.vy = -c.vy * 0.35;
                c.vx *= 0.55; c.vz *= 0.55;
                c.bounces++;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, c.state), nx, ny, nz, 0, 0.1, 0);
                if (c.bounces > 2 || Math.abs(c.vy) < 0.08) c.age = Math.max(c.age, c.life - 20);
            }
            c.x = nx; c.y = ny; c.z = nz;
            c.angle += c.spin * (c.bounces > 2 ? 0.2f : 1f);
            if (++c.age >= c.life || c.y < level.getMinBuildHeight()) {
                for (int k = 0; k < 4; k++)
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, c.state), c.x, c.y, c.z, 0, 0.05, 0);
                CHUNKS.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CHUNKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);
        var buffer = buffers.getBuffer(type);
        for (Chunk c : CHUNKS) {
            double x = c.ox + (c.x - c.ox) * pt, y = c.oy + (c.y - c.oy) * pt, z = c.oz + (c.z - c.oz) * pt;
            if ((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z) > 256 * 256) continue;
            BlockPos at = BlockPos.containing(x, y, z);
            int light = LevelRenderer.getLightColor(level, at);
            int rgb = mc.getBlockColors().getColor(c.state, level, at, 0);
            float r = 1f, g = 1f, b = 1f;
            if (rgb != -1) { r = (rgb >> 16 & 255) / 255f; g = (rgb >> 8 & 255) / 255f; b = (rgb & 255) / 255f; }
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            ps.mulPose(Axis.of(new org.joml.Vector3f(c.ax, c.ay, c.az)).rotationDegrees(c.oAngle + (c.angle - c.oAngle) * pt));
            ps.scale(c.size, c.size, c.size);
            ps.translate(-0.5, -0.5, -0.5);
            dispatcher.getModelRenderer().renderModel(ps.last(), buffer, c.state, dispatcher.getBlockModel(c.state),
                    r, g, b, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
            ps.popPose();
        }
        buffers.endBatch(type);
    }
}
