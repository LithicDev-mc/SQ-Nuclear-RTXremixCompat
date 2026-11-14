package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DosimeterPayload(float today, float rate, float body, float total, boolean pinned, float dose, int iodineTicks,
        int medicineTicks, float outside, float suit, int filter) implements CustomPacketPayload {
    public static final Type<DosimeterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "dosimeter"));
    public static final StreamCodec<ByteBuf, DosimeterPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.today);
                buf.writeFloat(p.rate);
                buf.writeFloat(p.body);
                buf.writeFloat(p.total);
                buf.writeBoolean(p.pinned);
                buf.writeFloat(p.dose);
                buf.writeInt(p.iodineTicks);
                buf.writeInt(p.medicineTicks);
                buf.writeFloat(p.outside);
                buf.writeFloat(p.suit);
                buf.writeInt(p.filter);
            },
            buf -> new DosimeterPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readFloat(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
