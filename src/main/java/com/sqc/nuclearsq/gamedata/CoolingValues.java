package com.sqc.nuclearsq.gamedata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

public record CoolingValues(Map<String, Machine> machines, Shaft shaft, Map<String, Integer> parts) {

    public int part(String key, int fallback) {
        Integer v = parts.get(key);
        return v == null ? fallback : v;
    }

    public record PortValues(int rate, int tank, int slots) {
        public static final Codec<PortValues> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("rate", 0).forGetter(PortValues::rate),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("tank", 0).forGetter(PortValues::tank),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("slots", 0).forGetter(PortValues::slots)
        ).apply(i, PortValues::new));
    }

    public record EnergyValues(int capacity, int use) {
        public static final Codec<EnergyValues> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("capacity").forGetter(EnergyValues::capacity),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("use", 0).forGetter(EnergyValues::use)
        ).apply(i, EnergyValues::new));
    }

    public record Machine(Map<String, PortValues> ports, Optional<EnergyValues> energy, Map<String, Integer> extra) {}

    public record Shaft(int createMaxRpm, int createRpmStep, int createSuPerFe) {
        public static final Codec<Shaft> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 256).fieldOf("create_max_rpm").forGetter(Shaft::createMaxRpm),
                Codec.intRange(1, 256).fieldOf("create_rpm_step").forGetter(Shaft::createRpmStep),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("create_su_per_fe").forGetter(Shaft::createSuPerFe)
        ).apply(i, Shaft::new));
    }

    @Nullable
    public Machine machine(String id) { return machines.get(id); }

    @Nullable
    public static CoolingValues parse(JsonObject root, List<String> errors) {
        int before = errors.size();
        Map<String, Machine> machines = new LinkedHashMap<>();
        JsonObject ms = object(root, "machines", "machines", errors);
        if (ms != null) {
            for (var e : ms.entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                String path = "machines." + e.getKey();
                if (!e.getValue().isJsonObject()) { errors.add(path + ": kein Objekt"); continue; }
                Machine m = machine(e.getValue().getAsJsonObject(), path, errors);
                if (m != null) machines.put(e.getKey(), m);
            }
        }
        Shaft shaft = null;
        JsonObject so = object(root, "shaft", "shaft", errors);
        if (so != null) shaft = decode(Shaft.CODEC, so, "shaft", errors);
        if (errors.size() > before) return null;
        Map<String, Integer> parts = new LinkedHashMap<>();
        JsonElement pe = root.get("parts");
        if (pe != null && pe.isJsonObject()) {
            for (var e : pe.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) parts.put(e.getKey(), v.getAsInt());
                else errors.add("parts." + e.getKey() + ": keine Zahl");
            }
        }
        if (errors.size() > before) return null;
        return new CoolingValues(Map.copyOf(machines), shaft, Map.copyOf(parts));
    }

    private static Machine machine(JsonObject o, String path, List<String> errors) {
        Map<String, PortValues> ports = new LinkedHashMap<>();
        JsonObject po = object(o, "ports", path + ".ports", errors);
        if (po != null) {
            for (var p : po.entrySet()) {
                if (p.getKey().startsWith("_")) continue;
                PortValues v = decode(PortValues.CODEC, p.getValue(), path + ".ports." + p.getKey(), errors);
                if (v != null) ports.put(p.getKey(), v);
            }
        }
        Optional<EnergyValues> energy = Optional.empty();
        JsonElement en = o.get("energy");
        if (en != null && !en.isJsonNull()) energy = Optional.ofNullable(decode(EnergyValues.CODEC, en, path + ".energy", errors));
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (var e : o.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals("ports") || k.equals("energy")) continue;
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) extra.put(k, v.getAsInt());
            else errors.add(path + "." + k + ": keine Zahl");
        }
        return new Machine(Map.copyOf(ports), energy, Map.copyOf(extra));
    }

    @Nullable
    private static JsonObject object(JsonObject parent, String key, String path, List<String> errors) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonObject()) {
            errors.add(path + ": fehlt oder ist kein Objekt");
            return null;
        }
        return e.getAsJsonObject();
    }

    @Nullable
    private static <T> T decode(Codec<T> codec, JsonElement e, String path, List<String> errors) {
        var r = codec.parse(JsonOps.INSTANCE, e);
        if (r.error().isPresent()) {
            errors.add(path + ": " + r.error().get().message());
            return null;
        }
        return r.getOrThrow();
    }

    public static void requireAll(CoolingValues v, List<String> errors) {
        for (var m : com.sqc.nuclearsq.machine.big.BigMachine.values()) {
            Machine vals = v.machines.get(m.id);
            if (vals == null) { errors.add("machines." + m.id + ": fehlt"); continue; }
            for (String port : m.portNames()) {
                if (!vals.ports.containsKey(port)) errors.add("machines." + m.id + ".ports." + port + ": fehlt");
            }
        }
    }
}
