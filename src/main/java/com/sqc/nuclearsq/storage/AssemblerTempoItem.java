package com.sqc.nuclearsq.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AssemblerTempoItem extends Item {
    public AssemblerTempoItem(Properties props) {
        super(props.stacksTo(StorageValues.assembler().tempoMax()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        StorageValues.Assembler v = StorageValues.assembler();
        lines.add(Component.translatable("tooltip.nuclearsq.assembler_tempo.max", v.tempoMax()).withStyle(ChatFormatting.GRAY));
        int n = Math.min(stack.getCount(), v.tempoMax());
        lines.add(Component.translatable("tooltip.nuclearsq.assembler_tempo.stack", n, v.speed(n), v.power(n)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
