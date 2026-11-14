package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExplosionFlashPayload(int ticks, double x, double y, double z, float range) implements CustomPacketPayload {
    public static final Type<ExplosionFlashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "explosion_flash"));
    public static final StreamCodec<ByteBuf, ExplosionFlashPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ExplosionFlashPayload::ticks,
                    ByteBufCodecs.DOUBLE, ExplosionFlashPayload::x, ByteBufCodecs.DOUBLE, ExplosionFlashPayload::y,
                    ByteBufCodecs.DOUBLE, ExplosionFlashPayload::z, ByteBufCodecs.FLOAT, ExplosionFlashPayload::range,
                    ExplosionFlashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
