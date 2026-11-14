package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MushroomCloudPayload(double x, double y, double z, float scale, boolean heavy) implements CustomPacketPayload {
    public static final Type<MushroomCloudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "mushroom_cloud"));
    public static final StreamCodec<ByteBuf, MushroomCloudPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, MushroomCloudPayload::x,
            ByteBufCodecs.DOUBLE, MushroomCloudPayload::y,
            ByteBufCodecs.DOUBLE, MushroomCloudPayload::z,
            ByteBufCodecs.FLOAT, MushroomCloudPayload::scale,
            ByteBufCodecs.BOOL, MushroomCloudPayload::heavy,
            MushroomCloudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
