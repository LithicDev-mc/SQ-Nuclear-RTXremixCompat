package com.sqc.nuclearsq.exo;

import java.util.List;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class ExoCoreItem extends Item {
    public static final int[] CAPACITY = {0, 100_000, 400_000, 1_600_000, 6_400_000, 25_600_000};
    public static final int[] MODULE_SLOTS = {0, 2, 3, 4, 5, 6};
    public static final int BATTERY_PERCENT = 20;
    public static final int MAX_RECEIVE = 20_000;

    public ExoCoreItem(Properties props) {
        super(props.stacksTo(1));
    }

    public static int tier(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ExoRegistry.TIER.get(), 1), 1, ExoRegistry.MAX_TIER);
    }

    public static int capacity(ItemStack stack) {
        return (int) (CAPACITY[tier(stack)] * (100L + stack.getOrDefault(ExoRegistry.BATTERY.get(), 0)) / 100);
    }

    public static int batteryBonus(ItemStack piece) {
        int best = 0;
        for (ModuleEntry m : piece.getOrDefault(ExoRegistry.MODULES.get(), List.<ModuleEntry>of()))
            if (ExoModule.of(m.id()) instanceof ExoModule x && x.kind() == ExoModule.Kind.BATTERY) best = Math.max(best, BATTERY_PERCENT * m.level());
        return best;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player p) || p.tickCount % 20 != 0) return;
        int b = 0;
        for (ItemStack a : p.getArmorSlots()) b = Math.max(b, batteryBonus(a));
        if (stack.getOrDefault(ExoRegistry.BATTERY.get(), 0) != b) stack.set(ExoRegistry.BATTERY.get(), b);
    }

    public static int moduleSlots(ItemStack stack) {
        return MODULE_SLOTS[tier(stack)];
    }

    public static int energy(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0), 0, capacity(stack));
    }

    public static boolean drain(Player player, int fe) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack c = inv.getItem(i);
            if (c.getItem() instanceof ExoCoreItem && energy(c) >= fe) {
                c.set(ModDataComponents.STORED_ENERGY.get(), energy(c) - fe);
                return true;
            }
        }
        return false;
    }

    public static IEnergyStorage storage(ItemStack stack) {
        return new ComponentEnergyStorage(stack, ModDataComponents.STORED_ENERGY.get(), capacity(stack), MAX_RECEIVE, 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3AB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.nuclearsq.exo_core.tier", tier(stack)));
        lines.add(Component.translatable("tooltip.nuclearsq.exo_core.energy", energy(stack), capacity(stack)));
    }
}
