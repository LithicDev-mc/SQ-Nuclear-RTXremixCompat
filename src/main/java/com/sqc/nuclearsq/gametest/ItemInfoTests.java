package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ItemInfoTests {
    private ItemInfoTests() {}

    private static final String FLOOR = "floor";

    private static JsonObject lang(String code) {
        try (var in = ItemInfoTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + code + ".json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new GameTestAssertException(code + " nicht lesbar: " + ex);
        }
    }

    @GameTest(template = FLOOR, batch = "item_info", timeoutTicks = 20)
    public static void everyItemHasInfo(GameTestHelper h) {
        JsonObject de = lang("de_de"), en = lang("en_us");
        List<String> missing = new ArrayList<>();
        int count = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(NuclearSQ.MODID)) continue;
            if (id.getPath().startsWith("header_") || id.getPath().equals("spacer_sqc")) continue;
            String key = new ItemStack(item).getDescriptionId() + ".info";
            count++;
            for (var l : List.of(de, en)) {
                if (!l.has(key) || l.get(key).getAsString().isBlank()) {
                    missing.add((l == de ? "de_de: " : "en_us: ") + key);
                }
            }
        }
        if (!missing.isEmpty()) {
            h.fail(missing.size() + " Info-Texte fehlen (tools/lang_item_info.py): " + String.join(", ", missing));
        }
        if (count < 200) h.fail("Nur " + count + " Items gezaehlt, Registry unvollstaendig?");
        for (var l : List.of(de, en)) {
            if (!l.has("tooltip.nuclearsq.info.hint")) h.fail("Hinweis tooltip.nuclearsq.info.hint fehlt");
        }
        Set<String> dk = new TreeSet<>(), ek = new TreeSet<>();
        for (String k : de.keySet()) if (k.endsWith(".info")) dk.add(k);
        for (String k : en.keySet()) if (k.endsWith(".info")) ek.add(k);
        if (!dk.equals(ek)) {
            Set<String> onlyDe = new TreeSet<>(dk), onlyEn = new TreeSet<>(ek);
            onlyDe.removeAll(ek);
            onlyEn.removeAll(dk);
            h.fail("Info-Schluessel ungleich, nur de: " + onlyDe + ", nur en: " + onlyEn);
        }
        h.succeed();
    }
}
