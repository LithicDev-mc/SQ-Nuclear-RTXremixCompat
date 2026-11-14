package com.sqc.nuclearsq.radiation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.RadiationDebugPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class RadiationDebugClient {
    private RadiationDebugClient() {}

    private static volatile RadiationDebugPayload last;
    private static volatile long lastMs;

    public static void receive(RadiationDebugPayload p) {
        last = p;
        lastMs = System.currentTimeMillis();
    }

    public static float brightness(double svH) {
        if (svH <= 0) return 0f;
        return Mth.clamp((float) ((Math.log10(svH) + 6.0) / 6.0), 0f, 1f);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        RadiationDebugPayload p = last;
        if (p == null || p.pos().length == 0 || System.currentTimeMillis() - lastMs > 3000L) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (int i = 0; i < p.pos().length; i++) {
            float t = brightness(p.svH()[i]);
            float r, g, b;
            if (p.kind()[i] == RadiationDebugPayload.KIND_SOURCE) {
                r = 0.45f + 0.55f * t; g = 0.25f + 0.4f * t; b = 0.05f;
            } else if (p.kind()[i] == RadiationDebugPayload.KIND_FALLOUT) {
                r = 0.4f + 0.6f * t; g = 0.4f + 0.6f * t; b = 0.1f;
            } else {
                b = 0.35f + 0.65f * t; g = 0.25f * t + 0.5f * t * t; r = 0.15f + 0.6f * t * t;
            }
            LevelRenderer.renderLineBox(ps, lines, new AABB(BlockPos.of(p.pos()[i])).inflate(0.004), r, g, b, 0.35f + 0.65f * t);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }
}
