package com.sqc.nuclearsq.radiation;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

public final class Shielding {
    private Shielding() {}

    private static final double STEP = 0.25;
    private static final double OPAQUE = 1e-7;
    public static final int CACHE_TICKS = 40;
    private static final int CACHE_MAX = 20000;

    private record Key(ServerLevel level, long from, long to) {}
    private record Entry(double value, long until) {}
    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static final Map<BlockState, Double> MATERIAL = new IdentityHashMap<>();

    public static double factor(ServerLevel lv, BlockPos source, Vec3 target) {
        BlockPos tb = BlockPos.containing(target);
        long now = lv.getGameTime();
        Key k = new Key(lv, source.asLong(), tb.asLong());
        Entry e = CACHE.get(k);
        if (e != null && e.until >= now) return e.value;
        if (CACHE.size() > CACHE_MAX) CACHE.clear();
        double v = trace(lv, Vec3.atCenterOf(source), target, source, tb);
        CACHE.put(k, new Entry(v, now + CACHE_TICKS));
        return v;
    }

    public static double factor(ServerLevel lv, Vec3 source, Vec3 target) {
        return factor(lv, BlockPos.containing(source), target);
    }

    public static double trace(ServerLevel lv, Vec3 from, Vec3 to, BlockPos skipA, BlockPos skipB) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return 1.0;
        int n = (int) Math.ceil(len / STEP);
        double step = len / n;
        double logT = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long lastPos = Long.MIN_VALUE;
        double lastLog = 0;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            p.set(Math.floor(from.x + d.x * t), Math.floor(from.y + d.y * t), Math.floor(from.z + d.z * t));
            long l = p.asLong();
            if (l != lastPos) {
                lastPos = l;
                if (l == skipA.asLong() || l == skipB.asLong() || !lv.isLoaded(p)) lastLog = 0;
                else lastLog = Math.log(transmission(lv.getBlockState(p)));
            }
            logT += lastLog * step;
            if (logT < Math.log(OPAQUE)) return 0;
        }
        return Math.exp(logT);
    }

    public static double transmission(BlockState st) {
        Double v = MATERIAL.get(st);
        if (v == null) {
            v = classify(st);
            MATERIAL.put(st, v);
        }
        return v;
    }

    private static final List<Map.Entry<TagKey<Block>, Double>> TAGS = List.of(
            Map.entry(tag("lead_glass"), RadiationValues.SHIELD_LEAD_GLASS),
            Map.entry(tag("lead"), RadiationValues.SHIELD_LEAD),
            Map.entry(tag("heavy_concrete"), RadiationValues.SHIELD_HEAVY_CONCRETE),
            Map.entry(tag("hull"), RadiationValues.SHIELD_HULL),
            Map.entry(tag("concrete"), RadiationValues.SHIELD_CONCRETE),
            Map.entry(tag("steel"), RadiationValues.SHIELD_STEEL),
            Map.entry(tag("stone"), RadiationValues.SHIELD_STONE),
            Map.entry(tag("soil"), RadiationValues.SHIELD_SOIL),
            Map.entry(tag("wood"), RadiationValues.SHIELD_WOOD),
            Map.entry(tag("light"), RadiationValues.SHIELD_LIGHT));

    private static final List<String> NO_GUESS = List.of("glowstone", "redstone", "stonecutter", "grindstone", "cauldron",
            "button", "pressure_plate", "torch", "lamp", "lantern");

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "shielding/" + name));
    }

    private static double classify(BlockState st) {
        boolean water = st.getFluidState().is(FluidTags.WATER);
        if (st.isAir()) return 1.0;
        boolean empty = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        if (empty) return water ? RadiationValues.SHIELD_WATER : 1.0;
        double m = Double.NaN;
        for (Map.Entry<TagKey<Block>, Double> e : TAGS) {
            if (st.is(e.getKey())) {
                m = e.getValue();
                break;
            }
        }
        if (Double.isNaN(m)) m = guess(st);
        if (Double.isNaN(m)) m = st.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? RadiationValues.SHIELD_OTHER_SOLID : RadiationValues.SHIELD_PARTIAL;
        return water ? Math.min(m, RadiationValues.SHIELD_WATER) : m;
    }

    private static double guess(BlockState st) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        String ns = key.getNamespace();
        if (ns.equals("minecraft") || ns.equals(NuclearSQ.MODID)) return Double.NaN;
        String id = key.getPath();
        for (String w : NO_GUESS) if (id.contains(w)) return Double.NaN;
        if (id.contains("lead") && id.contains("glass")) return RadiationValues.SHIELD_LEAD_GLASS;
        if (id.contains("lead")) return RadiationValues.SHIELD_LEAD;
        if (id.contains("concrete") && !id.contains("powder") || id.contains("obsidian")) return RadiationValues.SHIELD_CONCRETE;
        if (id.contains("steel") || id.contains("reinforced") || id.contains("anvil")) return RadiationValues.SHIELD_STEEL;
        if (id.contains("stone") || id.contains("brick") || id.contains("deepslate") || id.contains("basalt") || id.endsWith("_ore")) {
            return RadiationValues.SHIELD_STONE;
        }
        if (id.contains("gravel") || id.contains("clay") || id.contains("mud") || id.contains("snow")) return RadiationValues.SHIELD_SOIL;
        if (id.contains("glass")) return RadiationValues.SHIELD_LIGHT;
        return Double.NaN;
    }

    @EventBusSubscriber(modid = NuclearSQ.MODID)
    static final class TagReload {
        private TagReload() {}

        @SubscribeEvent
        static void onTagsUpdated(TagsUpdatedEvent event) {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) MATERIAL.clear();
        }
    }

    static void clear() {
        CACHE.clear();
    }
}
