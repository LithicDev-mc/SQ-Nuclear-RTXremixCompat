package com.sqc.nuclearsq.data;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LogbookPayload(int containerId, List<LogEntry> entries) implements CustomPacketPayload {
    public static final Type<LogbookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "logbook_append"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogbookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LogbookPayload::containerId, LogEntry.LIST_CODEC, LogbookPayload::entries, LogbookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
