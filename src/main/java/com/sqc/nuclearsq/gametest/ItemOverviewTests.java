package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.AchievementTree;
import com.sqc.nuclearsq.progression.CompendiumItems;
import com.sqc.nuclearsq.progression.ItemOverview;
import com.sqc.nuclearsq.progression.RecipeSources;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ItemOverviewTests {
    private ItemOverviewTests() {}

    @GameTest(template = "floor", batch = "compendium_items", timeoutTicks = 20)
    public static void everyItemIsInTheOverview(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        var regs = h.getLevel().registryAccess();
        List<String> problems = new ArrayList<>();
        var entries = ItemOverview.compute(recipes, regs);
        Set<String> machines = new HashSet<>();
        for (var n : AchievementTree.NODES) machines.add(n.item());
        Set<String> families = new HashSet<>();
        for (var e : BuiltInRegistries.ITEM.keySet())
            if (e.getNamespace().equals(NuclearSQ.MODID) && !ItemOverview.hidden(e.getPath(), machines)) families.add(CompendiumItems.family(e.getPath()));
        Map<String, Integer> count = new HashMap<>();
        Map<Item, Integer> at = ItemOverview.buildableAt(RecipeSources.all(recipes, regs));
        for (var e : entries) {
            count.merge(CompendiumItems.family(e.item()), 1, Integer::sum);
            if (e.stage() < 1 || e.stage() > 10) problems.add(e.item() + ": Etappe " + e.stage());
            if (e.group() < 0 || e.group() >= ItemOverview.GROUPS.size()) problems.add(e.item() + ": Gruppe " + e.group());
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, e.item()));
            Integer k = at.get(it);
            if (k != null && e.stage() < ItemOverview.stageAt(k)) problems.add(e.item() + " steht in Etappe " + e.stage() + ", baubar erst in " + ItemOverview.stageAt(k));
        }
        for (String f : families) if (count.getOrDefault(f, 0) != 1) problems.add(f + " steht " + count.getOrDefault(f, 0) + "-mal da");
        for (String lang : List.of("de_de", "en_us")) {
            String path = "/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json";
            try (var in = ItemOverviewTests.class.getResourceAsStream(path)) {
                var l = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> keys = new ArrayList<>(List.of("compendium.items.first"));
                for (String g : ItemOverview.GROUPS) keys.add("compendium.items.group." + g);
                for (String k : keys) if (!l.has(k)) problems.add(lang + ": " + k);
            } catch (Exception ex) {
                problems.add(path + " nicht lesbar: " + ex);
            }
        }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }
}
