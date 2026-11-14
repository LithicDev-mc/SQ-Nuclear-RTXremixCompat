package com.sqc.nuclearsq.nanobot;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GadgetKeyPayload(int action, int value) implements CustomPacketPayload {
    public static final Type<GadgetKeyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "gadget_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GadgetKeyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeInt(p.value);
            },
            buf -> new GadgetKeyPayload(buf.readByte(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
