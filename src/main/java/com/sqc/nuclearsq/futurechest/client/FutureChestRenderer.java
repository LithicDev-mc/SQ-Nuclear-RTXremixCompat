package com.sqc.nuclearsq.futurechest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.futurechest.FutureChestBlock;
import com.sqc.nuclearsq.futurechest.FutureChestBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

public class FutureChestRenderer implements BlockEntityRenderer<FutureChestBlockEntity> {
    private static final float HALF = 0.5f;
    private static final float HINGE_Y = 11 / 16f, HINGE_Z = 15 / 16f;

    public FutureChestRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public AABB getRenderBoundingBox(FutureChestBlockEntity be) {
        BlockPos p = be.getBlockPos();
        AABB box = new AABB(p).inflate(1);
        return be.animStart() == FutureChestBlockEntity.NONE ? box : box.minmax(new AABB(be.from(), be.from()).inflate(1));
    }

    @Override
    public void render(FutureChestBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || be.getBlockState().getValue(FutureChestBlock.UNFOLDED) || be.animStart() == FutureChestBlockEntity.NONE) return;
        float t = Math.max(0f, level.getGameTime() - be.animStart() + partial);
        Minecraft mc = Minecraft.getInstance();
        BakedModel body = mc.getModelManager().getModel(FutureChestClient.BODY);
        BakedModel lid = mc.getModelManager().getModel(FutureChestClient.LID);
        ModelBlockRenderer mr = mc.getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        float yaw = switch (be.getBlockState().getValue(FutureChestBlock.FACING)) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
        float lidAngle = 0f;
        pose.pushPose();
        if (t < FutureChestBlockEntity.THROW_TICKS) {
            float p = t / FutureChestBlockEntity.THROW_TICKS;
            Vec3 from = be.from().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
            double x = Mth.lerp(p, from.x, 0.5), y = Mth.lerp(p, from.y, HALF / 2) + Math.sin(p * Math.PI) * 0.8, z = Mth.lerp(p, from.z, 0.5);
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotationDegrees(p * 540f));
            pose.mulPose(Axis.XP.rotationDegrees(p * 360f));
            pose.scale(HALF, HALF, HALF);
            pose.translate(-0.5f, -0.5f, -0.5f);
        } else {
            if (!be.landedSound) {
                be.landedSound = true;
                BlockPos bp = be.getBlockPos();
                level.playLocalSound(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.7f, false);
            }
            float u = Mth.clamp((t - FutureChestBlockEntity.THROW_TICKS) / FutureChestBlockEntity.UNFOLD_TICKS, 0f, 1f);
            float s = HALF + HALF * easeOutBack(u);
            pose.translate(0.5f, 0f, 0.5f);
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0f, -0.5f);
            lidAngle = (float) Math.sin(Math.PI * Mth.clamp((u - 0.3f) / 0.6f, 0f, 1f)) * 50f;
        }
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.5f, 0f, -0.5f);
        mr.renderModel(pose.last(), vc, null, body, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        if (lidAngle != 0f) {
            pose.translate(0.5f, HINGE_Y, HINGE_Z);
            pose.mulPose(Axis.XP.rotationDegrees(lidAngle));
            pose.translate(-0.5f, -HINGE_Y, -HINGE_Z);
        }
        mr.renderModel(pose.last(), vc, null, lid, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float k = x - 1f;
        return 1f + c3 * k * k * k + c1 * k * k;
    }
}
