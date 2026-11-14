package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.RecipeSources.Need;
import com.sqc.nuclearsq.progression.RecipeSources.Source;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;

public final class RecipeTree {
    private RecipeTree() {}

    public static final int MAX_DEPTH = 24;

    public record Part(ItemStack stack, int count, @Nullable Source source, List<Source> alternatives,
                       List<Part> children, boolean raw, @Nullable Need need) {}

    public static Part build(RecipeManager recipes, HolderLookup.Provider regs, ItemStack target, int count) {
        return build(recipes, regs, target, count, Map.of());
    }

    public static Part build(RecipeManager recipes, HolderLookup.Provider regs, ItemStack target, int count, Map<Item, Integer> choice) {
        return node(recipes, regs, target.copyWithCount(1), Math.max(1, count), null, choice, new HashMap<>(), new HashSet<>(), 0);
    }

    private static Part node(RecipeManager recipes, HolderLookup.Provider regs, ItemStack stack, int count, @Nullable Need need,
                             Map<Item, Integer> choice, Map<Item, List<Source>> cache, Set<Item> path, int depth) {
        Item item = stack.getItem();
        List<Source> all = path.contains(item) || depth >= MAX_DEPTH ? List.of()
                : cache.computeIfAbsent(item, i -> sources(recipes, regs, i));
        if (all.isEmpty()) return new Part(stack, count, null, List.of(), List.of(), true, need);
        int pick = choice.getOrDefault(item, 0);
        if (pick < 0 || pick >= all.size()) pick = 0;
        Source src = all.get(pick);
        List<Source> alt = new ArrayList<>(all);
        alt.remove(pick);
        int per = Math.max(1, src.result().getCount());
        int runs = src.chance() < 1f ? 1 : (count + per - 1) / per;
        path.add(item);
        List<Part> kids = new ArrayList<>();
        for (Need n : src.needs()) {
            ItemStack[] items = n.ingredient().getItems();
            ItemStack show = items.length == 0 ? new ItemStack(Items.BARRIER) : items[0].copyWithCount(1);
            kids.add(node(recipes, regs, show, sat((long) runs * n.count()), n, choice, cache, path, depth + 1));
        }
        path.remove(item);
        return new Part(stack, count, src, List.copyOf(alt), List.copyOf(kids), kids.isEmpty(), need);
    }

    private static List<Source> sources(RecipeManager recipes, HolderLookup.Provider regs, Item item) {
        List<Source> all = RecipeSources.forItem(recipes, regs, item);
        if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(NuclearSQ.MODID)) return all;
        return all.stream().filter(s -> s.type().getNamespace().equals(NuclearSQ.MODID)).toList();
    }

    public static Map<Item, Integer> rawTotals(Part root) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        addRaw(root, out);
        return out;
    }

    private static void addRaw(Part p, Map<Item, Integer> out) {
        if (p.raw()) out.merge(p.stack().getItem(), p.count(), (a, b) -> sat((long) a + b));
        for (Part c : p.children()) addRaw(c, out);
    }

    private static int sat(long v) {
        return (int) Math.min(Integer.MAX_VALUE, v);
    }
}
