package com.sqc.nuclearsq.transport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.client.NetScanRenderTypes;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.WrenchItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class FlowArrowClient {
    private FlowArrowClient() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof WrenchItem) && !(mc.player.getOffhandItem().getItem() instanceof WrenchItem)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof ConduitBlockEntity be) || (be.flow() == 0 && !WrenchItem.outline)) return;
        int flow = be.flow();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(NetScanRenderTypes.LINES);
        ps.pushPose();
        ps.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        PoseStack.Pose pose = ps.last();
        Vec3 c = new Vec3(0.5, 0.5, 0.5);
        if (WrenchItem.outline) mc.level.getBlockState(pos).getShape(mc.level, pos).forAllEdges((x1, y1, z1, x2, y2, z2) ->
                line(lines, pose, new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), 0xFFFFFF));
        for (Direction d : Direction.values()) {
            Vec3 face = c.add(Vec3.atLowerCornerOf(d.getNormal()).scale(0.5));
            if ((flow & 1 << d.ordinal()) != 0) arrow(lines, pose, face, c, d.getOpposite());
            if ((flow & 1 << (d.ordinal() + 6)) != 0) arrow(lines, pose, c, face, d);
        }
        ps.popPose();
        buf.endBatch(NetScanRenderTypes.LINES);
    }

    private static void arrow(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to, Direction d) {
        line(vc, pose, from, to, 0xFFD228);
        Vec3 back = to.subtract(Vec3.atLowerCornerOf(d.getNormal()).scale(0.15));
        for (Direction s : Direction.values()) {
            if (s.getAxis() == d.getAxis()) continue;
            line(vc, pose, to, back.add(Vec3.atLowerCornerOf(s.getNormal()).scale(0.1)), 0xFFD228);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, int rgb) {
        Vec3 n = b.subtract(a).normalize();
        vc.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
