package com.sqc.nuclearsq.exo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ModuleEntry(String id, int level) {
    public static final Codec<ModuleEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ModuleEntry::id),
            Codec.INT.fieldOf("level").forGetter(ModuleEntry::level)).apply(i, ModuleEntry::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ModuleEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModuleEntry::id, ByteBufCodecs.VAR_INT, ModuleEntry::level, ModuleEntry::new);
}
