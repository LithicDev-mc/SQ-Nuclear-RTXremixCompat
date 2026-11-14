package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompendiumSearch {
    public enum Kind { PAGE, MACHINE, ITEM }

    public enum Filter { ALL, PAGES, MACHINES, ITEMS }

    public static final int EXACT = 0, STARTS = 1, TITLE = 2, KEYWORD = 3, TEXT = 4, NONE = -1;

    public record Entry(Kind kind, String id, String title, String keywords, String text, String extra, String page) {}

    public record Found(Entry entry, int rank) {}

    private record Folded(Entry entry, String title, String keys, String all) {}

    private final List<Folded> index = new ArrayList<>();

    public CompendiumSearch(List<Entry> entries) {
        for (Entry e : entries)
            index.add(new Folded(e, fold(e.title()).strip(), fold(e.keywords()), fold(e.title() + "\n" + e.keywords() + "\n" + e.text() + "\n" + e.extra())));
    }

    public int size() { return index.size(); }

    public List<Found> find(String query, Filter filter) {
        List<String> terms = terms(query);
        List<Found> out = new ArrayList<>();
        if (terms.isEmpty()) return out;
        String whole = String.join(" ", terms);
        for (Folded f : index) {
            if (!accepts(filter, f.entry().kind())) continue;
            int r = rank(f, terms, whole);
            if (r != NONE) out.add(new Found(f.entry(), r));
        }
        out.sort(Comparator.comparingInt(Found::rank).thenComparingInt(h -> h.entry().kind().ordinal()));
        return out;
    }

    private static boolean accepts(Filter f, Kind k) {
        return switch (f) {
            case ALL -> k != Kind.MACHINE;
            case PAGES -> k == Kind.PAGE;
            case MACHINES -> k == Kind.MACHINE;
            case ITEMS -> k == Kind.ITEM;
        };
    }

    public static int rank(Entry e, String query) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) return NONE;
        Folded f = new Folded(e, fold(e.title()).strip(), fold(e.keywords()), fold(e.title() + "\n" + e.keywords() + "\n" + e.text() + "\n" + e.extra()));
        return rank(f, terms, String.join(" ", terms));
    }

    private static int rank(Folded f, List<String> terms, String whole) {
        if (!hasAll(f.all(), terms)) return NONE;
        if (hasAll(f.title(), terms)) {
            if (f.title().equals(whole)) return EXACT;
            if (f.title().startsWith(terms.get(0))) return STARTS;
            return TITLE;
        }
        if (!f.keys().isBlank() && hasAll(f.title() + "\n" + f.keys(), terms)) return KEYWORD;
        return TEXT;
    }

    public static String fold(String s) {
        return s == null ? "" : fold0(s).text();
    }

    private record Fold(String text, int[] from, int[] to) {}

    private static final String[] PAIRS = {"ae", "oe", "ue"};

    private static Fold fold0(String s) {
        int n = s.length();
        char[] c = new char[n * 2];
        int[] a = new int[n * 2], b = new int[n * 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            char ch = Character.toLowerCase(s.charAt(i));
            if (ch == '-') continue;
            if (ch == 'ß') {
                c[k] = 's'; a[k] = i; b[k] = i + 1; k++;
                c[k] = 's'; a[k] = i; b[k] = i + 1; k++;
                continue;
            }
            if (ch == '_') ch = ' ';
            else if (ch == 'ä') ch = 'a';
            else if (ch == 'ö') ch = 'o';
            else if (ch == 'ü') ch = 'u';
            c[k] = ch; a[k] = i; b[k] = i + 1; k++;
        }
        for (String pair : PAIRS) {
            int m = 0;
            for (int i = 0; i < k; i++) {
                if (i + 1 < k && c[i] == pair.charAt(0) && c[i + 1] == 'e') {
                    c[m] = c[i]; a[m] = a[i]; b[m] = b[i + 1]; m++;
                    i++;
                } else {
                    c[m] = c[i]; a[m] = a[i]; b[m] = b[i]; m++;
                }
            }
            k = m;
        }
        return new Fold(new String(c, 0, k), Arrays.copyOf(a, k), Arrays.copyOf(b, k));
    }

    public static List<String> terms(String query) {
        if (query == null || query.isBlank()) return List.of();
        return Arrays.stream(fold(query).split("\\s+")).filter(t -> !t.isEmpty()).toList();
    }

    public static boolean hasAll(String folded, List<String> terms) {
        for (String t : terms) if (!folded.contains(t)) return false;
        return true;
    }

    public static List<int[]> spans(String text, List<String> terms) {
        List<int[]> raw = new ArrayList<>();
        if (text == null || text.isEmpty() || terms.isEmpty()) return raw;
        Fold f = fold0(text);
        for (String t : terms) {
            if (t.isEmpty()) continue;
            int at = f.text().indexOf(t);
            while (at >= 0) {
                raw.add(new int[] {f.from()[at], f.to()[at + t.length() - 1]});
                at = f.text().indexOf(t, at + t.length());
            }
        }
        raw.sort(Comparator.comparingInt(r -> r[0]));
        List<int[]> out = new ArrayList<>();
        for (int[] r : raw) {
            int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
            else out.add(r);
        }
        return out;
    }

    public static String snippet(String text, List<String> terms, int before, int max) {
        if (text == null || text.isBlank() || terms.isEmpty()) return "";
        String flat = text.replaceAll("\\s+", " ").strip();
        List<int[]> sp = spans(flat, terms);
        if (sp.isEmpty()) return "";
        int at = sp.get(0)[0], hitEnd = sp.get(0)[1];
        int start = Math.max(0, at - before);
        if (start > 0) {
            int blank = flat.indexOf(' ', start);
            start = blank >= 0 && blank < at ? blank + 1 : at;
        }
        int end = Math.min(flat.length(), Math.max(start + max, hitEnd));
        if (end < flat.length()) {
            int blank = flat.lastIndexOf(' ', end);
            if (blank > hitEnd) end = blank;
        }
        return (start > 0 ? "..." : "") + flat.substring(start, end).strip() + (end < flat.length() ? "..." : "");
    }

    private static final Pattern TAG = Pattern.compile("\\{(item|recipe|drop)=([^}]*)\\}|\\{/?[a-z]+(?:=[^}]*)?\\}|\\*\\*");

    public static String plain(String s, Function<String, String> lang) {
        if (s == null) return "";
        Matcher m = TAG.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep = "";
            if (m.group(1) != null) rep = m.group(1).equals("drop") ? m.group(2) : itemName(lang, m.group(2).trim());
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString().replace("**", "").replaceAll("(?m)^## ", "");
    }

    public static String itemName(Function<String, String> lang, String id) {
        String ns = "nuclearsq", path = id;
        int colon = id.indexOf(':');
        if (colon >= 0) { ns = id.substring(0, colon); path = id.substring(colon + 1); }
        String n = lang.apply("item." + ns + "." + path);
        if (n == null) n = lang.apply("block." + ns + "." + path);
        return n == null ? path : n;
    }

    public static List<String> itemsOf(String pageId) {
        Set<String> out = new LinkedHashSet<>();
        for (var m : List.of(CompendiumItems.RECIPE_ITEMS, CompendiumItems.MORE_ITEMS)) {
            List<String> ids = m.get(pageId);
            if (ids != null) out.addAll(ids);
        }
        for (var nd : AchievementTree.NODES) if (pageId.equals(nd.page())) out.addAll(AchievementTree.proofItems(nd));
        return List.copyOf(out);
    }

    private static void append(StringBuilder sb, String s) {
        if (s == null || s.isBlank()) return;
        if (!sb.isEmpty()) sb.append('\n');
        sb.append(s);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    public static Entry page(String pageId, String sectionId, String chapterId, Function<String, String> lang) {
        String base = "compendium." + pageId;
        StringBuilder text = new StringBuilder();
        append(text, plain(lang.apply(base + ".text"), lang));
        append(text, plain(lang.apply(base + ".tip"), lang));
        if (pageId.startsWith("guide_")) {
            Guide.Chapter gc = Guide.chapter(pageId.substring("guide_".length()));
            if (gc != null) for (Guide.Step st : gc.steps())
                for (String k : List.of(".title", ".text")) append(text, plain(lang.apply("compendium.guide.step." + st.key() + k), lang));
        }
        StringBuilder extra = new StringBuilder();
        append(extra, lang.apply("compendium.section." + sectionId));
        append(extra, lang.apply("compendium." + chapterId + ".chapter"));
        Set<String> seen = new LinkedHashSet<>();
        for (var nodes : List.of(PlantOverview.NODES, AssemblyTree.NODES, AchievementTree.NODES, SecretWeaponsTree.NODES))
            for (var nd : nodes) {
                if (!pageId.equals(nd.page()) || !seen.add(nd.key())) continue;
                append(extra, lang.apply("compendium.plant." + nd.key() + ".name"));
                append(extra, lang.apply("compendium.plant." + nd.key() + ".short"));
            }
        for (String id : itemsOf(pageId)) {
            append(extra, id);
            append(extra, itemName(lang, id));
        }
        return new Entry(Kind.PAGE, pageId, plain(lang.apply(base + ".title"), lang), orEmpty(lang.apply(base + ".keywords")), text.toString(), extra.toString(), pageId);
    }

    public static List<Entry> machines(Function<String, String> lang) {
        List<Entry> out = new ArrayList<>();
        for (var nd : AchievementTree.NODES) {
            String base = "compendium.plant." + nd.key();
            out.add(new Entry(Kind.MACHINE, nd.key(), orEmpty(lang.apply(base + ".name")), "", orEmpty(lang.apply(base + ".short")), "", nd.page()));
        }
        return out;
    }

    public static Entry item(String path, String name) {
        return new Entry(Kind.ITEM, path, orEmpty(name), "", "", path, CompendiumItems.page(path));
    }
}
