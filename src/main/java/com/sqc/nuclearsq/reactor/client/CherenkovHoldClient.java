package com.sqc.nuclearsq.reactor.client;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reactor.Cherenkov;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class CherenkovHoldClient {
    private CherenkovHoldClient() {}

    private static final int HOLD_TICKS = 5;

    private record Pending(BlockHitResult hit, BlockPos holder, int[] ticks) {}

    @Nullable
    private static Pending pending;

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (pending != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isSecondaryUseActive()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockEntity be = Cherenkov.find(mc.level, hit.getBlockPos());
        if (be == null) return;
        pending = new Pending(hit, be.getBlockPos(), new int[1]);
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Pending p = pending;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                || !(mc.level.getBlockEntity(p.holder()) instanceof Cherenkov.Holder h) || !player.getMainHandItem().isEmpty()) {
            pending = null;
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            pending = null;
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, p.hit());
            if (result.consumesAction() && result.shouldSwing()) player.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (++p.ticks()[0] < HOLD_TICKS) return;
        pending = null;
        mc.setScreen(new CherenkovScreen(p.holder(), h.cherenkov()));
    }
}
