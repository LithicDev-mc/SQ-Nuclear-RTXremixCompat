package com.sqc.nuclearsq.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class DriveStorage extends SavedData {
    private static final String NAME = "nuclearsq_drives";
    private final Map<UUID, DriveContents> drives = new HashMap<>();
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<String, CompoundTag> parked = new HashMap<>();
    private final Map<String, ComponentAssembler> live = new HashMap<>();

    public record Seat(ResourceKey<Level> dim, BlockPos pos, int slot) {}

    public static DriveStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DriveStorage::new, DriveStorage::load, null), NAME);
    }

    public DriveContents contents(UUID id) {
        return drives.computeIfAbsent(id, k -> watch(new DriveContents()));
    }

    @Nullable
    public Seat seat(UUID id) {
        return seats.get(id);
    }

    public void seat(UUID id, Seat seat) {
        if (!seat.equals(seats.put(id, seat))) setDirty();
    }

    public void release(UUID id, Seat seat) {
        if (seats.remove(id, seat)) setDirty();
    }

    public static String assemblerKey(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    @Nullable
    CompoundTag attach(String key, ComponentAssembler a) {
        CompoundTag t = parked.remove(key);
        live.put(key, a);
        setDirty();
        return t;
    }

    void detach(String key, ComponentAssembler a, HolderLookup.Provider registries) {
        if (live.get(key) != a) return;
        live.remove(key);
        parked.put(key, a.saveState(registries));
        setDirty();
    }

    void forget(String key, ComponentAssembler a) {
        if (live.get(key) == a) live.remove(key);
        parked.remove(key);
        setDirty();
    }

    public void releaseJob(long job) {
        for (DriveContents c : drives.values()) c.release(job);
    }

    public boolean hasAssemblerState(String key) {
        return parked.containsKey(key) || live.containsKey(key);
    }

    private boolean knownJob(long job) {
        for (ComponentAssembler a : live.values()) if (a.hasJob(job)) return true;
        for (CompoundTag t : parked.values()) if (ComponentAssembler.stateHasJob(t, job)) return true;
        return false;
    }

    private DriveContents watch(DriveContents c) {
        c.onChange(this::setDirty);
        return c;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        drives.forEach((id, c) -> {
            if (c.types() == 0) return;
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.put("items", c.save(registries));
            t.put("reserved", c.saveReserved(registries));
            list.add(t);
        });
        tag.put("drives", list);
        ListTag seatList = new ListTag();
        seats.forEach((id, st) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("dim", st.dim().location().toString());
            t.putLong("pos", st.pos().asLong());
            t.putInt("slot", st.slot());
            seatList.add(t);
        });
        tag.put("seats", seatList);
        ListTag asm = new ListTag();
        Map<String, CompoundTag> all = new HashMap<>(parked);
        live.forEach((k, a) -> all.put(k, a.saveState(registries)));
        all.forEach((k, st) -> {
            CompoundTag t = new CompoundTag();
            t.putString("key", k);
            t.put("state", st);
            asm.add(t);
        });
        tag.put("assemblers", asm);
        return tag;
    }

    public static DriveStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        DriveStorage s = new DriveStorage();
        for (Tag e : tag.getList("drives", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            DriveContents c = DriveContents.load(t.getList("items", Tag.TAG_COMPOUND), registries);
            c.loadReserved(t.getList("reserved", Tag.TAG_COMPOUND), registries);
            s.drives.put(t.getUUID("id"), s.watch(c));
        }
        for (Tag e : tag.getList("assemblers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            s.parked.put(t.getString("key"), t.getCompound("state"));
        }
        for (DriveContents c : s.drives.values()) {
            for (long job : c.reservingJobs()) if (!s.knownJob(job)) c.release(job);
        }
        for (Tag e : tag.getList("seats", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null) continue;
            s.seats.put(t.getUUID("id"), new Seat(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos")), t.getInt("slot")));
        }
        return s;
    }
}
