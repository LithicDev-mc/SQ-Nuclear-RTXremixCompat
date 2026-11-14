package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class PatternRecipes {
    private PatternRecipes() {}

    public static final int NONE = 0, CRAFTING = 1, SMALL_PARTS = 2, ASSEMBLER = 3;

    public record Match(Pattern pattern, int kind) {}

    @Nullable
    public static Match match(Level level, List<ItemStack> grid) {
        List<ItemStack> stacks = new ArrayList<>();
        boolean piles = false;
        for (ItemStack s : grid) {
            if (s.isEmpty()) continue;
            stacks.add(s);
            piles |= s.getCount() > 1;
        }
        if (stacks.isEmpty()) return null;
        Match m = piles ? null : crafting(level, grid);
        if (m == null) m = smallParts(level, stacks);
        if (m == null) m = assembler(level, stacks);
        if (m == null && piles) m = crafting(level, grid);
        return m;
    }

    @Nullable
    private static Match crafting(Level level, List<ItemStack> grid) {
        List<ItemStack> ones = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) ones.add(i < grid.size() && !grid.get(i).isEmpty() ? grid.get(i).copyWithCount(1) : ItemStack.EMPTY);
        CraftingInput in = CraftingInput.of(3, 3, ones);
        var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, in, level);
        if (found.isEmpty()) return null;
        CraftingRecipe r = found.get().value();
        ItemStack out = r.assemble(in, level.registryAccess());
        if (out.isEmpty()) return null;
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack s : r.getRemainingItems(in)) if (!s.isEmpty()) rest.add(s);
        return new Match(new Pattern(Pattern.merge(ones), new Pattern.Part(out, out.getCount()), Pattern.merge(rest)), CRAFTING);
    }

    @Nullable
    private static Match smallParts(Level level, List<ItemStack> stacks) {
        SmallPartsRecipe.Input in = new SmallPartsRecipe.Input(stacks);
        for (RecipeHolder<SmallPartsRecipe> h : level.getRecipeManager().getAllRecipesFor(SmallPartsRegistry.TYPE.get())) {
            SmallPartsRecipe r = h.value();
            if (!r.matches(in, level)) continue;
            List<ItemStack> used = take(stacks, r.ingredients().stream().map(n -> new Need(n.ingredient()::test, n.count())).toList());
            return new Match(new Pattern(Pattern.merge(used), new Pattern.Part(r.result(), r.result().getCount()), List.of()), SMALL_PARTS);
        }
        return null;
    }

    @Nullable
    private static Match assembler(Level level, List<ItemStack> stacks) {
        AssemblyRecipe r = AssemblyRecipe.find(level.getRecipeManager(), stacks);
        if (r == null) return null;
        int[][] plan = r.plan(stacks);
        if (plan == null) return null;
        List<ItemStack> used = new ArrayList<>();
        for (int[] row : plan) {
            for (int j = 0; j < row.length; j++) if (row[j] > 0) used.add(stacks.get(j).copyWithCount(row[j]));
        }
        ItemStack out = r.result();
        return new Match(new Pattern(Pattern.merge(used), new Pattern.Part(out, out.getCount()), List.of()), ASSEMBLER);
    }

    private record Need(java.util.function.Predicate<ItemStack> test, int count) {}

    private static List<ItemStack> take(List<ItemStack> stacks, List<Need> needs) {
        int[] left = new int[stacks.size()];
        for (int i = 0; i < left.length; i++) left[i] = stacks.get(i).getCount();
        List<ItemStack> out = new ArrayList<>();
        for (Need n : needs) {
            int want = n.count;
            for (int i = 0; i < stacks.size() && want > 0; i++) {
                if (left[i] <= 0 || !n.test.test(stacks.get(i))) continue;
                int k = Math.min(want, left[i]);
                left[i] -= k;
                want -= k;
                out.add(stacks.get(i).copyWithCount(k));
            }
        }
        return out;
    }
}
