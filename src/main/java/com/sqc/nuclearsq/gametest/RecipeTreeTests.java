package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.progression.RecipeTree;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeTreeTests {
    private RecipeTreeTests() {}

    private static final String FLOOR = "floor";

    static int depth(RecipeTree.Part p) {
        int d = 0;
        for (var c : p.children()) d = Math.max(d, depth(c) + 1);
        return d;
    }

    @GameTest(template = FLOOR, batch = "compendium_recipe_tree", timeoutTicks = 200)
    public static void everyCraftableItemHasARecipeTree(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        var regs = h.getLevel().registryAccess();
        List<String> problems = new ArrayList<>();
        for (Item item : RecipeSources.craftable(recipes, regs)) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            try {
                RecipeTree.Part root = RecipeTree.build(recipes, regs, new ItemStack(item), 1);
                if (root.source() == null) problems.add(id + ": Wurzel ohne Rezept");
                if (depth(root) >= RecipeTree.MAX_DEPTH) problems.add(id + ": Tiefe " + RecipeTree.MAX_DEPTH + " erreicht, Schleife nicht erkannt");
                if (RecipeTree.rawTotals(root).isEmpty()) problems.add(id + ": keine Rohstoffe");
            } catch (Exception ex) {
                problems.add(id + ": " + ex);
            }
        }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }
}
