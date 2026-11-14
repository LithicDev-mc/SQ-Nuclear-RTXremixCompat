package com.sqc.nuclearsq.registry;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    private ModDataComponents() {}

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_ENERGY = COMPONENTS.register(
            "stored_energy", () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FUEL_CHARGE = COMPONENTS.register(
            "fuel_charge", () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, FuelRodBlockEntity.UNIT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> FUEL_UNLOADED = COMPONENTS.register(
            "fuel_unloaded", () -> DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> FUEL_MOX = COMPONENTS.register(
            "fuel_mox", () -> DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> GRID_FROM = COMPONENTS.register(
            "grid_from", () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FILTER_LEFT = COMPONENTS.register(
            "filter_left", () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 36000)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> FILTER_COMBO = COMPONENTS.register(
            "filter_combo", () -> DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> CONTAMINATION = COMPONENTS.register(
            "contamination", () -> DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> DRIVE_ID = COMPONENTS.register(
            "drive_id", () -> DataComponentType.<java.util.UUID>builder().persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.sqc.nuclearsq.storage.DriveItem.Fill>> DRIVE_FILL =
            COMPONENTS.register("drive_fill", () -> DataComponentType.<com.sqc.nuclearsq.storage.DriveItem.Fill>builder()
                    .persistent(com.sqc.nuclearsq.storage.DriveItem.Fill.CODEC).networkSynchronized(com.sqc.nuclearsq.storage.DriveItem.Fill.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.sqc.nuclearsq.storage.Pattern>> PATTERN =
            COMPONENTS.register("pattern", () -> DataComponentType.<com.sqc.nuclearsq.storage.Pattern>builder()
                    .persistent(com.sqc.nuclearsq.storage.Pattern.CODEC).networkSynchronized(com.sqc.nuclearsq.storage.Pattern.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> WIFI_LINK = COMPONENTS.register(
            "wifi_link", () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
