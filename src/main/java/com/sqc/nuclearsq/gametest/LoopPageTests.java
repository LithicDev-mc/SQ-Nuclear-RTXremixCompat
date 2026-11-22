package com.sqc.nuclearsq.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.PlantLoops;
import com.sqc.nuclearsq.progression.PrimaryLoop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class LoopPageTests {
    private LoopPageTests() {}

    private static JsonObject json(String path) {
        try (var in = LoopPageTests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new GameTestAssertException(path + " nicht lesbar: " + ex);
        }
    }

    private static void check(GameTestHelper h, String page, PrimaryLoop.Diagram d, List<String> must) {
        var nodes = d.nodes();
        int[] order = d.order();
        if (order.length != nodes.size()) h.fail(page + ": Reihenfolge hat " + order.length + " statt " + nodes.size() + " Eintraege");
        Set<Integer> seen = new HashSet<>();
        for (int k : order) {
            if (k < 0 || k >= nodes.size() || !seen.add(k)) h.fail(page + ": Knoten " + k + " fehlt oder ist doppelt in der Reihenfolge");
        }
        Set<String> keys = new HashSet<>(), kinds = new HashSet<>();
        Set<Integer> linked = new HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) h.fail(page + ": Knoten " + n.key() + " doppelt");
            if (n.x() < 0 || n.x() > d.width() || n.y() < 0 || n.y() > d.height()) h.fail(page + ": " + n.key() + " liegt ausserhalb der Seite");
            if (BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item())) == Items.AIR)
                h.fail(page + ": Item " + n.item() + " ist nicht registriert");
            for (String suffix : List.of("", "_small")) {
                String img = "/assets/" + NuclearSQ.MODID + "/textures/gui/compendium/machines/" + n.item() + suffix + ".png";
                if (LoopPageTests.class.getResource(img) == null) h.fail(page + ": Kompendium-Bild fehlt: " + img);
            }
        }
        for (String want : must) if (!keys.contains(want)) h.fail(page + ": ohne " + want);
        for (var f : d.flows()) {
            if (f.from() < 0 || f.from() >= nodes.size() || f.to() < 0 || f.to() >= nodes.size() || f.from() == f.to())
                h.fail(page + ": Leitung " + f.kind() + " verbindet ungueltige Knoten");
            int[] p = f.path();
            if (p.length < 4 || p.length % 2 != 0) h.fail(page + ": Leitung " + f.kind() + " hat keinen gueltigen Weg");
            for (int i = 0; i < p.length; i += 2) {
                if (p[i] < 0 || p[i] > d.width() || p[i + 1] < 0 || p[i + 1] > d.height()) h.fail(page + ": Leitung " + f.kind() + " verlaesst die Seite");
            }
            kinds.add(f.kind());
            linked.add(f.from());
            linked.add(f.to());
        }
        for (int k = 0; k < nodes.size(); k++) {
            if (k != d.hull() && !linked.contains(k)) h.fail(page + ": " + nodes.get(k).key() + " haengt an keiner Leitung");
        }
        for (String lang : List.of("de_de", "en_us")) {
            JsonObject l = json("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json");
            for (String k : List.of("compendium." + page + ".title", "compendium." + page + ".text"))
                if (!l.has(k)) h.fail(lang + ": " + k + " fehlt");
            for (var n : nodes) {
                for (String part : List.of("name", "short", "hint"))
                    if (!l.has("compendium.loop." + n.key() + "." + part)) h.fail(lang + ": compendium.loop." + n.key() + "." + part + " fehlt");
                if (!l.has("compendium." + n.page() + ".title")) h.fail(lang + ": Zielseite " + n.page() + " hat keinen Titel");
            }
            for (String kind : kinds)
                if (!l.has("compendium.loop.pwr_flow." + kind)) h.fail(lang + ": compendium.loop.pwr_flow." + kind + " fehlt");
        }
    }

    @GameTest(template = "floor", batch = "compendium_loop_levels", timeoutTicks = 20)
    public static void extendedAndFullLoopPagesAreComplete(GameTestHelper h) {
        check(h, "reactor_pwr_extended", PlantLoops.EXTENDED, List.of("pwr_pressurizer", "pwr_relief", "pwr_hp_injection", "tower"));
        check(h, "reactor_pwr_full", PlantLoops.FULL, List.of("pwr_pressurizer", "pwr_relief", "pwr_hp_injection", "condenser", "tower", "pump"));
        Map<String, String> anims = new java.util.HashMap<>();
        JsonObject root = json("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json");
        for (var s : root.getAsJsonArray("sections"))
            for (var c : s.getAsJsonObject().getAsJsonArray("chapters"))
                for (var p : c.getAsJsonObject().getAsJsonArray("pages"))
                    if (p.isJsonObject()) anims.put(p.getAsJsonObject().get("id").getAsString(), p.getAsJsonObject().get("anim").getAsString());
        if (!"PRIMARY_EXT".equals(anims.get("reactor_pwr_extended"))) h.fail("Seite reactor_pwr_extended fehlt in der Struktur");
        if (!"PRIMARY_FULL".equals(anims.get("reactor_pwr_full"))) h.fail("Seite reactor_pwr_full fehlt in der Struktur");
        h.succeed();
    }
}
