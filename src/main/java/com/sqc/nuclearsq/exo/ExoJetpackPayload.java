package com.sqc.nuclearsq.exo;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExoJetpackPayload(int bits) implements CustomPacketPayload {
    public static final Type<ExoJetpackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "exo_jetpack"));
    public static final StreamCodec<ByteBuf, ExoJetpackPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ExoJetpackPayload::new, ExoJetpackPayload::bits);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
