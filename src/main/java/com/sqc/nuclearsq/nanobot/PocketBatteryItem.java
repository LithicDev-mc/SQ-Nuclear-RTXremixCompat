package com.sqc.nuclearsq.nanobot;

import java.util.List;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class PocketBatteryItem extends Item {
    public static final int[] CAPACITY = {0, 1_000_000, 4_000_000, 16_000_000};
    public static final int MAX_RECEIVE = 20_000;

    private final int tier;

    public PocketBatteryItem(Properties props, int tier) {
        super(props.stacksTo(1));
        this.tier = tier;
    }

    public int capacity() {
        return CAPACITY[tier];
    }

    public static int energy(ItemStack stack) {
        int cap = stack.getItem() instanceof PocketBatteryItem b ? b.capacity() : 0;
        return Mth.clamp(stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0), 0, cap);
    }

    public IEnergyStorage storage(ItemStack stack) {
        return new ComponentEnergyStorage(stack, ModDataComponents.STORED_ENERGY.get(), capacity(), MAX_RECEIVE, 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3AB8FF;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.nuclearsq.pocket_battery.energy", energy(stack), capacity()));
    }
}
