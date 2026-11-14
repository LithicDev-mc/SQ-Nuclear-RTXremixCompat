package com.sqc.nuclearsq.robotics.pet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.pet.RoboPetEntity;
import com.sqc.nuclearsq.robotics.pet.RoboPetRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class RoboPetClient {
    private RoboPetClient() {}

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RoboPetModel.DOG, () -> RoboPetModel.createLayer(false));
        event.registerLayerDefinition(RoboPetModel.CAT, () -> RoboPetModel.createLayer(true));
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RoboPetRegistry.DOG.get(), ctx -> new Renderer(ctx, false));
        event.registerEntityRenderer(RoboPetRegistry.CAT.get(), ctx -> new Renderer(ctx, true));
    }

    private static final class Renderer extends MobRenderer<RoboPetEntity, RoboPetModel> {
        private final ResourceLocation texture;

        Renderer(EntityRendererProvider.Context ctx, boolean cat) {
            super(ctx, new RoboPetModel(ctx.bakeLayer(cat ? RoboPetModel.CAT : RoboPetModel.DOG), cat), cat ? 0.25f : 0.3f);
            this.texture = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, cat ? "textures/entity/robo_cat.png" : "textures/entity/robo_dog.png");
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack ps, MultiBufferSource buf, int light, RoboPetEntity e, float pos, float amt, float pt, float age, float yaw, float pitch) {
                    ItemStack st = e.carried();
                    if (st.isEmpty()) return;
                    ps.pushPose();
                    getParentModel().mouth(ps);
                    ps.mulPose(Axis.XP.rotationDegrees(90f));
                    Minecraft.getInstance().getItemRenderer().renderStatic(st, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
                    ps.popPose();
                }
            });
        }

        @Override
        public boolean shouldRender(RoboPetEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
            return super.shouldRender(e, f, x, y, z) && (com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()
                    || com.sqc.nuclearsq.client.OcclusionCull.visible(e.level(), e.blockPosition(), e.getBoundingBox()));
        }

        @Override
        public ResourceLocation getTextureLocation(RoboPetEntity e) {
            return texture;
        }

        @Override
        protected void scale(RoboPetEntity e, PoseStack pose, float partialTick) {
            pose.scale(0.5f, 0.5f, 0.5f);
        }
    }
}
