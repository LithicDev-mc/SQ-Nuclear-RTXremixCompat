package com.sqc.nuclearsq.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ParticleClasses {
    public enum Upgrade {
        SPEED("speed"), ENERGY("energy"), EFFICIENCY("efficiency"), OUTPUT("output");

        public final String key;

        Upgrade(String key) { this.key = key; }

        public int slot() { return OUTPUTS + ordinal(); }
    }

    public static final int OUTPUTS = 9, CARD = OUTPUTS + Upgrade.values().length, SLOTS = CARD + 1;
    public static final List<String> CARDS = List.of("medium", "heavy", "rare", "exotic");
    public static final int ALL = 4;

    public record Entry(String item, int weight, int min, int max) {}

    public record ItemClass(String id, int weight, List<Entry> entries) {}

    public record Table(int max, int[] timePct, int[] energyPct, int[] boostPct, int[] bonusPct) {}

    public record Roll(int cls, ItemStack stack) {}

    private static volatile ParticleClasses instance;

    public final List<ItemClass> classes;
    private final Table[] tables = new Table[Upgrade.values().length];
    private final int[] cardEnergyPct;

    private ParticleClasses(JsonObject root) {
        List<ItemClass> list = new ArrayList<>();
        for (JsonElement ce : need(root.getAsJsonArray("classes"), "classes")) {
            JsonObject c = ce.getAsJsonObject();
            String cid = need(c.get("id"), "classes[].id").getAsString();
            List<Entry> entries = new ArrayList<>();
            for (JsonElement ee : need(c.getAsJsonArray("items"), cid + ".items")) {
                JsonObject e = ee.getAsJsonObject();
                String item = need(e.get("item"), cid + ".items[].item").getAsString();
                if (ResourceLocation.tryParse(item.startsWith("#") ? item.substring(1) : item) == null) {
                    throw new IllegalArgumentException(cid + ": ungueltige ID " + item);
                }
                JsonArray n = need(e.getAsJsonArray("count"), cid + "." + item + ".count");
                Entry entry = new Entry(item, need(e.get("weight"), cid + "." + item + ".weight").getAsInt(), n.get(0).getAsInt(), n.get(1).getAsInt());
                if (entry.weight() < 0 || entry.min() < 1 || entry.max() < entry.min()) {
                    throw new IllegalArgumentException(cid + "." + item + ": weight >= 0 und 1 <= count[0] <= count[1] noetig");
                }
                entries.add(entry);
            }
            list.add(new ItemClass(cid, need(c.get("weight"), cid + ".weight").getAsInt(), List.copyOf(entries)));
        }
        if (list.size() != CARDS.size() + 1) throw new IllegalArgumentException("classes: " + (CARDS.size() + 1) + " Klassen noetig, da sind " + list.size());
        classes = Collections.unmodifiableList(list);
        JsonObject up = need(root.getAsJsonObject("upgrades"), "upgrades");
        for (Upgrade u : Upgrade.values()) {
            JsonObject t = need(up.getAsJsonObject(u.key), "upgrades." + u.key);
            int max = need(t.get("max"), "upgrades." + u.key + ".max").getAsInt();
            tables[u.ordinal()] = new Table(max, ints(t, "time_pct", max), ints(t, "energy_pct", max), ints(t, "class_boost_pct", max),
                    ints(t, "bonus_pct", max));
        }
        cardEnergyPct = ints(need(root.getAsJsonObject("cards"), "cards"), "energy_pct", ALL);
    }

    private static <T> T need(@Nullable T v, String path) {
        if (v == null) throw new IllegalArgumentException(path + " fehlt");
        return v;
    }

    public static ParticleClasses parse(JsonObject root) {
        return new ParticleClasses(root);
    }

    public static void set(ParticleClasses pc) {
        instance = pc;
    }

    public static ParticleClasses builtIn() {
        String res = "/data/nuclearsq/particle_classes.json";
        try (InputStream in = ParticleClasses.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException(res + " fehlt");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException(res + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static int[] ints(JsonObject t, String key, int max) {
        int[] out = new int[max + 1];
        JsonArray a = t.getAsJsonArray(key);
        for (int i = 0; i <= max; i++) {
            out[i] = a == null ? (key.endsWith("boost_pct") || key.equals("bonus_pct") ? 0 : 100) : a.get(Math.min(i, a.size() - 1)).getAsInt();
        }
        return out;
    }

    public static ParticleClasses get() {
        ParticleClasses pc = instance;
        if (pc == null) instance = pc = builtIn();
        return pc;
    }

    public Table table(Upgrade u) { return tables[u.ordinal()]; }

    public int max(Upgrade u) { return table(u).max(); }

    private static int at(int[] col, int n) { return col[Math.max(0, Math.min(n, col.length - 1))]; }

    public int time(int baseTicks, int[] levels) {
        return Math.max(1, Math.round(baseTicks * at(table(Upgrade.SPEED).timePct(), levels[Upgrade.SPEED.ordinal()]) / 100f));
    }

    public int energy(int baseFe, int[] levels) {
        return energy(baseFe, levels, 0);
    }

    public int cardEnergyPct(int tier) {
        return cardEnergyPct[Math.max(0, Math.min(tier, ALL))];
    }

    public int energy(int baseFe, int[] levels, int tier) {
        double f = baseFe * cardEnergyPct(tier) / 100.0;
        for (Upgrade u : Upgrade.values()) f *= at(table(u).energyPct(), levels[u.ordinal()]) / 100.0;
        return Math.max(1, (int) Math.round(f));
    }

    public int bonus(int[] levels) {
        return at(table(Upgrade.OUTPUT).bonusPct(), levels[Upgrade.OUTPUT.ordinal()]);
    }

    public double[] weights(int[] levels) {
        return weights(levels, ALL);
    }

    public double[] weights(int[] levels, int tier) {
        double boost = at(table(Upgrade.EFFICIENCY).boostPct(), levels[Upgrade.EFFICIENCY.ordinal()]) / 100.0;
        double[] w = new double[classes.size()];
        for (int k = 0; k < w.length; k++) {
            boolean any = false;
            for (Entry e : classes.get(k).entries()) any |= resolve(e.item()) != null;
            w[k] = any && k <= tier ? classes.get(k).weight() * Math.pow(1 + boost, k) : 0;
        }
        return w;
    }

    public int[] chances(int[] levels) {
        return chances(levels, ALL);
    }

    public int[] chances(int[] levels, int tier) {
        double[] w = weights(levels, tier);
        double sum = 0;
        for (double x : w) sum += x;
        int[] out = new int[w.length];
        for (int k = 0; k < w.length; k++) out[k] = sum <= 0 ? 0 : (int) Math.round(1000 * w[k] / sum);
        return out;
    }

    @Nullable
    public Roll roll(RandomSource rnd, int[] levels) {
        return roll(rnd, levels, ALL);
    }

    @Nullable
    public Roll roll(RandomSource rnd, int[] levels, int tier) {
        double[] w = weights(levels, tier);
        int cls = pick(rnd, w);
        if (cls < 0) return null;
        List<Entry> entries = classes.get(cls).entries();
        double[] ew = new double[entries.size()];
        for (int i = 0; i < ew.length; i++) ew[i] = resolve(entries.get(i).item()) == null ? 0 : entries.get(i).weight();
        int ei = pick(rnd, ew);
        if (ei < 0) return null;
        Entry e = entries.get(ei);
        Item item = resolve(e.item());
        int count = e.min() + (e.max() > e.min() ? rnd.nextInt(e.max() - e.min() + 1) : 0);
        int bonus = bonus(levels);
        int loads = 1 + bonus / 100 + (rnd.nextInt(100) < bonus % 100 ? 1 : 0);
        ItemStack stack = new ItemStack(item, Math.min(count * loads, new ItemStack(item).getMaxStackSize()));
        return new Roll(cls, stack);
    }

    private static int pick(RandomSource rnd, double[] w) {
        double sum = 0;
        for (double x : w) sum += x;
        if (sum <= 0) return -1;
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0 && w[i] > 0) return i;
        }
        for (int i = w.length - 1; i >= 0; i--) if (w[i] > 0) return i;
        return -1;
    }

    @Nullable
    public static Item resolve(String spec) {
        if (spec.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(spec.substring(1));
            if (rl == null) return null;
            var tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl));
            if (tag.isEmpty()) return null;
            return tag.get().stream().map(h -> h.value())
                    .min(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString())).orElse(null);
        }
        ResourceLocation rl = ResourceLocation.tryParse(spec);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR ? null : it;
    }
}
