package com.sqc.nuclearsq.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LogEntry(long seq, long dayTime, long millis, int severity, Component text) {
    public static final int INFO = 0, CLEAR = 1, WARN = 2, ALARM = 3;

    public static final StreamCodec<RegistryFriendlyByteBuf, LogEntry> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LogEntry::seq, ByteBufCodecs.VAR_LONG, LogEntry::dayTime,
            ByteBufCodecs.VAR_LONG, LogEntry::millis, ByteBufCodecs.VAR_INT, LogEntry::severity,
            ComponentSerialization.TRUSTED_STREAM_CODEC, LogEntry::text, LogEntry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, java.util.List<LogEntry>> LIST_CODEC = CODEC.apply(ByteBufCodecs.list());

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putLong("seq", seq);
        t.putLong("day", dayTime);
        t.putLong("ms", millis);
        t.putInt("sev", severity);
        t.putString("text", Component.Serializer.toJson(text, registries));
        return t;
    }

    public static LogEntry load(CompoundTag t, HolderLookup.Provider registries) {
        return new LogEntry(t.getLong("seq"), t.getLong("day"), t.getLong("ms"), t.getInt("sev"),
                DataInstrumentBlockEntity.json(t.getString("text"), registries));
    }

    public static String clock(long dayTime) {
        long t = Math.floorMod(dayTime + 6000, 24000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    public static long day(long dayTime) {
        return dayTime / 24000 + 1;
    }
}
