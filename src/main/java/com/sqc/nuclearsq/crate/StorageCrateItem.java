package com.sqc.nuclearsq.crate;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

public class StorageCrateItem extends BlockItem {
    public StorageCrateItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        long used = stack.getOrDefault(CrateRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).nonEmptyStream().count();
        ItemStack up = stack.getOrDefault(CrateRegistry.UPGRADE.get(), ItemContainerContents.EMPTY).copyOne();
        int cap = StorageCrateBlockEntity.capacityFor(up);
        tip.add(Component.translatable("tooltip.nuclearsq.storage_crate.filled", used, cap).withStyle(used == 0 ? ChatFormatting.GRAY : ChatFormatting.AQUA));
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }
}
