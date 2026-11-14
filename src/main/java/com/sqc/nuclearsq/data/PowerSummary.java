package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PowerSummary(int plants, long peak, List<Item> producers, List<Item> consumers, List<Item> storages) {
    public static final PowerSummary EMPTY = new PowerSummary(0, 0, List.of(), List.of(), List.of());
    public static final int TOP = 8;

    public record Item(Component name, int count, long a, long b) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Item> CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Item::name, ByteBufCodecs.VAR_INT, Item::count,
                ByteBufCodecs.VAR_LONG, Item::a, ByteBufCodecs.VAR_LONG, Item::b, Item::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Item>> ITEMS = Item.CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, PowerSummary> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PowerSummary::plants, ByteBufCodecs.VAR_LONG, PowerSummary::peak,
            ITEMS, PowerSummary::producers, ITEMS, PowerSummary::consumers, ITEMS, PowerSummary::storages, PowerSummary::new);

    static final class Group {
        private final Map<String, long[]> sums = new LinkedHashMap<>();
        private final Map<String, Component> names = new LinkedHashMap<>();

        void add(Component name, long a, long b) {
            String key = name.toString();
            long[] s = sums.computeIfAbsent(key, k -> new long[3]);
            names.putIfAbsent(key, name);
            s[0]++;
            s[1] += a;
            s[2] += b;
        }

        List<Item> top() {
            List<Item> out = new ArrayList<>();
            for (var e : sums.entrySet()) out.add(new Item(names.get(e.getKey()), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2]));
            out.sort(Comparator.comparingLong(Item::a).reversed());
            return out.size() > TOP ? new ArrayList<>(out.subList(0, TOP)) : out;
        }
    }
}
