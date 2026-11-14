package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.CompendiumItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CompendiumPageTests {
    private CompendiumPageTests() {}

    private static final String FLOOR = "floor";

    static Set<String> pageIds() {
        try (var in = CompendiumPageTests.class.getResourceAsStream("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json")) {
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> ids = new HashSet<>();
            for (var s : root.getAsJsonArray("sections"))
                for (var c : s.getAsJsonObject().getAsJsonArray("chapters"))
                    for (var p : c.getAsJsonObject().getAsJsonArray("pages"))
                        ids.add(p.isJsonObject() ? p.getAsJsonObject().get("id").getAsString() : p.getAsString());
            return ids;
        } catch (Exception ex) {
            throw new GameTestAssertException("kompendium_struktur.json nicht lesbar: " + ex);
        }
    }

    static boolean exempt(String path) {
        return path.startsWith("header_") || path.equals("spacer_sqc") || path.startsWith("geiger_counter_needle_")
                || path.matches("exo_core_sqc_\\d+");
    }

    @GameTest(template = FLOOR, batch = "compendium_pages", timeoutTicks = 20)
    public static void everyItemHasACompendiumPage(GameTestHelper h) {
        Set<String> pages = pageIds();
        List<String> problems = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(NuclearSQ.MODID) || exempt(id.getPath())) continue;
            String page = CompendiumItems.page(id.getPath());
            if (page == null) problems.add(id.getPath() + ": keine Seite");
            else if (!pages.contains(page)) problems.add(id.getPath() + ": Seite " + page + " fehlt in der Struktur");
        }
        for (var m : List.of(CompendiumItems.RECIPE_ITEMS, CompendiumItems.MORE_ITEMS))
            for (String pg : m.keySet()) if (!pages.contains(pg)) problems.add("Seite " + pg + " aus CompendiumItems fehlt in der Struktur");
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_pages", timeoutTicks = 20)
    public static void modScopeShowsEveryItem(GameTestHelper h) {
        var scope = com.sqc.nuclearsq.progression.ModScope.build(com.sqc.nuclearsq.progression.RecipeSources.all(h.getLevel().getRecipeManager(), h.getLevel().registryAccess()));
        Set<net.minecraft.world.item.Item> shown = new HashSet<>();
        Set<Long> slots = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (var n : scope.nodes) {
            shown.add(n.item());
            var b = scope.chapters.get(n.box());
            if (!slots.add(((long) n.x() << 32) | (n.y() & 0xFFFFFFFFL))) problems.add(n.item() + ": Platz doppelt belegt");
            if (n.x() < b.x() || n.y() < b.y() || n.x() + com.sqc.nuclearsq.progression.ModScope.SLOT > b.x() + b.w() || n.y() + com.sqc.nuclearsq.progression.ModScope.SLOT > b.y() + b.h())
                problems.add(n.item() + ": liegt nicht im Kapitel");
        }
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(NuclearSQ.MODID) && !exempt(id.getPath()) && !shown.contains(item)) problems.add(id.getPath() + ": fehlt in All Items");
        }
        for (double t : scope.time) if (!(t >= 0 && t <= scope.duration)) problems.add("Aufbauzeit ausserhalb: " + t);
        if (scope.edges.isEmpty()) problems.add("keine Verbindungen");
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }
}
