package com.sqc.nuclearsq.radiation;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class ItemContaminationEvents {
    private ItemContaminationEvents() {}

    private static final float TOOLTIP_RED = 0.001f;

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity ie = event.getItemEntity();
        if (!(ie.level() instanceof ServerLevel lv)) return;
        ItemContamination.contaminateFromGround(lv, ie);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel lv) || event.getDrops().isEmpty()) return;
        double svH = ItemContamination.dropContamination(lv, e);
        if (svH <= 0) return;
        for (ItemEntity ie : event.getDrops()) ItemContamination.atLeast(ie.getItem(), (float) svH);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getDrops().isEmpty()) return;
        float ground = ItemContamination.groundAt(event.getLevel(), event.getPos());
        if (ground <= 0) return;
        float svH = (float) (ground * ItemContamination.PICKUP_FROM_GROUND);
        for (ItemEntity ie : event.getDrops()) ItemContamination.atLeast(ie.getItem(), svH);
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide()) return;
        double add = ItemContamination.eatDose(event.getItem());
        if (add <= 0) return;
        Contamination.setBody(p, Contamination.body(p) + add);
        p.displayClientMessage(Component.translatable("message.nuclearsq.ate_contaminated").withStyle(ChatFormatting.RED), false);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack s = event.getItemStack();
        float c = ItemContamination.get(s);
        if (c <= 0) return;
        event.getToolTip().add(Component.translatable("tooltip.nuclearsq.contaminated", RadiationManager.fmtRate(c))
                .withStyle(c >= TOOLTIP_RED ? ChatFormatting.RED : ChatFormatting.YELLOW));
    }
}
