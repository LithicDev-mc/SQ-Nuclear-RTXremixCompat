package com.sqc.nuclearsq.gamedata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.particle.ParticleClasses;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class GameData {
    private GameData() {}

    public static final String COOLING = "cooling_values.json", PARTICLES = "particle_classes.json", MULTIBLOCKS = "multiblocks";

    private static volatile CoolingValues cooling;
    @Nullable
    private static volatile CoolingValues builtInCooling;
    private static final List<String> ERRORS = new CopyOnWriteArrayList<>();
    private static volatile GameDataPayload diff = GameDataPayload.EMPTY;

    public static CoolingValues cooling() {
        CoolingValues c = cooling;
        if (c == null) cooling = c = builtInCooling();
        return c;
    }

    public static List<String> errors() {
        return List.copyOf(ERRORS);
    }

    record Raw(@Nullable JsonObject cooling, @Nullable JsonObject particles, Map<String, JsonObject> multiblocks, List<String> errors) {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    static final class Listener extends SimplePreparableReloadListener<Raw> {
        @Override
        protected Raw prepare(ResourceManager rm, ProfilerFiller profiler) {
            List<String> errors = new ArrayList<>();
            JsonObject c = read(rm, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, COOLING), errors);
            JsonObject p = read(rm, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, PARTICLES), errors);
            Map<String, JsonObject> mb = new TreeMap<>();
            for (var e : rm.listResources(MULTIBLOCKS, rl -> rl.getNamespace().equals(NuclearSQ.MODID) && rl.getPath().endsWith(".json")).keySet()) {
                String id = e.getPath().substring(MULTIBLOCKS.length() + 1, e.getPath().length() - 5);
                JsonObject o = read(rm, e, errors);
                if (o != null) mb.put(id, o);
            }
            return new Raw(c, p, mb, errors);
        }

        @Override
        protected void apply(Raw raw, ResourceManager rm, ProfilerFiller profiler) {
            ERRORS.clear();
            ERRORS.addAll(raw.errors());
            List<String> errors = new ArrayList<>();
            applyAll(raw.cooling(), raw.particles(), raw.multiblocks(), errors);
            ERRORS.addAll(errors);
            for (String e : ERRORS) NuclearSQ.LOGGER.error("NuclearSQ Datenpaket: {}", e);
            diff = diffOf(raw);
        }
    }

    @Nullable
    private static JsonObject read(ResourceManager rm, ResourceLocation rl, List<String> errors) {
        var res = rm.getResource(rl);
        if (res.isEmpty()) {
            errors.add(file(rl.getPath()) + ": fehlt");
            return null;
        }
        try (Reader r = res.get().openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            errors.add(file(rl.getPath()) + " (" + res.get().sourcePackId() + "): kein gueltiges JSON: " + e.getMessage());
            return null;
        }
    }

    static String file(String path) {
        return "data/" + NuclearSQ.MODID + "/" + path;
    }

    static void applyAll(@Nullable JsonObject coolingJson, @Nullable JsonObject particlesJson, Map<String, JsonObject> multiblocks,
                         List<String> errors) {
        if (coolingJson != null) applyCooling(coolingJson, errors);
        if (particlesJson != null) {
            try {
                ParticleClasses.set(ParticleClasses.parse(particlesJson));
            } catch (RuntimeException e) {
                errors.add(file(PARTICLES) + ": " + e.getMessage());
            }
        }
        for (MultiblockStructure s : MultiblockStructure.ALL.values()) s.reset();
        for (var e : multiblocks.entrySet()) {
            String name = file(MULTIBLOCKS + "/" + e.getKey() + ".json");
            MultiblockStructure s = MultiblockStructure.ALL.get(e.getKey());
            if (s == null) {
                errors.add(name + ": kein Blockmuster mit dieser ID");
                continue;
            }
            var data = MultiblockStructure.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, e.getValue());
            if (data.error().isPresent()) {
                errors.add(name + ": " + data.error().get().message());
                continue;
            }
            for (String err : s.override(data.getOrThrow(), true)) errors.add(name + ": " + err);
        }
    }

    public static boolean applyCooling(JsonObject json, List<String> errors) {
        List<String> local = new ArrayList<>();
        CoolingValues v = CoolingValues.parse(json, local);
        if (v != null) CoolingValues.requireAll(v, local);
        if (!local.isEmpty()) {
            for (String e : local) errors.add(file(COOLING) + ": " + e);
            return false;
        }
        cooling = v;
        for (BigMachine m : BigMachine.values()) m.apply(v);
        return true;
    }

    public static void resetToBuiltIn() {
        CoolingValues v = builtInCooling();
        cooling = v;
        for (BigMachine m : BigMachine.values()) m.apply(v);
        ParticleClasses.set(ParticleClasses.builtIn());
        for (MultiblockStructure s : MultiblockStructure.ALL.values()) s.reset();
    }

    public static CoolingValues builtInCooling() {
        CoolingValues b = builtInCooling;
        if (b != null) return b;
        List<String> errors = new ArrayList<>();
        b = CoolingValues.parse(builtInJson(COOLING), errors);
        if (b == null) throw new IllegalStateException(file(COOLING) + " fehlerhaft: " + String.join("; ", errors));
        builtInCooling = b;
        return b;
    }

    @Nullable
    static JsonObject builtInJsonOrNull(String path) {
        String res = "/" + file(path);
        try (InputStream in = GameData.class.getResourceAsStream(res)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(res + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    static JsonObject builtInJson(String path) {
        JsonObject o = builtInJsonOrNull(path);
        if (o == null) throw new IllegalStateException(file(path) + " fehlt");
        return o;
    }

    private static GameDataPayload diffOf(Raw raw) {
        Optional<String> c = differs(raw.cooling(), COOLING);
        Optional<String> p = differs(raw.particles(), PARTICLES);
        Map<String, String> mb = new TreeMap<>();
        for (var e : raw.multiblocks().entrySet()) {
            differs(e.getValue(), MULTIBLOCKS + "/" + e.getKey() + ".json").ifPresent(s -> mb.put(e.getKey(), s));
        }
        return new GameDataPayload(c, p, mb);
    }

    private static Optional<String> differs(@Nullable JsonElement loaded, String path) {
        if (loaded == null || loaded.equals(builtInJsonOrNull(path))) return Optional.empty();
        return Optional.of(loaded.toString());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        GameDataPayload p = diff;
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, p));
    }

    public static void applyRemote(GameDataPayload p) {
        resetToBuiltIn();
        List<String> errors = new ArrayList<>();
        Map<String, JsonObject> mb = new TreeMap<>();
        p.multiblocks().forEach((k, v) -> mb.put(k, JsonParser.parseString(v).getAsJsonObject()));
        applyAll(p.cooling().map(s -> JsonParser.parseString(s).getAsJsonObject()).orElse(null),
                p.particles().map(s -> JsonParser.parseString(s).getAsJsonObject()).orElse(null), mb, errors);
        for (String e : errors) NuclearSQ.LOGGER.error("NuclearSQ Datenpaket vom Server: {}", e);
    }
}
