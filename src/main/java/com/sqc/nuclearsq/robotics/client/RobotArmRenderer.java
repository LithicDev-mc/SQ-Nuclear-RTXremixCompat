package com.sqc.nuclearsq.robotics.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.RobotArmBlock;
import com.sqc.nuclearsq.robotics.RobotArmBlockEntity;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

public class RobotArmRenderer implements BlockEntityRenderer<RobotArmBlockEntity> {
    private static final RandomSource RAND = RandomSource.create(42);
    private static final Map<BakedModel, BakedQuad[]> QUADS = new IdentityHashMap<>();
    private static final float PIVOT_Y = 11f / 16f;
    private static final float DIP_DEG = 12f, JAW_OPEN = 0.9f, JAW_CLOSED = -0.35f;

    public RobotArmRenderer(BlockEntityRendererProvider.Context context) {
    }

    static ModelResourceLocation model(String block, String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + block + "_" + part));
    }

    static void clearCache() {
        QUADS.clear();
    }

    @Override
    public void render(RobotArmBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();
        if (!com.sqc.nuclearsq.client.OcclusionCull.visible(level, pos, new AABB(pos))) return;
        String block = be.getBlockState().is(RoboticsRegistry.HEAVY_ARM.get()) ? "heavy_robot_arm_sqc" : "robot_arm_sqc";

        float yawSrc = yawTo(be.source(), pos), yawDst = yawTo(be.target(), pos);
        if (Float.isNaN(yawSrc)) yawSrc = yawFacing(be.getBlockState().getValue(RobotArmBlock.FACING));
        if (Float.isNaN(yawDst)) yawDst = yawSrc;

        float yaw = yawSrc, dip = 0f, jaw = 0f;
        boolean holding = false;
        float t = (level.getGameTime() - be.animStart() + partial) / be.animTicks();
        if (t >= 0f && t < 1f) {
            if (t < 0.15f) dip = ease(t / 0.15f);
            else if (t < 0.25f) { dip = 1f; jaw = ease((t - 0.15f) / 0.10f); }
            else if (t < 0.40f) { dip = 1f - ease((t - 0.25f) / 0.15f); jaw = 1f; }
            else if (t < 0.65f) { jaw = 1f; yaw = swing(yawSrc, yawDst, (t - 0.40f) / 0.25f); }
            else if (t < 0.75f) { yaw = yawDst; dip = ease((t - 0.65f) / 0.10f); jaw = 1f; }
            else if (t < 0.85f) { yaw = yawDst; dip = 1f; jaw = 1f - ease((t - 0.75f) / 0.10f); }
            else {
                float k = (t - 0.85f) / 0.15f;
                yaw = swing(yawDst, yawSrc, k);
                dip = 1f - ease(Math.min(1f, k / 0.5f));
            }
        }

        pose.pushPose();
        pose.translate(0.5f, PIVOT_Y, 0.5f);
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-DIP_DEG * dip));
        pose.translate(-0.5f, -PIVOT_Y, -0.5f);
        holding = t >= 0.20f && t < 0.80f && !be.carried().isEmpty();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        draw(pose, vc, model(block, "head"), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        float d = Mth.lerp(jaw, JAW_OPEN, JAW_CLOSED) / 16f;
        pose.translate(-d, 0f, 0f);
        draw(pose, vc, model(block, "jaw_l"), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.translate(2f * d, 0f, 0f);
        draw(pose, vc, model(block, "jaw_r"), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        if (holding) {
            pose.translate(0.5f - d, 0.17f, 1.6f / 16f);
            pose.scale(0.3f, 0.3f, 0.3f);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.carried(), net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        }
        pose.popPose();
    }

    private static float yawTo(BlockPos to, BlockPos from) {
        if (to == null) return Float.NaN;
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return Float.NaN;
        return (float) Math.atan2(-dx, -dz);
    }

    private static float yawFacing(Direction d) {
        return (float) Math.atan2(-d.getStepX(), -d.getStepZ());
    }

    private static float swing(float from, float to, float k) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(to - from));
        return from + (float) Math.toRadians(delta) * ease(k);
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0f, 1f);
        return k * k * (3f - 2f * k);
    }

    private static void draw(PoseStack pose, VertexConsumer vc, ModelResourceLocation loc, int light, int overlay) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(loc);
        BakedQuad[] quads = QUADS.get(m);
        if (quads == null) {
            List<BakedQuad> all = new ArrayList<>(m.getQuads(null, null, RAND, ModelData.EMPTY, null));
            for (Direction d : Direction.values()) all.addAll(m.getQuads(null, d, RAND, ModelData.EMPTY, null));
            QUADS.put(m, quads = all.toArray(new BakedQuad[0]));
        }
        for (BakedQuad q : quads) vc.putBulkData(pose.last(), q, 1f, 1f, 1f, 1f, light, overlay);
    }

    @Override
    public int getViewDistance() {
        return Math.min(48, com.sqc.nuclearsq.NuclearClientConfig.viewDistance());
    }
}
