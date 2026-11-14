package com.sqc.nuclearsq.reactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.ReactorProps;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class FuelRodRenderer implements BlockEntityRenderer<FuelRodBlockEntity> {
    public FuelRodRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FuelRodBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (!com.sqc.nuclearsq.NuclearClientConfig.inGlowRange(be.getBlockPos())) return;
        BlockState st = be.getBlockState();
        if (!st.hasProperty(FuelRodBlock.CHARGE) || st.getValue(ReactorProps.DRIVEN) || st.getValue(FuelRodBlock.CHARGE) <= 0) return;
        if (!com.sqc.nuclearsq.client.OcclusionCull.visible(be.getLevel(), be.getBlockPos(), net.minecraft.world.phys.AABB.unitCubeFromLowerCorner(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos())))) return;
        RodGlow.segment(pose, buffers, be.getLevel(), be.getBlockPos(), st.getValue(FuelRodBlock.CHARGE), com.sqc.nuclearsq.reactor.Cherenkov.DEFAULT);
    }

    @Override
    public int getViewDistance() {
        return Math.min(com.sqc.nuclearsq.NuclearClientConfig.viewDistance(), com.sqc.nuclearsq.NuclearClientConfig.glowDistance() + 16);
    }
}
