package com.sqc.nuclearsq.robotics.drone;

import java.nio.ByteBuffer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SurveyMap(int x0, int z0, int w, int h, byte[] cells) {
    public static final int CELL = 2;
    public static final Codec<SurveyMap> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x0").forGetter(SurveyMap::x0), Codec.INT.fieldOf("z0").forGetter(SurveyMap::z0),
            Codec.INT.fieldOf("w").forGetter(SurveyMap::w), Codec.INT.fieldOf("h").forGetter(SurveyMap::h),
            Codec.BYTE_BUFFER.xmap(b -> { byte[] a = new byte[b.remaining()]; b.get(a); return a; }, ByteBuffer::wrap).fieldOf("cells").forGetter(SurveyMap::cells))
            .apply(i, SurveyMap::new));
    public static final StreamCodec<ByteBuf, SurveyMap> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SurveyMap::x0, ByteBufCodecs.VAR_INT, SurveyMap::z0, ByteBufCodecs.VAR_INT, SurveyMap::w,
            ByteBufCodecs.VAR_INT, SurveyMap::h, ByteBufCodecs.BYTE_ARRAY, SurveyMap::cells, SurveyMap::new);

    public static SurveyMap empty(int x0, int z0, int w, int h) {
        return new SurveyMap(x0, z0, w, h, new byte[w * h]);
    }

    public static int encode(double svH) {
        return (int) Math.max(1, Math.min(255, Math.round((Math.log10(Math.max(svH, 1e-8)) + 8) * 28 + 1)));
    }

    public static double decode(int b) {
        return b <= 0 ? 0 : Math.pow(10, (b - 1) / 28.0 - 8);
    }

    public int at(int i, int j) {
        return cells[j * w + i] & 0xFF;
    }

    public void set(int i, int j, double svH) {
        if (i >= 0 && j >= 0 && i < w && j < h) cells[j * w + i] = (byte) encode(svH);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SurveyMap m && m.x0 == x0 && m.z0 == z0 && m.w == w && m.h == h && java.util.Arrays.equals(m.cells, cells);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(cells) * 31 + x0 * 7 + z0;
    }
}
