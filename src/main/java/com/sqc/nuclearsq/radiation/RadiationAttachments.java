package com.sqc.nuclearsq.radiation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.NuclearSQ;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class RadiationAttachments {
    private RadiationAttachments() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    public static final Supplier<AttachmentType<Double>> DOSE = TYPES.register("dose",
            () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());

    public static final Supplier<AttachmentType<Double>> BODY = TYPES.register("body_contamination",
            () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());

    public static final Supplier<AttachmentType<Long>> IODINE_UNTIL = TYPES.register("iodine_until",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    public static final Supplier<AttachmentType<Long>> MEDICINE_UNTIL = TYPES.register("medicine_until",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    public static final Supplier<AttachmentType<Double>> DOSIMETER_TOTAL = TYPES.register("dosimeter_total",
            () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());

    public record Dosimeter(long day, double today, int warn, boolean pinned, float rate, float outside, float suit) {
        public static final Dosimeter EMPTY = new Dosimeter(0, 0, 0, false, 0, 0, 0);
        static final Codec<Dosimeter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Dosimeter::day),
                Codec.DOUBLE.fieldOf("today").forGetter(Dosimeter::today),
                Codec.INT.fieldOf("warn").forGetter(Dosimeter::warn),
                Codec.BOOL.fieldOf("pinned").forGetter(Dosimeter::pinned),
                Codec.FLOAT.fieldOf("rate").forGetter(Dosimeter::rate),
                Codec.FLOAT.fieldOf("outside").forGetter(Dosimeter::outside),
                Codec.FLOAT.fieldOf("suit").forGetter(Dosimeter::suit)).apply(i, Dosimeter::new));

        public Dosimeter withDay(long day, double today, int warn) { return new Dosimeter(day, today, warn, pinned, rate, outside, suit); }
        public Dosimeter withToday(double today) { return new Dosimeter(day, today, warn, pinned, rate, outside, suit); }
        public Dosimeter withWarn(int warn) { return new Dosimeter(day, today, warn, pinned, rate, outside, suit); }
        public Dosimeter withPinned(boolean pinned) { return new Dosimeter(day, today, warn, pinned, rate, outside, suit); }
        public Dosimeter withShown(float rate, float outside, float suit) { return new Dosimeter(day, today, warn, pinned, rate, outside, suit); }
    }

    public static final Supplier<AttachmentType<Dosimeter>> DOSIMETER = TYPES.register("dosimeter",
            () -> AttachmentType.builder(() -> Dosimeter.EMPTY).serialize(Dosimeter.CODEC).build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
