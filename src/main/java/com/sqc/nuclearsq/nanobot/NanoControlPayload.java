package com.sqc.nuclearsq.nanobot;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NanoControlPayload(int action, int value) implements CustomPacketPayload {
    public static final Type<NanoControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "nano_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NanoControlPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeInt(p.value);
            },
            buf -> new NanoControlPayload(buf.readByte(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
