package com.sqc.nuclearsq.explosion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.bomb.FallingBombEntity;
import com.sqc.nuclearsq.explosion.bomb.MultiPartBombBlock;
import com.sqc.nuclearsq.registry.BombRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class BombClient {
    private BombClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BombRegistry.FALLING_BOMB.get(), BombRenderer::new);
    }

    private static class BombRenderer extends FallingBlockRenderer {
        private final BlockRenderDispatcher dispatcher;

        BombRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.dispatcher = context.getBlockRenderDispatcher();
        }

        @Override
        public boolean shouldRender(FallingBlockEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                    double camX, double camY, double camZ) {
            return entity.shouldRender(camX, camY, camZ);
        }

        @Override
        public void render(FallingBlockEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light) {
            if (!(entity instanceof FallingBombEntity bomb) || !(bomb.bomb() instanceof MultiPartBombBlock multi)) {
                super.render(entity, yaw, partialTicks, pose, buffer, light);
                return;
            }
            float half = multi.width() / 2.0f;
            shadowRadius = half;
            BlockState state = entity.getBlockState();
            for (int part : multi.modelParts()) {
                pose.pushPose();
                pose.translate(multi.dx(part) - half, multi.dy(part), multi.dz(part) - half);
                drawPart(entity, state.setValue(multi.partProperty(), part), pose, buffer);
                pose.popPose();
            }
        }

        private void drawPart(FallingBlockEntity entity, BlockState state, PoseStack pose, MultiBufferSource buffer) {
            var model = dispatcher.getBlockModel(state);
            BlockPos at = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            long seed = state.getSeed(entity.getStartPos());
            for (RenderType type : model.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY)) {
                dispatcher.getModelRenderer().tesselateBlock(entity.level(), model, state, at, pose,
                        buffer.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), false, RandomSource.create(), seed,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
            }
        }
    }
}
