package com.sqc.nuclearsq.reactor;

import java.util.List;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class FuelRodBlockItem extends BlockItem {
    public FuelRodBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int charge(ItemStack stack) {
        if (unloaded(stack)) return 0;
        return stack.getOrDefault(ModDataComponents.FUEL_CHARGE.get(), FuelRodBlockEntity.UNIT);
    }

    public static boolean mox(ItemStack stack) {
        return stack.has(ModDataComponents.FUEL_MOX.get());
    }

    public static boolean unloaded(ItemStack stack) {
        return stack.has(ModDataComponents.FUEL_UNLOADED.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (unloaded(stack)) {
            tooltip.add(Component.translatable("item.nuclearsq.fuel_rod_block_sqc.unloaded").withStyle(net.minecraft.ChatFormatting.AQUA));
            return;
        }
        if (mox(stack)) tooltip.add(Component.translatable("item.nuclearsq.fuel_rod_block_sqc.mox").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        int remaining = charge(stack);
        tooltip.add(Component.translatable("item.nuclearsq.fuel_rod_block_sqc.charge",
                Math.round(remaining * 100f / FuelRodBlockEntity.UNIT)));
        if (remaining <= 0) {
            tooltip.add(Component.translatable("item.nuclearsq.fuel_rod_block_sqc.spent").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }
}
