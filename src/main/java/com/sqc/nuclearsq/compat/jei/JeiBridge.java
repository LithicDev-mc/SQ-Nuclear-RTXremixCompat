package com.sqc.nuclearsq.compat.jei;

import javax.annotation.Nullable;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

public final class JeiBridge {
    private JeiBridge() {}

    @Nullable
    private static volatile IJeiRuntime runtime;

    static void set(@Nullable IJeiRuntime r) {
        runtime = r;
    }

    public static boolean showRecipe(ItemStack stack) {
        IJeiRuntime r = runtime;
        if (r == null) return false;
        r.getRecipesGui().show(r.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        return true;
    }
}
