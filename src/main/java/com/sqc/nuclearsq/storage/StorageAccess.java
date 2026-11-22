package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class StorageAccess {
    private record Drive(BigMachineBlockEntity rack, int slot, DriveContents contents, StorageValues.Drive limits, int priority) {}

    private final MinecraftServer server;
    private final List<Drive> drives = new ArrayList<>();
    private final int racks, online;
    private final boolean complete;
    private final java.util.Set<Long> chunks;

    StorageAccess(Level level, List<BigMachineBlockEntity> racks) {
        this(level, racks, true, java.util.Set.of());
    }

    StorageAccess(Level level, List<BigMachineBlockEntity> racks, boolean complete, java.util.Set<Long> chunks) {
        this.complete = complete;
        this.chunks = chunks;
        this.server = level.getServer();
        this.racks = racks.size();
        int on = 0;
        java.util.Set<DriveContents> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (server != null) {
            for (BigMachineBlockEntity be : racks) {
                ServerRack r = be.rack();
                if (r == null || !r.online()) continue;
                on++;
                for (int i = 0; i < r.slots(); i++) {
                    DriveContents c = r.contents(server, i);
                    if (c != null && seen.add(c)) drives.add(new Drive(be, i, c, r.limits(i), r.priority()));
                }
            }
        }
        online = on;
    }

    public boolean complete() {
        return complete;
    }

    public java.util.Set<Long> chunks() {
        return chunks;
    }

    public int racks() {
        return racks;
    }

    public int onlineRacks() {
        return online;
    }

    public int drives() {
        return drives.size();
    }

    public Map<ItemKey, Long> list() {
        Map<ItemKey, Long> out = new LinkedHashMap<>();
        for (Drive d : drives) d.contents.items().forEach((k, n) -> out.merge(k, n, Long::sum));
        return out;
    }

    public long count(ItemKey key) {
        long n = 0;
        for (Drive d : drives) n += d.contents.count(key);
        return n;
    }

    public long available(ItemKey key) {
        long n = 0;
        for (Drive d : drives) n += d.contents.free(key);
        return n;
    }

    public long reserved(ItemKey key) {
        long n = 0;
        for (Drive d : drives) n += d.contents.reserved(key);
        return n;
    }

    public long countFor(ItemKey key, long job) {
        long n = 0;
        for (Drive d : drives) n += d.contents.reservedBy(job, key) + d.contents.free(key);
        return n;
    }

    public Map<ItemKey, Long> listReserved() {
        Map<ItemKey, Long> out = new LinkedHashMap<>();
        for (Drive d : drives) {
            for (ItemKey k : d.contents.items().keySet()) {
                long r = d.contents.reserved(k);
                if (r > 0) out.merge(k, r, Long::sum);
            }
        }
        return out;
    }

    public long reserve(long job, ItemKey key, long n) {
        if (key == null || n <= 0) return 0;
        long left = n;
        for (int i = drives.size() - 1; i >= 0 && left > 0; i--) left -= drives.get(i).contents.reserve(job, key, left);
        return n - left;
    }

    public long extract(ItemKey key, long n, boolean simulate, long job) {
        if (key == null || n <= 0) return 0;
        long left = n;
        for (int i = drives.size() - 1; i >= 0 && left > 0; i--) {
            Drive d = drives.get(i);
            long got = d.contents.extractOwn(key, left, simulate, job);
            if (got > 0 && !simulate) changed(d);
            left -= got;
        }
        if (left > 0) left -= extract(key, left, simulate);
        return n - left;
    }

    public long used() {
        long n = 0;
        for (Drive d : drives) n += d.contents.total();
        return n;
    }

    public long capacity() {
        long n = 0;
        for (Drive d : drives) n += d.limits.items();
        return n;
    }

    public long insert(ItemKey key, long n, boolean simulate) {
        if (key == null || n <= 0) return 0;
        long left = n;
        for (int from = 0, to; from < drives.size() && left > 0; from = to) {
            int prio = drives.get(from).priority;
            for (to = from; to < drives.size() && drives.get(to).priority == prio; to++) {}
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int i = from; i < to && left > 0; i++) {
                    Drive d = drives.get(i);
                    if ((d.contents.count(key) > 0) != (pass == 0)) continue;
                    long got = d.contents.insert(key, left, d.limits, simulate);
                    if (got > 0 && !simulate) changed(d);
                    left -= got;
                }
            }
        }
        return n - left;
    }

    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        long got = insert(ItemKey.of(stack), stack.getCount(), simulate);
        return stack.copyWithCount(stack.getCount() - (int) got);
    }

    public long extract(ItemKey key, long n, boolean simulate) {
        if (key == null || n <= 0) return 0;
        long left = n;
        for (int i = drives.size() - 1; i >= 0 && left > 0; i--) {
            Drive d = drives.get(i);
            long got = d.contents.extract(key, left, simulate);
            if (got > 0 && !simulate) changed(d);
            left -= got;
        }
        return n - left;
    }

    public ItemStack extractStack(ItemKey key, int n, boolean simulate) {
        int want = Math.min(n, key.maxStack());
        long got = extract(key, want, simulate);
        return got <= 0 ? ItemStack.EMPTY : key.stack((int) got);
    }

    private void changed(Drive d) {
        ServerRack r = d.rack.rack();
        if (r == null) return;
        r.touch(d.slot);
        r.updateFill(server, d.slot);
        d.rack.setChanged();
    }
}
