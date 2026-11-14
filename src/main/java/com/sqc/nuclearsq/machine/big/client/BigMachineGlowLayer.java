package com.sqc.nuclearsq.machine.big.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class BigMachineGlowLayer extends GeoRenderLayer<BigMachineBlockEntity> {
    public static final int FRAMES = 8, TICKS_PER_FRAME = 3;
    private static final RenderType[][] TYPES = new RenderType[BigMachine.values().length][FRAMES];

    static {
        for (BigMachine m : BigMachine.values()) {
            for (int k = 0; k < FRAMES; k++) {
                TYPES[m.ordinal()][k] = RenderType.entityTranslucentEmissive(
                        ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/block/" + m.id + "_glow_" + k + ".png"));
            }
        }
    }

    public BigMachineGlowLayer(GeoRenderer<BigMachineBlockEntity> renderer) {
        super(renderer);
    }

    static boolean capture;

    public static boolean capturing() {
        return capture;
    }

    static int glowFrame(BigMachineBlockEntity be) {
        if (com.sqc.nuclearsq.machine.big.BigBattery.handles(be.machine())) {
            int charge = be.chargeLevel();
            return charge <= 0 ? -1 : Math.min(FRAMES, charge) - 1;
        }
        return be.isRunning() ? (int) (RenderUtil.getCurrentTick() / TICKS_PER_FRAME) % FRAMES : -1;
    }

    static RenderType type(BigMachine m, int frame) {
        return TYPES[m.ordinal()][frame];
    }

    static boolean isGlow(RenderType t) {
        for (RenderType[] row : TYPES) for (RenderType r : row) if (r == t) return true;
        return false;
    }

    @Override
    public void render(PoseStack poseStack, BigMachineBlockEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        int frame;
        if (capture) {
            frame = 0;
        } else {
            if (!com.sqc.nuclearsq.NuclearClientConfig.inGlowRange(animatable.getBlockPos())) return;
            frame = glowFrame(animatable);
            if (frame < 0) return;
        }
        RenderType type = TYPES[animatable.machine().ordinal()][frame];
        GlowCubes.begin(animatable.machine().id);
        try {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 0xFFFFFFFF);
        } finally {
            GlowCubes.end();
        }
    }
}
