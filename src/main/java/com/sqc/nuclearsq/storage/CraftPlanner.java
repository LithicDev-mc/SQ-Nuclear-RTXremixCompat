package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftPlanner {
    private CraftPlanner() {}

    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 4000;

    public record Step(Pattern pattern, int crafts) {}

    public record Plan(List<Step> steps, Map<ItemKey, Long> missing, boolean tooComplex, Map<ItemKey, Long> reserved) {
        public Plan(List<Step> steps, Map<ItemKey, Long> missing) {
            this(steps, missing, false, Map.of());
        }

        public boolean ok() {
            return missing.isEmpty() && !steps.isEmpty() && !tooComplex;
        }

        public int size() {
            long n = 0;
            for (Step s : steps) n += s.crafts();
            return (int) Math.min(Integer.MAX_VALUE, n);
        }
    }

    public static Plan plan(StorageAccess storage, Map<ItemKey, Pattern> book, ItemKey target, int amount) {
        Ctx c = new Ctx(storage, book);
        Pattern p = book.get(target);
        if (p == null || amount <= 0) {
            if (amount > 0) c.missing.put(target, (long) amount);
            return new Plan(List.of(), c.missing);
        }
        c.path.add(target);
        c.craft(p, target, amount, 0);
        return new Plan(c.steps, c.missing, c.nodes > MAX_NODES, c.stock);
    }

    private static final class Ctx {
        final StorageAccess storage;
        final Map<ItemKey, Pattern> book;
        final Map<ItemKey, Long> used = new HashMap<>();
        final Map<ItemKey, Long> missing = new LinkedHashMap<>();
        final Map<ItemKey, Long> stock = new LinkedHashMap<>();
        final List<Step> steps = new ArrayList<>();
        final Set<ItemKey> path = new HashSet<>();
        int nodes;

        Ctx(StorageAccess storage, Map<ItemKey, Pattern> book) {
            this.storage = storage;
            this.book = book;
        }

        long free(ItemKey k) {
            return storage.available(k) - used.getOrDefault(k, 0L);
        }

        void need(ItemKey k, long n, int depth) {
            if (++nodes > MAX_NODES) return;
            long take = Math.max(0, Math.min(n, free(k)));
            used.merge(k, take, Long::sum);
            long real = Math.min(take, storage.available(k) - stock.getOrDefault(k, 0L));
            if (real > 0) stock.merge(k, real, Long::sum);
            long left = n - take;
            if (left <= 0) return;
            Pattern p = book.get(k);
            if (p == null || depth >= MAX_DEPTH || path.contains(k)) {
                missing.merge(k, left, Long::sum);
                return;
            }
            path.add(k);
            craft(p, k, left, depth + 1);
            path.remove(k);
            used.merge(k, left, Long::sum);
        }

        void craft(Pattern p, ItemKey out, long amount, int depth) {
            int per = Math.max(1, p.output().count());
            long crafts = (amount + per - 1) / per;
            for (Pattern.Part in : p.inputs()) need(in.key(), in.count() * crafts, depth);
            steps.add(new Step(p, (int) Math.min(Integer.MAX_VALUE, crafts)));
            used.merge(out, -crafts * per, Long::sum);
            for (Pattern.Part r : p.remainders()) used.merge(r.key(), -r.count() * crafts, Long::sum);
        }
    }
}
