package com.sqc.nuclearsq.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ComputeModuleItem extends Item {
    public final String valuesId;

    public ComputeModuleItem(Properties props, String valuesId) {
        super(props.stacksTo(16));
        this.valuesId = valuesId;
    }

    public StorageValues.Compute values() {
        return StorageValues.module(valuesId);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        StorageValues.Compute c = values();
        if (c.cpu() > 0) lines.add(Component.translatable("tooltip.nuclearsq.compute.cpu", c.cpu()).withStyle(ChatFormatting.GRAY));
        if (c.ram() > 0) lines.add(Component.translatable("tooltip.nuclearsq.compute.ram", StorageValues.gb(c.ram())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.drive.power", c.fePerTick()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
