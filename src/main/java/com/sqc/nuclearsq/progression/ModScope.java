package com.sqc.nuclearsq.progression;

import com.sqc.nuclearsq.NuclearSQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModScope {
    public static final int SLOT = 18, PITCH = 22, PAD = 8, HEAD = 14, SEC_HEAD = 22, GAP = 6, SEC_GAP = 12;
    private static final String START = "uranium_ore_sqc";

    public record Node(Item item, String page, int box, int x, int y, boolean machine) {}

    public record Box(String key, int parent, int x, int y, int w, int h) {}

    public record Edge(int from, int to, boolean machine) {}

    public final List<Node> nodes = new ArrayList<>();
    public final List<Box> sections = new ArrayList<>(), chapters = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    public final List<List<Integer>> adj = new ArrayList<>();
    public float[] time = new float[0];
    public int[] order = new int[0];
    public int width, height;
    public float duration;

    private ModScope() {}

    public static boolean exempt(String path) {
        return path.startsWith("header_") || path.equals("spacer_sqc") || path.startsWith("geiger_counter_needle_") || path.matches("exo_core_sqc_\\d+");
    }

    private record Chap(String section, String id, List<String> pages) {}

    private static List<Chap> book() {
        try (var in = ModScope.class.getResourceAsStream("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json")) {
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            List<Chap> out = new ArrayList<>();
            for (var sec : root.getAsJsonArray("sections"))
                for (var ch : sec.getAsJsonObject().getAsJsonArray("chapters")) {
                    List<String> pages = new ArrayList<>();
                    for (var pe : ch.getAsJsonObject().getAsJsonArray("pages")) pages.add(pe.getAsJsonObject().get("id").getAsString());
                    out.add(new Chap(sec.getAsJsonObject().get("id").getAsString(), ch.getAsJsonObject().get("id").getAsString(), pages));
                }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("kompendium_struktur.json nicht lesbar: " + e, e);
        }
    }

    private static boolean side(String section) { return section.equals("grp_start") || section.equals("achievements"); }

    private static final class Cell {
        final List<Integer> items = new ArrayList<>();
        int cols, cols0, x, y, w, h;
    }

    private static List<List<Cell>> rows(List<Cell> cells, int maxW, int minW, int gap) {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        int x = 0, w = minW;
        for (Cell c : cells) {
            if (!row.isEmpty() && x + c.w > maxW) { rows.add(row); row = new ArrayList<>(); x = 0; }
            row.add(c);
            x += c.w + gap;
            w = Math.max(w, x - gap);
        }
        if (!row.isEmpty()) rows.add(row);
        for (List<Cell> r : rows) {
            int sum = 0, rx = 0;
            for (Cell c : r) sum += c.w;
            int extra = w - sum - gap * (r.size() - 1), left = extra;
            for (int i = 0; i < r.size(); i++) {
                Cell c = r.get(i);
                int add = i == r.size() - 1 ? left : extra * c.w / sum;
                left -= add;
                c.x = rx;
                c.w += add;
                rx += c.w + gap;
            }
        }
        return rows;
    }

    private static int[] stack(List<List<Cell>> rows, int gap) {
        int y = 0, w = 0;
        for (List<Cell> r : rows) {
            int h = 0;
            for (Cell c : r) h = Math.max(h, c.h);
            for (Cell c : r) { c.y = y; c.h = h; w = Math.max(w, c.x + c.w); }
            y += h + gap;
        }
        return new int[] {w, y - gap, rows.size()};
    }

    private static int[] shelf(List<Cell> cells, int maxW, int gap) { return stack(rows(cells, maxW, 0, gap), gap); }

    private static int chapters(List<Cell> cs, int iw) {
        for (Cell c : cs) { c.cols = c.cols0; size(c); }
        var rows = rows(cs, iw, iw, GAP);
        for (Cell c : cs) {
            int w = c.w;
            c.cols = Math.max(c.cols0, Math.min(c.items.size(), (w - 2 * PAD + PITCH - SLOT) / PITCH));
            size(c);
            c.w = w;
        }
        return stack(rows, GAP)[1];
    }

    public static ModScope build(Collection<RecipeSources.Source> sources) {
        ModScope s = new ModScope();
        List<Chap> book = book();
        Map<String, String> exact = new HashMap<>(), family = new HashMap<>();
        Map<String, Integer> chapterOfPage = new HashMap<>();
        for (boolean late : new boolean[] {false, true})
            for (int c = 0; c < book.size(); c++) {
                if (side(book.get(c).section()) != late) continue;
                for (String pg : book.get(c).pages()) {
                    chapterOfPage.put(pg, c);
                    for (var m : List.of(CompendiumItems.RECIPE_ITEMS, CompendiumItems.MORE_ITEMS)) {
                        List<String> ids = m.get(pg);
                        if (ids == null) continue;
                        for (String id : ids) { exact.putIfAbsent(id, pg); family.putIfAbsent(CompendiumItems.family(id), pg); }
                    }
                }
            }
        Map<Item, Integer> index = new LinkedHashMap<>();
        List<Item> items = new ArrayList<>();
        List<String> pages = new ArrayList<>();
        List<Integer> chap = new ArrayList<>();
        int vanilla = book.size(), kits = vanilla + 1;
        for (Item item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(NuclearSQ.MODID) || exempt(id.getPath())) continue;
            String pg = exact.get(id.getPath());
            if (pg == null) pg = family.get(CompendiumItems.family(id.getPath()));
            if (pg == null) pg = CompendiumItems.page(id.getPath());
            Integer c = pg == null ? null : chapterOfPage.get(pg);
            if (c == null) continue;
            if (id.getPath().endsWith("_kit_sqc")) c = kits;
            index.put(item, items.size());
            items.add(item);
            pages.add(pg);
            chap.add(c);
        }
        Set<Long> seen = new HashSet<>();
        List<int[]> links = new ArrayList<>();
        for (var src : sources) {
            Integer to = index.get(src.result().getItem());
            if (to == null || !BuiltInRegistries.ITEM.getKey(src.result().getItem()).getNamespace().equals(NuclearSQ.MODID)) continue;
            for (var need : src.needs()) {
                ItemStack[] alts = need.ingredient().getItems();
                List<Item> use = new ArrayList<>();
                for (ItemStack a : alts)
                    if (use.size() < 3 && index.containsKey(a.getItem()) && chap.get(index.get(a.getItem())) != vanilla) use.add(a.getItem());
                if (use.isEmpty() && alts.length > 0) use.add(alts[0].getItem());
                for (Item it : use) {
                    Integer from = index.get(it);
                    if (from == null) {
                        from = items.size();
                        index.put(it, from);
                        items.add(it);
                        pages.add(null);
                        chap.add(vanilla);
                    }
                    if (!from.equals(to) && seen.add(((long) from << 32) | to)) links.add(new int[] {from, to, 0});
                }
            }
            Integer m = index.get(BuiltInRegistries.ITEM.get(src.where().icon));
            if (m != null && chap.get(m) != vanilla && !m.equals(to) && seen.add(((long) m << 32) | to | (1L << 62))) links.add(new int[] {m, to, 1});
        }
        int n = items.size();
        Cell[] cells = new Cell[kits + 1];
        for (int i = 0; i < n; i++) {
            if (cells[chap.get(i)] == null) cells[chap.get(i)] = new Cell();
            cells[chap.get(i)].items.add(i);
        }
        Comparator<Integer> byKind = Comparator.comparing((Integer i) -> {
            String p = BuiltInRegistries.ITEM.getKey(items.get(i)).getPath();
            return p.endsWith("_controller_sqc") ? "0" + p.substring(0, p.length() - 15) + "0" : p.endsWith("_kit_sqc") ? "0" + p.substring(0, p.length() - 8) + "1" : "1";
        }).thenComparing(i -> BuiltInRegistries.ITEM.getId(items.get(i)));
        List<String> secIds = new ArrayList<>();
        for (Chap c : book) if (!secIds.contains(c.section())) secIds.add(c.section());
        secIds.add("kits");
        secIds.add("vanilla");
        List<Cell> secCells = new ArrayList<>();
        List<List<Integer>> secChaps = new ArrayList<>();
        List<String> secKeys = new ArrayList<>();
        for (String sid : secIds) {
            List<Integer> mine = new ArrayList<>();
            for (int c = 0; c <= kits; c++)
                if (cells[c] != null && (c == vanilla ? sid.equals("vanilla") : c == kits ? sid.equals("kits") : book.get(c).section().equals(sid))) mine.add(c);
            if (mine.isEmpty()) continue;
            List<Cell> cs = new ArrayList<>();
            for (int c : mine) {
                Cell cell = cells[c];
                cell.items.sort(c == kits ? Comparator.comparing((Integer i) -> chapterOfPage.get(pages.get(i))).thenComparing(byKind) : byKind);
                int k = cell.items.size();
                cell.cols = cell.cols0 = Math.max(Math.min(k, 4), (int) Math.ceil(Math.sqrt(k * 2.2)));
                cs.add(cell);
            }
            int bestW = 0;
            double best = Double.MAX_VALUE;
            for (int mw = 100; mw <= 1400; mw += 22) {
                for (Cell c : cs) size(c);
                int[] r = shelf(cs, mw, GAP);
                double sc = Math.abs(Math.log((r[0] / (double) r[1]) / 1.35)) + 0.15 * r[2];
                if (sc < best) { best = sc; bestW = mw; }
            }
            for (Cell c : cs) size(c);
            int[] r = shelf(cs, bestW, GAP);
            Cell sec = new Cell();
            sec.w = r[0] + 2 * PAD;
            sec.h = r[1] + SEC_HEAD + PAD;
            secCells.add(sec);
            secChaps.add(mine);
            secKeys.add(sid.equals("vanilla") || sid.equals("kits") ? "compendium.modscope." + sid : "compendium.section." + sid);
        }
        int[] w0 = new int[secCells.size()], h0 = new int[secCells.size()];
        for (int i = 0; i < w0.length; i++) { w0[i] = secCells.get(i).w; h0[i] = secCells.get(i).h; }
        int bestW = 0;
        double best = Double.MAX_VALUE;
        for (int mw = 600; mw <= 2600; mw += 25) {
            for (int i = 0; i < w0.length; i++) { secCells.get(i).w = w0[i]; secCells.get(i).h = h0[i]; }
            int[] r = shelf(secCells, mw, SEC_GAP);
            double sc = Math.abs(Math.log((r[0] / (double) r[1]) / 1.6));
            if (sc < best) { best = sc; bestW = mw; }
        }
        for (int i = 0; i < w0.length; i++) { secCells.get(i).w = w0[i]; secCells.get(i).h = h0[i]; }
        var secRows = rows(secCells, bestW, 0, SEC_GAP);
        for (int i = 0; i < w0.length; i++) {
            List<Cell> cs = new ArrayList<>();
            for (int c : secChaps.get(i)) cs.add(cells[c]);
            secCells.get(i).h = chapters(cs, secCells.get(i).w - 2 * PAD) + SEC_HEAD + PAD;
        }
        int[] tot = stack(secRows, SEC_GAP);
        s.width = tot[0];
        s.height = tot[1];
        int[] nx = new int[n], ny = new int[n], nbox = new int[n];
        for (int si = 0; si < secCells.size(); si++) {
            Cell sec = secCells.get(si);
            s.sections.add(new Box(secKeys.get(si), -1, sec.x, sec.y, sec.w, sec.h));
            List<Integer> mine = secChaps.get(si);
            int ih = sec.h - SEC_HEAD - PAD, maxY = 0;
            for (int c : mine) maxY = Math.max(maxY, cells[c].y);
            for (int c : mine) {
                Cell cell = cells[c];
                if (cell.y == maxY) cell.h = ih - cell.y;
                int bx = sec.x + PAD + cell.x, by = sec.y + SEC_HEAD + cell.y;
                s.chapters.add(new Box(c == vanilla ? "compendium.modscope.vanilla" : c == kits ? "compendium.modscope.kits" : "compendium." + book.get(c).id() + ".chapter", si, bx, by, cell.w, cell.h));
                for (int k = 0; k < cell.items.size(); k++) {
                    int i = cell.items.get(k);
                    nx[i] = bx + PAD + (k % cell.cols) * PITCH;
                    ny[i] = by + HEAD + (k / cell.cols) * PITCH;
                    nbox[i] = s.chapters.size() - 1;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            String p = BuiltInRegistries.ITEM.getKey(items.get(i)).getPath();
            s.nodes.add(new Node(items.get(i), pages.get(i), nbox[i], nx[i], ny[i], pages.get(i) != null && p.endsWith("_controller_sqc")));
            s.adj.add(new ArrayList<>());
        }
        for (int[] l : links) {
            s.adj.get(l[0]).add(s.edges.size());
            s.adj.get(l[1]).add(s.edges.size());
            s.edges.add(new Edge(l[0], l[1], l[2] == 1));
        }
        s.grow(index);
        return s;
    }

    private static void size(Cell c) {
        int rows = (c.items.size() + c.cols - 1) / c.cols;
        c.w = c.cols * PITCH - (PITCH - SLOT) + 2 * PAD;
        c.h = rows * PITCH - (PITCH - SLOT) + HEAD + PAD;
    }

    private void grow(Map<Item, Integer> index) {
        int n = nodes.size();
        time = new float[n];
        order = new int[n];
        if (n == 0) return;
        Integer st = index.get(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, START)));
        int start = st == null ? 0 : st;
        boolean[] vis = new boolean[n];
        Set<Integer> open = new HashSet<>(), front = new HashSet<>();
        double[] d0 = new double[n];
        for (int i = 0; i < n; i++) d0[i] = Math.hypot(nodes.get(i).x() - nodes.get(start).x(), nodes.get(i).y() - nodes.get(start).y());
        int count = 0, next = start;
        float t = 0;
        while (count < n) {
            if (next < 0) {
                for (int i = 0; i < n; i++) if (!vis[i] && (next < 0 || d0[i] < d0[next])) next = i;
            }
            vis[next] = true;
            front.remove(next);
            open.add(nodes.get(next).box());
            order[count] = next;
            time[next] = t;
            t += Math.max(0.05f, 0.8f * (float) Math.exp(-count / 13.0));
            count++;
            for (int e : adj.get(next)) {
                int o = edges.get(e).from() == next ? edges.get(e).to() : edges.get(e).from();
                if (!vis[o]) front.add(o);
            }
            next = -1;
            double bestScore = 0;
            for (int f : front) {
                double sc = d0[f] + (open.contains(nodes.get(f).box()) ? 0 : 120);
                if (next < 0 || sc < bestScore || (sc == bestScore && f < next)) { next = f; bestScore = sc; }
            }
        }
        duration = t + 1.5f;
    }
}
