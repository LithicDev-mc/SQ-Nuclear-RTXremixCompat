package com.sqc.nuclearsq.client;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class RecipeLink {
    private RecipeLink() {}

    private static boolean jei() {
        return ModList.get().isLoaded("jei");
    }

    public static ItemStack machine(BigMachine m) {
        var item = BigMachineRegistry.CONTROLLER_ITEMS.get(m);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    private static BigMachine of(ItemStack stack) {
        for (var e : BigMachineRegistry.CONTROLLER_ITEMS.entrySet()) if (stack.is(e.getValue().get())) return e.getKey();
        return null;
    }

    public static boolean available(ItemStack machine) {
        BigMachine m = of(machine);
        return m != null && MachineRecipes.has(m);
    }

    public static boolean showRecipe(ItemStack stack) {
        return jei() && com.sqc.nuclearsq.compat.jei.JeiBridge.showRecipe(stack);
    }

    public static void show(ItemStack machine) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MachineRecipesScreen open) {
            open.onClose();
            return;
        }
        BigMachine m = of(machine);
        if (m != null && mc.screen instanceof AbstractContainerScreen<?> parent) mc.setScreen(new MachineRecipesScreen(parent, m, machine));
    }
}
