package com.sqc.nuclearsq.nanobot;

import com.sqc.nuclearsq.exo.ExoCoreItem;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class NanoPower {
    private NanoPower() {}

    public static final int FE_PER_UNIT = 20;

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        NanobotRegistry.POCKET_BATTERIES.forEach(d -> event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> ((PocketBatteryItem) stack.getItem()).storage(stack), d.get()));
    }

    public static boolean hasCore(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof ExoCoreItem) return true;
        return false;
    }

    public static boolean hasPower(Player player) {
        Inventory inv = player.getInventory();
        boolean core = false, battery = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof ExoCoreItem) {
                if (ExoCoreItem.energy(st) >= FE_PER_UNIT) return true;
                core = true;
            } else if (PocketBatteryItem.energy(st) >= FE_PER_UNIT) {
                battery = true;
            }
        }
        return core && battery;
    }

    public static boolean drain(Player player, int amount) {
        int need = amount * FE_PER_UNIT;
        if (ExoCoreItem.drain(player, need)) return true;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof ExoCoreItem) refill(inv, inv.getItem(i));
        return ExoCoreItem.drain(player, need);
    }

    private static void refill(Inventory inv, ItemStack core) {
        for (int i = 0; i < inv.getContainerSize() && ExoCoreItem.energy(core) < ExoCoreItem.capacity(core); i++) {
            ItemStack b = inv.getItem(i);
            int take = Math.min(ExoCoreItem.capacity(core) - ExoCoreItem.energy(core), PocketBatteryItem.energy(b));
            if (!(b.getItem() instanceof PocketBatteryItem) || take <= 0) continue;
            b.set(ModDataComponents.STORED_ENERGY.get(), PocketBatteryItem.energy(b) - take);
            core.set(ModDataComponents.STORED_ENERGY.get(), ExoCoreItem.energy(core) + take);
        }
    }
}
