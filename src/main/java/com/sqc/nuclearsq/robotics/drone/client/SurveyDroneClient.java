package com.sqc.nuclearsq.robotics.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.drone.DroneRegistry;
import com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class SurveyDroneClient {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/survey_drone.png");

    private SurveyDroneClient() {}

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SurveyDroneModel.LAYER, SurveyDroneModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DroneRegistry.DRONE.get(), ctx -> new MobRenderer<SurveyDroneEntity, SurveyDroneModel>(ctx,
                new SurveyDroneModel(ctx.bakeLayer(SurveyDroneModel.LAYER)), 0.4f) {
            @Override
            public void render(SurveyDroneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
                getModel().spin(e, pt);
                super.render(e, yaw, pt, ps, buf, light);
            }

            @Override
            public ResourceLocation getTextureLocation(SurveyDroneEntity e) {
                return TEXTURE;
            }
        });
    }
}
