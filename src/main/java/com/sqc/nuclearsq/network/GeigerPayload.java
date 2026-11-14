package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GeigerPayload(float rate, boolean hasTarget, float x, float y, float z, float apparent, boolean here)
        implements CustomPacketPayload {
    public static final Type<GeigerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "geiger"));
    public static final StreamCodec<ByteBuf, GeigerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.rate);
                buf.writeBoolean(p.hasTarget);
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                buf.writeFloat(p.apparent);
                buf.writeBoolean(p.here);
            },
            buf -> new GeigerPayload(buf.readFloat(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readBoolean()));

    public static GeigerPayload reading(float rate) {
        return new GeigerPayload(rate, false, 0f, 0f, 0f, 0f, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
