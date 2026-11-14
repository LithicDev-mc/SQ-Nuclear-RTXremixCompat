package com.sqc.nuclearsq.spent;

import com.sqc.nuclearsq.reactor.FuelRodBlockItem;
import com.sqc.nuclearsq.registry.SpentFuelRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SpentFuel {
    private SpentFuel() {}

    public static final double HOT_ROD_CARRY_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.HOT_ROD_CARRY_SV_H,
            COOLED_CARRY_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.COOLED_CARRY_SV_H;
    public static final double POOL_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.POOL_SV_H, DRY_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.DRY_SV_H;

    public static boolean isSpentRod(ItemStack stack) {
        return stack.getItem() instanceof FuelRodBlockItem && FuelRodBlockItem.charge(stack) <= 0 && !FuelRodBlockItem.unloaded(stack);
    }

    public static boolean isCooled(ItemStack stack) {
        return stack.is(SpentFuelRegistry.SPENT_FUEL.get());
    }

    public static boolean fitsCastor(ItemStack stack) {
        return isSpentRod(stack) || isCooled(stack);
    }

    public static final String CANISTER = "glass_canister_sqc";

    public static boolean isWaste(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (fitsCastor(stack)) return true;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("nuclearsq") && (id.getPath().equals("filter_residue_sqc") || id.getPath().equals("radioactive_fallout_sqc")
                || id.getPath().equals("worn_wall_tile_sqc") || id.getPath().equals("spent_resin_sqc") || id.getPath().equals(CANISTER));
    }

    public static double carried(Player p) {
        double sv = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (isCooled(s)) sv += s.getCount() * COOLED_CARRY_SV_H;
            else if (BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(CANISTER)) sv += s.getCount() * com.sqc.nuclearsq.radiation.RadiationValues.CANISTER_CARRY_SV_H;
            else if (isSpentRod(s)) sv += s.getCount() * HOT_ROD_CARRY_SV_H;
        }
        return sv;
    }
}
