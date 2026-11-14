package com.sqc.nuclearsq.radiation;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class RespiratorFilterItem extends Item {
    private final boolean combo;

    public RespiratorFilterItem(Properties props, boolean combo) {
        super(props);
        this.combo = combo;
    }

    public boolean combo() {
        return combo;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack filter = player.getItemInHand(hand);
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(mask.getItem() instanceof RespiratorItem)) {
            if (!level.isClientSide()) RespiratorItem.message(player, "message.nuclearsq.filter_no_mask");
            return InteractionResultHolder.fail(filter);
        }
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(filter, true);
        return RespiratorItem.insert(player, mask, filter) ? InteractionResultHolder.sidedSuccess(filter, false) : InteractionResultHolder.fail(filter);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(combo ? "tooltip.nuclearsq.respirator_filter_iodine" : "tooltip.nuclearsq.respirator_filter", RespiratorItem.FILTER_SECONDS / 60).withStyle(ChatFormatting.GRAY));
    }
}
