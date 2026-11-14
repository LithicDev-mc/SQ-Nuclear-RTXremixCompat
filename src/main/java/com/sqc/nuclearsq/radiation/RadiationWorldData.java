package com.sqc.nuclearsq.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class RadiationWorldData extends SavedData {
    static final String KEY = "nuclearsq_radiation";
    private static final SavedData.Factory<RadiationWorldData> FACTORY =
            new SavedData.Factory<>(RadiationWorldData::new, RadiationWorldData::load, null);

    public record Core(Vec3 pos, double strength, long start, long end) {
        static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                Vec3.CODEC.fieldOf("pos").forGetter(Core::pos),
                Codec.DOUBLE.fieldOf("strength").forGetter(Core::strength),
                Codec.LONG.fieldOf("start").forGetter(Core::start),
                Codec.LONG.fieldOf("end").forGetter(Core::end)).apply(i, Core::new));

        public double rateAt(long now) {
            return strength * Math.pow(RadiationValues.DOSE_DECAY, Math.max(0, now - start) / 20.0);
        }
    }

    record Emitter(double svH, long until) {}

    final List<Core> cores = new ArrayList<>();
    final ChunkIndexedPositions fallout = new ChunkIndexedPositions();
    final Contamination.Zones zones = new Contamination.Zones(this);
    final Long2ObjectOpenHashMap<Emitter> emitters = new Long2ObjectOpenHashMap<>();
    final Set<UUID> dosed = new java.util.HashSet<>();

    public static RadiationWorldData get(ServerLevel lv) {
        return lv.getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    boolean hasSources() {
        return !cores.isEmpty() || !fallout.isEmpty() || !emitters.isEmpty() || zones.size() > 0;
    }

    public List<Core> cores() {
        return java.util.Collections.unmodifiableList(cores);
    }

    public Contamination.Zones zones() {
        return zones;
    }

    public void addCore(Core c) {
        cores.add(c);
        setDirty();
    }

    public void removeCoresAt(Vec3 pos) {
        if (cores.removeIf(c -> c.pos().distanceToSqr(pos) < 1e-6)) setDirty();
    }

    void pruneCores(long now) {
        if (cores.removeIf(c -> now >= c.end())) setDirty();
    }

    public boolean addFallout(long pos) {
        if (!fallout.add(pos)) return false;
        setDirty();
        return true;
    }

    public boolean removeFallout(long pos) {
        if (!fallout.remove(pos)) return false;
        setDirty();
        return true;
    }

    public boolean hasFallout(long pos) {
        return fallout.contains(pos);
    }

    public int falloutCount() {
        return fallout.size();
    }

    public static RadiationWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        RadiationWorldData d = new RadiationWorldData();
        Core.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("cores")).ifSuccess(d.cores::addAll);
        for (long l : tag.getLongArray("fallout")) d.fallout.add(l);
        long[] pos = tag.getLongArray("contamination");
        int[] lv = tag.getIntArray("contaminationLevel");
        for (int i = 0; i < pos.length && i < lv.length; i++) d.zones.put(pos[i], Float.intBitsToFloat(lv[i]));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Core.CODEC.listOf().encodeStart(NbtOps.INSTANCE, cores).ifSuccess(t -> tag.put("cores", t));
        tag.putLongArray("fallout", fallout.toLongArray());
        long[] pos = zones.positions();
        int[] lv = new int[pos.length];
        for (int i = 0; i < pos.length; i++) lv[i] = Float.floatToIntBits(zones.level.get(pos[i]));
        tag.putLongArray("contamination", pos);
        tag.putIntArray("contaminationLevel", lv);
        return tag;
    }
}
