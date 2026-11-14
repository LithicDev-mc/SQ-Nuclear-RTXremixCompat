package com.sqc.nuclearsq.data;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record DataEntry(BlockPos pos, Component name, String type, int state, Component stateText, int dist,
                        List<Line> values, List<Line> controls, List<Slot> slots) {
    public static final int VALUE = 0, SWITCH = 1, STEP = 2;

    public record Line(int kind, String key, Component label, String text, double value, double max, double step) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.kind);
            buf.writeUtf(l.key);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, l.label);
            buf.writeUtf(l.text);
            buf.writeDouble(l.value);
            buf.writeDouble(l.max);
            buf.writeDouble(l.step);
        }, buf -> new Line(buf.readVarInt(), buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        public float frac() {
            return max > 0 ? (float) Math.max(0, Math.min(1, value / max)) : -1f;
        }
    }

    public record Stock(ItemStack item, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stock> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            ItemStack.STREAM_CODEC.encode(buf, s.item);
            buf.writeVarLong(s.count);
        }, buf -> new Stock(ItemStack.STREAM_CODEC.decode(buf), buf.readVarLong()));
    }

    public static final int EMPTY = 0, DRIVE = 1, MODULE = 2;

    public record Slot(int kind, Component name, String text, float frac, List<Stock> items, int more) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.kind);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, s.name);
            buf.writeUtf(s.text);
            buf.writeFloat(s.frac);
            Stock.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, s.items);
            buf.writeVarInt(s.more);
        }, buf -> new Slot(buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(),
                buf.readFloat(), Stock.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DataEntry> STREAM_CODEC = StreamCodec.of((buf, e) -> {
        BlockPos.STREAM_CODEC.encode(buf, e.pos);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.name);
        buf.writeUtf(e.type);
        buf.writeVarInt(e.state);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.stateText);
        buf.writeVarInt(e.dist);
        Line.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.values);
        Line.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.controls);
        Slot.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.slots);
    }, buf -> new DataEntry(BlockPos.STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
            buf.readUtf(), buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(),
            Line.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), Line.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));
    public static final StreamCodec<RegistryFriendlyByteBuf, List<DataEntry>> LIST_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

    public static DataEntry of(DataSource s, BlockPos from) {
        return new DataEntry(s.pos(), s.name(), s.type(), s.state(), s.stateText(), (int) Math.round(Math.sqrt(s.pos().distSqr(from))),
                List.of(), List.of(), List.of());
    }

    public boolean rack() {
        return !slots.isEmpty();
    }

    @javax.annotation.Nullable
    public Line line(String key) {
        for (Line l : values) if (l.key.equals(key)) return l;
        for (Line l : controls) if (l.key.equals(key)) return l;
        return null;
    }

    public static int color(int state) {
        return switch (state) {
            case DataSource.OK -> 0xFF41FF7A;
            case DataSource.WARN -> 0xFFFFB23A;
            case DataSource.ALARM -> 0xFFFF5A4E;
            default -> 0xFF8792A0;
        };
    }
}
