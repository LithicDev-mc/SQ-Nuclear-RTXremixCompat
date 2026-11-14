package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VeinMinePayload(boolean held) implements CustomPacketPayload {
    public static final Type<VeinMinePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "vein_mine"));
    public static final StreamCodec<ByteBuf, VeinMinePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(VeinMinePayload::new, VeinMinePayload::held);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
