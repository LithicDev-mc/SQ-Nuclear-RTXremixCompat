package com.sqc.nuclearsq.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PowerSample(long prod, long cons, long stored, long capacity, long in, long out) {
    public static final PowerSample ZERO = new PowerSample(0, 0, 0, 0, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, PowerSample> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PowerSample::prod, ByteBufCodecs.VAR_LONG, PowerSample::cons,
            ByteBufCodecs.VAR_LONG, PowerSample::stored, ByteBufCodecs.VAR_LONG, PowerSample::capacity,
            ByteBufCodecs.VAR_LONG, PowerSample::in, ByteBufCodecs.VAR_LONG, PowerSample::out, PowerSample::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, java.util.List<PowerSample>> LIST_CODEC = CODEC.apply(ByteBufCodecs.list());

    public long balance() {
        return prod - cons;
    }

    public float fill() {
        return capacity <= 0 ? 0f : Math.max(0f, Math.min(1f, stored / (float) capacity));
    }

    public long[] toArray() {
        return new long[] {prod, cons, stored, capacity, in, out};
    }

    public static PowerSample of(long[] a, int off) {
        return new PowerSample(a[off], a[off + 1], a[off + 2], a[off + 3], a[off + 4], a[off + 5]);
    }

    public static final int FIELDS = 6;

    public static PowerSample average(java.util.List<PowerSample> list, int from, int n) {
        if (n <= 0 || from < 0 || from + n > list.size()) return ZERO;
        long p = 0, c = 0, i = 0, o = 0;
        for (int k = from; k < from + n; k++) {
            PowerSample s = list.get(k);
            p += s.prod;
            c += s.cons;
            i += s.in;
            o += s.out;
        }
        PowerSample last = list.get(from + n - 1);
        return new PowerSample(Math.round(p / (double) n), Math.round(c / (double) n), last.stored, last.capacity,
                Math.round(i / (double) n), Math.round(o / (double) n));
    }
}
