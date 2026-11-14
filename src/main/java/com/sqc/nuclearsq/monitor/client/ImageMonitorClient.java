package com.sqc.nuclearsq.monitor.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.monitor.ImageMonitorBlock;
import com.sqc.nuclearsq.monitor.ImageMonitorInputPayload;
import com.sqc.nuclearsq.monitor.ImageMonitorRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ImageMonitorClient {
    private ImageMonitorClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ImageMonitorRegistry.MONITOR_BE.get(), ImageMonitorRenderer::new);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ImageMonitorRenderer.releaseAll();
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ImageMonitorBlock)) return;
        if (!event.getEntity().getMainHandItem().isEmpty()) return;
        if (event.getEntity().isShiftKeyDown()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            Minecraft.getInstance().setScreen(new ImageMonitorControlScreen(event.getPos()));
        } else if (Screen.hasControlDown()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            PacketDistributor.sendToServer(new ImageMonitorInputPayload(event.getPos(), ImageMonitorInputPayload.CYCLE, 0, 0, 0, ""));
        }
    }
}
