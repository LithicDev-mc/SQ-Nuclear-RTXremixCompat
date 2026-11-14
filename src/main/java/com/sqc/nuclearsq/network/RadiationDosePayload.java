package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadiationDosePayload(float dose) implements CustomPacketPayload {
    public static final Type<RadiationDosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "radiation_dose"));
    public static final StreamCodec<ByteBuf, RadiationDosePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, RadiationDosePayload::dose, RadiationDosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
