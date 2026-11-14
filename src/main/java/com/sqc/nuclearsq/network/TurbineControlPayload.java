package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TurbineControlPayload(boolean running, int throttle) implements CustomPacketPayload {
    public static final Type<TurbineControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "turbine_control"));
    public static final StreamCodec<ByteBuf, TurbineControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TurbineControlPayload::running,
            ByteBufCodecs.VAR_INT, TurbineControlPayload::throttle,
            TurbineControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
