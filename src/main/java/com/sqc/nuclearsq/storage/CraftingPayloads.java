package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class CraftingPayloads {
    private CraftingPayloads() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, path);
    }

    public record Clear(int containerId) implements CustomPacketPayload {
        public static final Type<Clear> TYPE = new Type<>(id("crafting_clear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Clear> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Clear::containerId, Clear::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Fill(int containerId, ResourceLocation recipe) implements CustomPacketPayload {
        public static final Type<Fill> TYPE = new Type<>(id("crafting_fill"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fill> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Fill::containerId, ResourceLocation.STREAM_CODEC, Fill::recipe, Fill::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
