package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.CompendiumSearch;
import com.sqc.nuclearsq.progression.CompendiumSearch.Entry;
import com.sqc.nuclearsq.progression.CompendiumSearch.Filter;
import com.sqc.nuclearsq.progression.CompendiumSearch.Found;
import com.sqc.nuclearsq.progression.CompendiumSearch.Kind;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CompendiumSearchTests {
    private CompendiumSearchTests() {}

    private static final String FLOOR = "floor";

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static Entry page(String id, String title, String keywords, String text) {
        return new Entry(Kind.PAGE, id, title, keywords, text, "", id);
    }

    private static List<String> ids(List<Found> found) {
        return found.stream().map(f -> f.entry().id()).toList();
    }

    static Map<String, String> lang(String code) {
        try (var in = CompendiumSearchTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + code + ".json")) {
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> out = new HashMap<>();
            for (var e : root.entrySet()) if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
            return out;
        } catch (Exception ex) {
            throw new GameTestAssertException(code + ".json nicht lesbar: " + ex);
        }
    }

    static CompendiumSearch realIndex(Map<String, String> lang) {
        try (var in = CompendiumSearchTests.class.getResourceAsStream("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json")) {
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            List<Entry> all = new ArrayList<>();
            for (var s : root.getAsJsonArray("sections")) {
                String sid = s.getAsJsonObject().get("id").getAsString();
                for (var c : s.getAsJsonObject().getAsJsonArray("chapters")) {
                    String cid = c.getAsJsonObject().get("id").getAsString();
                    for (var p : c.getAsJsonObject().getAsJsonArray("pages")) {
                        String pid = p.isJsonObject() ? p.getAsJsonObject().get("id").getAsString() : p.getAsString();
                        all.add(CompendiumSearch.page(pid, sid, cid, lang::get));
                    }
                }
            }
            all.addAll(CompendiumSearch.machines(lang::get));
            return new CompendiumSearch(all);
        } catch (GameTestAssertException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GameTestAssertException("kompendium_struktur.json nicht lesbar: " + ex);
        }
    }

    @GameTest(template = FLOOR, batch = "compendium_search", timeoutTicks = 20)
    public static void foldingTreatsUmlautsAndCaseAlike(GameTestHelper h) {
        String a = CompendiumSearch.fold("Kühlmittel"), b = CompendiumSearch.fold("KUEHLMITTEL"), c = CompendiumSearch.fold("kuhlmittel");
        check(a.equals(b) && b.equals(c), "Kühlmittel / KUEHLMITTEL / kuhlmittel falten verschieden: " + a + ", " + b + ", " + c);
        check(CompendiumSearch.fold("Straße").equals(CompendiumSearch.fold("STRASSE")), "ß und ss falten verschieden");
        check(CompendiumSearch.fold("Groß-Assembler").equals("grossassembler"), "Groß-Assembler faltet zu " + CompendiumSearch.fold("Groß-Assembler"));
        check(CompendiumSearch.fold("Ä Ö Ü").equals("a o u"), "Ä Ö Ü faltet zu " + CompendiumSearch.fold("Ä Ö Ü"));
        String t = "Kühl und Kuehl";
        List<int[]> sp = CompendiumSearch.spans(t, CompendiumSearch.terms("KUHL"));
        check(sp.size() == 2, "2 Fundstellen erwartet, gefunden " + sp.size());
        check(t.substring(sp.get(0)[0], sp.get(0)[1]).equals("Kühl") && t.substring(sp.get(1)[0], sp.get(1)[1]).equals("Kuehl"),
                "Fundstellen falsch: " + t.substring(sp.get(0)[0], sp.get(0)[1]) + " / " + t.substring(sp.get(1)[0], sp.get(1)[1]));
        CompendiumSearch idx = new CompendiumSearch(List.of(page("p", "Große Kühlpumpe", "", "")));
        check(ids(idx.find("grosse kuehlpumpe", Filter.ALL)).equals(List.of("p")), "Suche mit Umschreibung findet die Seite nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_search", timeoutTicks = 20)
    public static void rankingTitleBeforeKeywordBeforeText(GameTestHelper h) {
        CompendiumSearch idx = new CompendiumSearch(List.of(
                page("text", "Halle", "", "In der Halle steht eine Pumpe."),
                page("other", "Turbine", "", "Dampf und Strom"),
                page("keyword", "Turm", "Pumpe, Wasser", "Hoch und rund"),
                page("contains", "Große Pumpe", "", ""),
                page("starts", "Pumpenhaus", "", ""),
                page("exact", "Pumpe", "", "")));
        List<Found> found = idx.find("pumpe", Filter.ALL);
        check(ids(found).equals(List.of("exact", "starts", "contains", "keyword", "text")), "Reihenfolge falsch: " + ids(found));
        check(found.get(0).rank() == CompendiumSearch.EXACT && found.get(1).rank() == CompendiumSearch.STARTS && found.get(2).rank() == CompendiumSearch.TITLE
                && found.get(3).rank() == CompendiumSearch.KEYWORD && found.get(4).rank() == CompendiumSearch.TEXT, "Raenge falsch");
        CompendiumSearch mixed = new CompendiumSearch(List.of(
                page("seite", "Pumpe", "", ""),
                new Entry(Kind.ITEM, "pump_sqc", "Pumpe", "", "", "pump_sqc", "seite"),
                new Entry(Kind.MACHINE, "pump", "Pumpe", "", "", "", "seite")));
        check(ids(mixed.find("pumpe", Filter.ALL)).equals(List.of("seite", "pump_sqc")), "Alles falsch: " + ids(mixed.find("pumpe", Filter.ALL)));
        check(ids(mixed.find("pumpe", Filter.ITEMS)).equals(List.of("pump_sqc")), "Filter Items falsch");
        check(ids(mixed.find("pumpe", Filter.MACHINES)).equals(List.of("pump")), "Filter Maschinen falsch");
        check(ids(mixed.find("pumpe", Filter.PAGES)).equals(List.of("seite")), "Filter Seiten falsch");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_search", timeoutTicks = 20)
    public static void severalWordsMustAllMatch(GameTestHelper h) {
        CompendiumSearch idx = new CompendiumSearch(List.of(
                page("a", "Große Pumpe", "", "Fördert Wasser."),
                page("b", "Kleine Pumpe", "", "Fördert Öl."),
                page("c", "Wassertank", "", "")));
        check(ids(idx.find("pumpe wasser", Filter.ALL)).equals(List.of("a")), "pumpe wasser: " + ids(idx.find("pumpe wasser", Filter.ALL)));
        check(ids(idx.find("Wasser   Pumpe", Filter.ALL)).equals(List.of("a")), "Wasser Pumpe: " + ids(idx.find("Wasser   Pumpe", Filter.ALL)));
        check(idx.find("pumpe luft", Filter.ALL).isEmpty(), "pumpe luft darf nichts finden");
        check(idx.find("   ", Filter.ALL).isEmpty(), "leere Suche darf nichts finden");
        String snip = CompendiumSearch.snippet("Das ist ein recht langer Satz vor der Hauptkühlmittelpumpe im Primärkreis und danach noch mehr Text.",
                CompendiumSearch.terms("pumpe"), 6, 30);
        check(snip.startsWith("...") && CompendiumSearch.fold(snip).contains("pumpe"), "Textausschnitt falsch: " + snip);
        check(CompendiumSearch.snippet("Ohne Treffer", CompendiumSearch.terms("pumpe"), 6, 30).isEmpty(), "Ausschnitt ohne Treffer muss leer sein");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_search", timeoutTicks = 20)
    public static void abbreviationFrdFindsRodDrive(GameTestHelper h) {
        CompendiumSearch idx = realIndex(lang("de_de"));
        List<Found> frd = idx.find("FRD", Filter.PAGES);
        check(!frd.isEmpty() && frd.get(0).entry().id().equals("rod_drive") && frd.get(0).rank() <= CompendiumSearch.KEYWORD,
                "FRD findet rod_drive nicht als ersten Treffer: " + ids(frd));
        List<Found> title = idx.find("Brennstabantrieb", Filter.PAGES);
        check(!title.isEmpty() && title.get(0).entry().id().equals("rod_drive") && title.get(0).rank() == CompendiumSearch.EXACT,
                "Brennstabantrieb: erster Treffer " + (title.isEmpty() ? "keiner" : title.get(0).entry().id()));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_search", timeoutTicks = 20)
    public static void keywordKeysBelongToPages(GameTestHelper h) {
        Set<String> pages = CompendiumPageTests.pageIds();
        Map<String, String> de = lang("de_de"), en = lang("en_us");
        List<String> problems = new ArrayList<>();
        for (var m : List.of(de, en))
            for (String k : m.keySet()) {
                if (!k.startsWith("compendium.") || !k.endsWith(".keywords")) continue;
                String pg = k.substring("compendium.".length(), k.length() - ".keywords".length());
                if (!pages.contains(pg)) problems.add(k + ": Seite fehlt in der Struktur");
                if (!de.containsKey(k) || !en.containsKey(k)) problems.add(k + ": nicht in de_de und en_us");
            }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems.stream().distinct().toList()));
        h.succeed();
    }
}
