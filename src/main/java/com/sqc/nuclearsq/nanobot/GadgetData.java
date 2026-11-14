package com.sqc.nuclearsq.nanobot;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GadgetData(int shape, int source, Optional<BlockPos> p1, Optional<BlockPos> p2, boolean hollow, int radius, int axis, int height, Paste paste) {
    public static final int CUBOID = 0, HULL = 1, PLANE = 2, LINE = 3, SPHERE = 4, CIRCLE = 5, CYLINDER = 6, SHAPES = 7;
    public static final int INVENTORY = 0, PORT = 1;
    public static final int MAX_RADIUS = 32, MAX_HEIGHT = 32, MAX_OFFSET = 16;

    public static final GadgetData EMPTY = new GadgetData(CUBOID, INVENTORY, Optional.empty(), Optional.empty(), false, 3, 1, 3, Paste.DEFAULT);

    public record Paste(int rot, boolean mirror, boolean replace, int ox, int oy, int oz) {
        public static final Paste DEFAULT = new Paste(0, false, false, 0, 0, 0);

        public static final Codec<Paste> CODEC = RecordCodecBuilder.create(i -> i.group(
                NanoCodecs.clamped(0, 3).optionalFieldOf("rot", 0).forGetter(Paste::rot),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Paste::mirror),
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(Paste::replace),
                NanoCodecs.clamped(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("ox", 0).forGetter(Paste::ox),
                NanoCodecs.clamped(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("oy", 0).forGetter(Paste::oy),
                NanoCodecs.clamped(-MAX_OFFSET, MAX_OFFSET).optionalFieldOf("oz", 0).forGetter(Paste::oz)).apply(i, Paste::new));

        public static final StreamCodec<ByteBuf, Paste> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.rot);
                    buf.writeBoolean(p.mirror);
                    buf.writeBoolean(p.replace);
                    ByteBufCodecs.VAR_INT.encode(buf, p.ox);
                    ByteBufCodecs.VAR_INT.encode(buf, p.oy);
                    ByteBufCodecs.VAR_INT.encode(buf, p.oz);
                },
                buf -> new Paste(ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean(), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));

        private static int clampOffset(int v) {
            return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v));
        }

        public Paste withRot(int r) {
            return new Paste(Math.floorMod(r, 4), mirror, replace, ox, oy, oz);
        }

        public Paste withMirror(boolean m) {
            return new Paste(rot, m, replace, ox, oy, oz);
        }

        public Paste withReplace(boolean r) {
            return new Paste(rot, mirror, r, ox, oy, oz);
        }

        public Paste withOffset(int axis, int v) {
            v = clampOffset(v);
            return switch (axis) {
                case 0 -> new Paste(rot, mirror, replace, v, oy, oz);
                case 1 -> new Paste(rot, mirror, replace, ox, v, oz);
                default -> new Paste(rot, mirror, replace, ox, oy, v);
            };
        }

        public int offset(int axis) {
            return axis == 0 ? ox : axis == 1 ? oy : oz;
        }
    }

    public GadgetData(int shape, int source, Optional<BlockPos> p1, Optional<BlockPos> p2) {
        this(shape, source, p1, p2, false, 3, 1, 3, Paste.DEFAULT);
    }

    public static final Codec<GadgetData> CODEC = RecordCodecBuilder.create(i -> i.group(
            NanoCodecs.clamped(0, SHAPES - 1).optionalFieldOf("shape", CUBOID).forGetter(GadgetData::shape),
            NanoCodecs.clamped(0, 1).optionalFieldOf("source", INVENTORY).forGetter(GadgetData::source),
            BlockPos.CODEC.optionalFieldOf("p1").forGetter(GadgetData::p1),
            BlockPos.CODEC.optionalFieldOf("p2").forGetter(GadgetData::p2),
            Codec.BOOL.optionalFieldOf("hollow", false).forGetter(GadgetData::hollow),
            NanoCodecs.clamped(1, MAX_RADIUS).optionalFieldOf("radius", 3).forGetter(GadgetData::radius),
            NanoCodecs.clamped(0, 2).optionalFieldOf("axis", 1).forGetter(GadgetData::axis),
            NanoCodecs.clamped(1, MAX_HEIGHT).optionalFieldOf("height", 3).forGetter(GadgetData::height),
            Paste.CODEC.optionalFieldOf("paste", Paste.DEFAULT).forGetter(GadgetData::paste)).apply(i, GadgetData::new));

    private static final StreamCodec<ByteBuf, Optional<BlockPos>> OPT_POS = ByteBufCodecs.optional(BlockPos.STREAM_CODEC);

    public static final StreamCodec<ByteBuf, GadgetData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.VAR_INT.encode(buf, d.shape);
                ByteBufCodecs.VAR_INT.encode(buf, d.source);
                OPT_POS.encode(buf, d.p1);
                OPT_POS.encode(buf, d.p2);
                buf.writeBoolean(d.hollow);
                ByteBufCodecs.VAR_INT.encode(buf, d.radius);
                ByteBufCodecs.VAR_INT.encode(buf, d.axis);
                ByteBufCodecs.VAR_INT.encode(buf, d.height);
                Paste.STREAM_CODEC.encode(buf, d.paste);
            },
            buf -> new GadgetData(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), OPT_POS.decode(buf), OPT_POS.decode(buf), buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), Paste.STREAM_CODEC.decode(buf)));

    public static boolean isRound(int shape) {
        return shape >= SPHERE;
    }

    public int needed() {
        return isRound(shape) ? 1 : 2;
    }

    public int needed(NanoTask task) {
        return task == NanoTask.GADGET_PASTE ? 1 : needed();
    }

    public GadgetData withShape(int s) {
        return new GadgetData(Math.floorMod(s, SHAPES), source, p1, p2, hollow, radius, axis, height, paste);
    }

    public GadgetData withSource(int s) {
        return new GadgetData(shape, s == PORT ? PORT : INVENTORY, p1, p2, hollow, radius, axis, height, paste);
    }

    public GadgetData withPoints(Optional<BlockPos> a, Optional<BlockPos> b) {
        return new GadgetData(shape, source, a, b, hollow, radius, axis, height, paste);
    }

    public GadgetData withHollow(boolean h) {
        return new GadgetData(shape, source, p1, p2, h, radius, axis, height, paste);
    }

    public GadgetData withRadius(int r) {
        return new GadgetData(shape, source, p1, p2, hollow, Math.max(1, Math.min(MAX_RADIUS, r)), axis, height, paste);
    }

    public GadgetData withAxis(int a) {
        return new GadgetData(shape, source, p1, p2, hollow, radius, Math.floorMod(a, 3), height, paste);
    }

    public GadgetData withHeight(int h) {
        return new GadgetData(shape, source, p1, p2, hollow, radius, axis, Math.max(1, Math.min(MAX_HEIGHT, h)), paste);
    }

    public GadgetData withPaste(Paste p) {
        return new GadgetData(shape, source, p1, p2, hollow, radius, axis, height, p);
    }

    public int points() {
        return (p1.isPresent() ? 1 : 0) + (p2.isPresent() ? 1 : 0);
    }

    public boolean ready() {
        return points() >= needed();
    }

    public boolean ready(NanoTask task) {
        return points() >= needed(task);
    }
}
