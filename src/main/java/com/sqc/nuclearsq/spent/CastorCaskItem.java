package com.sqc.nuclearsq.spent;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

public class CastorCaskItem extends BlockItem {
    public CastorCaskItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int[] c = CastorCaskBlockEntity.counts(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems());
        tooltip.add(Component.translatable("item.nuclearsq.castor_large_sqc.contents", c[0], c[1]).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.nuclearsq.castor_large_sqc.tip").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.nuclearsq.castor_large_sqc.size").withStyle(ChatFormatting.DARK_GRAY));
    }
}
