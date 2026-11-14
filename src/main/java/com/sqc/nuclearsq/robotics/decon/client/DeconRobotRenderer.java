package com.sqc.nuclearsq.robotics.decon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.decon.DeconRobotEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DeconRobotRenderer extends EntityRenderer<DeconRobotEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/decon_robot.png");
    private final DeconRobotModel model;

    public DeconRobotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new DeconRobotModel(ctx.bakeLayer(DeconRobotModel.LAYER));
        this.shadowRadius = 0.9f;
    }

    @Override
    public boolean shouldRender(DeconRobotEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return super.shouldRender(e, f, x, y, z) && (com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()
                || com.sqc.nuclearsq.client.OcclusionCull.visible(e.level(), e.blockPosition(), e.getBoundingBox()));
    }

    @Override
    public void render(DeconRobotEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float rot = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180f - rot));
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        model.setTracks(Mth.lerp(partial, e.trackLo, e.trackL), Mth.lerp(partial, e.trackRo, e.trackR));
        model.setArm(Mth.lerp(partial, e.armO, e.arm) / DeconRobotEntity.ARM_TICKS);
        model.setupAnim(e, 0f, 0f, e.tickCount + partial, rot, Mth.lerp(partial, e.xRotO, e.getXRot()));
        var vc = buffers.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DeconRobotEntity e) {
        return TEXTURE;
    }
}
