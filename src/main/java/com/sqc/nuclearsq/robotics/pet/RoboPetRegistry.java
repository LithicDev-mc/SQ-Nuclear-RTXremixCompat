package com.sqc.nuclearsq.robotics.pet;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RoboPetRegistry {
    private RoboPetRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RoboPetEntity>> DOG = ENTITIES.register("robo_dog",
            () -> EntityType.Builder.of(RoboPetEntity::new, MobCategory.MISC).sized(0.6f, 0.75f).clientTrackingRange(8).build("robo_dog"));
    public static final DeferredHolder<EntityType<?>, EntityType<RoboPetEntity>> CAT = ENTITIES.register("robo_cat",
            () -> EntityType.Builder.of(RoboPetEntity::new, MobCategory.MISC).sized(0.5f, 0.6f).clientTrackingRange(8).build("robo_cat"));

    public static final DeferredItem<RoboPetKitItem> DOG_KIT = ITEMS.register("robo_dog_kit_sqc",
            () -> new RoboPetKitItem(new Item.Properties().stacksTo(1), DOG));
    public static final DeferredItem<RoboPetKitItem> CAT_KIT = ITEMS.register("robo_cat_kit_sqc",
            () -> new RoboPetKitItem(new Item.Properties().stacksTo(1), CAT));
    public static final DeferredItem<RoboToyItem> BALL = ITEMS.register("robo_ball_sqc", () -> new RoboToyItem(new Item.Properties().stacksTo(1), false));
    public static final DeferredItem<RoboToyItem> WAND = ITEMS.register("robo_feather_wand_sqc", () -> new RoboToyItem(new Item.Properties().stacksTo(1), true));

    public static final DeferredItem<RoboCollarItem> COLLAR_SNIFFER = ITEMS.register("robo_collar_sniffer_sqc", () -> new RoboCollarItem(new Item.Properties().stacksTo(1), RoboCollarItem.SNIFFER));
    public static final DeferredItem<RoboCollarItem> COLLAR_TIDY = ITEMS.register("robo_collar_tidy_sqc", () -> new RoboCollarItem(new Item.Properties().stacksTo(1), RoboCollarItem.TIDY));
    public static final DeferredItem<RoboCollarItem> COLLAR_LAMP = ITEMS.register("robo_collar_lamp_sqc", () -> new RoboCollarItem(new Item.Properties().stacksTo(1), RoboCollarItem.LAMP));

    private static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS = DeferredRegister.create(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.core.BlockPos>> CHEST = COMPONENTS.register("robo_collar_chest",
            () -> net.minecraft.core.component.DataComponentType.<net.minecraft.core.BlockPos>builder().persistent(net.minecraft.core.BlockPos.CODEC).networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC).build());

    public static net.minecraft.world.item.ItemStack collarStack(int kind) {
        return switch (kind) {
            case RoboCollarItem.SNIFFER -> new net.minecraft.world.item.ItemStack(COLLAR_SNIFFER.get());
            case RoboCollarItem.TIDY -> new net.minecraft.world.item.ItemStack(COLLAR_TIDY.get());
            case RoboCollarItem.LAMP -> new net.minecraft.world.item.ItemStack(COLLAR_LAMP.get());
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(DOG.get(), RoboPetEntity.attributes().build());
            event.put(CAT.get(), RoboPetEntity.attributes().build());
        });
    }
}
