package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadiationDebugPayload(long[] pos, float[] svH, byte[] kind) implements CustomPacketPayload {
    public static final byte KIND_CONTAMINATION = 0, KIND_SOURCE = 1, KIND_FALLOUT = 2;
    public static final Type<RadiationDebugPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "radiation_debug"));
    public static final StreamCodec<ByteBuf, RadiationDebugPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.pos.length);
                for (int i = 0; i < p.pos.length; i++) {
                    buf.writeLong(p.pos[i]);
                    buf.writeFloat(p.svH[i]);
                    buf.writeByte(p.kind[i]);
                }
            },
            buf -> {
                int n = Math.max(0, Math.min(buf.readInt(), 4096));
                long[] pos = new long[n];
                float[] sv = new float[n];
                byte[] kind = new byte[n];
                for (int i = 0; i < n; i++) {
                    pos[i] = buf.readLong();
                    sv[i] = buf.readFloat();
                    kind[i] = buf.readByte();
                }
                return new RadiationDebugPayload(pos, sv, kind);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
