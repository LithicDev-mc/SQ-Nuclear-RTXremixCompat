package com.sqc.nuclearsq.gamedata;

import com.sqc.nuclearsq.NuclearSQ;

import java.util.Map;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameDataPayload(Optional<String> cooling, Optional<String> particles, Map<String, String> multiblocks)
        implements CustomPacketPayload {
    public static final GameDataPayload EMPTY = new GameDataPayload(Optional.empty(), Optional.empty(), Map.of());

    private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(1 << 20);

    public static final Type<GameDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "game_data"));
    public static final StreamCodec<ByteBuf, GameDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(TEXT), GameDataPayload::cooling,
            ByteBufCodecs.optional(TEXT), GameDataPayload::particles,
            ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, TEXT), GameDataPayload::multiblocks,
            GameDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
