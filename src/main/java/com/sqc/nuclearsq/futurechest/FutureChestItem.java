package com.sqc.nuclearsq.futurechest;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

public class FutureChestItem extends BlockItem {
    public FutureChestItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        long used = stack.getOrDefault(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).nonEmptyStream().count();
        int cap = FutureChestBlockEntity.BASE_SIZE;
        for (ItemStack up : stack.getOrDefault(FutureChestRegistry.UPGRADES.get(), ItemContainerContents.EMPTY).nonEmptyItems()) {
            if (up.getItem() instanceof QuantumUpgradeItem u && u.kind == QuantumUpgradeItem.Kind.CAPACITY) cap = FutureChestBlockEntity.BASE_SIZE << Math.min(FutureChestBlockEntity.MAX_UPGRADES, up.getCount());
            else if (up.getItem() instanceof QuantumUpgradeItem u) tip.add(Component.translatable(up.getItem().getDescriptionId()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tip.add(Component.translatable("tooltip.nuclearsq.future_chest.filled", used, cap)
                .withStyle(used == 0 ? ChatFormatting.GRAY : ChatFormatting.AQUA));
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }
}
