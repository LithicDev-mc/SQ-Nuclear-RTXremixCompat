package com.sqc.nuclearsq.data;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PowerPayload(int containerId, List<PowerSample> fine, boolean replaceCoarse, List<PowerSample> coarse, PowerSummary summary)
        implements CustomPacketPayload {
    public static final Type<PowerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "power_monitor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PowerPayload::containerId, PowerSample.LIST_CODEC, PowerPayload::fine,
            ByteBufCodecs.BOOL, PowerPayload::replaceCoarse, PowerSample.LIST_CODEC, PowerPayload::coarse,
            PowerSummary.CODEC, PowerPayload::summary, PowerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
