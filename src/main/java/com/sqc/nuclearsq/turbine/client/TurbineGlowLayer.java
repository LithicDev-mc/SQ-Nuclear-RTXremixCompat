package com.sqc.nuclearsq.turbine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class TurbineGlowLayer extends GeoRenderLayer<TurbineControllerBlockEntity> {
    public static final int FRAMES = 8, TICKS_PER_FRAME = 3;
    private static final RenderType[] TYPES = new RenderType[FRAMES];

    static {
        for (int k = 0; k < FRAMES; k++) {
            TYPES[k] = RenderType.entityTranslucentEmissive(
                    ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/block/turbine_glow_" + k + ".png"));
        }
    }

    public TurbineGlowLayer(GeoRenderer<TurbineControllerBlockEntity> renderer) {
        super(renderer);
    }

    public static boolean isGlow(RenderType t) {
        for (RenderType r : TYPES) if (r == t) return true;
        return false;
    }

    static RenderType current(TurbineControllerBlockEntity be) {
        return be.spin() <= 0 ? null : TYPES[(int) (RenderUtil.getCurrentTick() / TICKS_PER_FRAME) % FRAMES];
    }

    @Override
    public void render(PoseStack poseStack, TurbineControllerBlockEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType type;
        if (com.sqc.nuclearsq.machine.big.client.BigMachineGlowLayer.capturing()) {
            type = TYPES[0];
        } else {
            if (animatable.spin() <= 0 || !com.sqc.nuclearsq.NuclearClientConfig.inGlowRange(animatable.getBlockPos())) return;
            type = TYPES[(int) (RenderUtil.getCurrentTick() / TICKS_PER_FRAME) % FRAMES];
        }
        com.sqc.nuclearsq.machine.big.client.GlowCubes.begin("turbine");
        try {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 0xFFFFFFFF);
        } finally {
            com.sqc.nuclearsq.machine.big.client.GlowCubes.end();
        }
    }
}
