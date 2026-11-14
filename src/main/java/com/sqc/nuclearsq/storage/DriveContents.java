package com.sqc.nuclearsq.storage;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class DriveContents {
    private final Map<ItemKey, Long> items = new LinkedHashMap<>();
    private long total;
    private final Map<Long, Map<ItemKey, Long>> reserved = new LinkedHashMap<>();
    private final Map<ItemKey, Long> reservedTotal = new LinkedHashMap<>();
    private Runnable onChange = () -> {};

    void onChange(Runnable r) {
        onChange = r;
    }

    public Map<ItemKey, Long> items() {
        return Collections.unmodifiableMap(items);
    }

    public long total() {
        return total;
    }

    public int types() {
        return items.size();
    }

    public long count(ItemKey key) {
        return items.getOrDefault(key, 0L);
    }

    public long insert(ItemKey key, long n, StorageValues.Drive limit, boolean simulate) {
        if (n <= 0) return 0;
        boolean known = items.containsKey(key);
        if (!known && items.size() >= limit.types()) return 0;
        long fit = Math.min(n, limit.items() - total);
        if (fit <= 0) return 0;
        if (!simulate) {
            items.merge(key, fit, Long::sum);
            total += fit;
            onChange.run();
        }
        return fit;
    }

    public long free(ItemKey key) {
        return Math.max(0, count(key) - reservedTotal.getOrDefault(key, 0L));
    }

    public long reserved(ItemKey key) {
        return reservedTotal.getOrDefault(key, 0L);
    }

    public long reservedBy(long job, ItemKey key) {
        Map<ItemKey, Long> m = reserved.get(job);
        return m == null ? 0 : m.getOrDefault(key, 0L);
    }

    public long reserve(long job, ItemKey key, long n) {
        long got = Math.min(n, free(key));
        if (got <= 0) return 0;
        reserved.computeIfAbsent(job, k -> new LinkedHashMap<>()).merge(key, got, Long::sum);
        reservedTotal.merge(key, got, Long::sum);
        onChange.run();
        return got;
    }

    public boolean release(long job) {
        Map<ItemKey, Long> m = reserved.remove(job);
        if (m == null) return false;
        m.forEach((k, n) -> reservedTotal.compute(k, (kk, v) -> v == null || v - n <= 0 ? null : v - n));
        onChange.run();
        return true;
    }

    public Set<Long> reservingJobs() {
        return new HashSet<>(reserved.keySet());
    }

    public long extract(ItemKey key, long n, boolean simulate) {
        return take(key, Math.min(n, free(key)), simulate);
    }

    public long extract(ItemKey key, long n, boolean simulate, long job) {
        long own = Math.min(n, reservedBy(job, key));
        long got = own + Math.min(n - own, free(key));
        if (got <= 0) return 0;
        if (simulate) return Math.min(got, count(key));
        got = Math.min(got, count(key));
        long fromOwn = Math.min(own, got);
        if (fromOwn > 0) {
            Map<ItemKey, Long> m = reserved.get(job);
            if (m.get(key) - fromOwn <= 0) m.remove(key);
            else m.merge(key, -fromOwn, Long::sum);
            if (m.isEmpty()) reserved.remove(job);
            reservedTotal.compute(key, (k, v) -> v == null || v - fromOwn <= 0 ? null : v - fromOwn);
        }
        return take(key, got, false);
    }

    public long extractOwn(ItemKey key, long n, boolean simulate, long job) {
        long own = Math.min(n, Math.min(reservedBy(job, key), count(key)));
        if (own <= 0) return 0;
        return simulate ? own : extract(key, own, false, job);
    }

    private long take(ItemKey key, long n, boolean simulate) {
        long have = items.getOrDefault(key, 0L);
        long take = Math.min(have, n);
        if (take <= 0) return 0;
        if (!simulate) {
            if (take == have) items.remove(key);
            else items.put(key, have - take);
            total -= take;
            onChange.run();
        }
        return take;
    }

    public ListTag saveReserved(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        reserved.forEach((job, m) -> m.forEach((k, n) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("job", job);
            t.put("item", k.proto().save(registries));
            t.putLong("n", n);
            list.add(t);
        }));
        return list;
    }

    public void loadReserved(ListTag list, HolderLookup.Provider registries) {
        for (Tag e : list) {
            CompoundTag t = (CompoundTag) e;
            ItemKey k = ItemKey.of(ItemStack.parseOptional(registries, t.getCompound("item")));
            if (k == null) continue;
            long n = Math.min(t.getLong("n"), count(k) - reservedTotal.getOrDefault(k, 0L));
            if (n <= 0) continue;
            reserved.computeIfAbsent(t.getLong("job"), j -> new LinkedHashMap<>()).merge(k, n, Long::sum);
            reservedTotal.merge(k, n, Long::sum);
        }
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        items.forEach((k, n) -> {
            CompoundTag t = new CompoundTag();
            t.put("item", k.proto().save(registries));
            t.putLong("n", n);
            list.add(t);
        });
        return list;
    }

    public static DriveContents load(ListTag list, HolderLookup.Provider registries) {
        DriveContents c = new DriveContents();
        for (Tag e : list) {
            CompoundTag t = (CompoundTag) e;
            ItemStack s = ItemStack.parseOptional(registries, t.getCompound("item"));
            long n = t.getLong("n");
            ItemKey k = ItemKey.of(s);
            if (k == null || n <= 0) continue;
            c.items.merge(k, n, Long::sum);
            c.total += n;
        }
        return c;
    }
}
