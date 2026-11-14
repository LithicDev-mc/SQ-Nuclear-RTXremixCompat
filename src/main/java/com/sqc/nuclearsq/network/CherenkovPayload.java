package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CherenkovPayload(BlockPos pos, int level) implements CustomPacketPayload {
    public static final Type<CherenkovPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "cherenkov"));
    public static final StreamCodec<ByteBuf, CherenkovPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CherenkovPayload::pos,
            ByteBufCodecs.VAR_INT, CherenkovPayload::level,
            CherenkovPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
