package com.sqc.nuclearsq.nanobot;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NanobotRegistry {
    private NanobotRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TASK = COMPONENTS.register(
            "nano_task", () -> DataComponentType.<Integer>builder().persistent(NanoCodecs.clamped(0, NanoTask.values().length - 1))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final java.util.List<DeferredItem<PocketBatteryItem>> POCKET_BATTERIES = new java.util.ArrayList<>();

    public static final DeferredItem<NanoCanisterItem> CANISTER = ITEMS.register("nano_swarm_canister_sqc",
            () -> new NanoCanisterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<DroneItemPortBlock> DRONE_ITEM_PORT = BLOCKS.register("drone_item_port_sqc",
            () -> new DroneItemPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 12.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> DRONE_ITEM_PORT_ITEM = ITEMS.registerSimpleBlockItem("drone_item_port_sqc", DRONE_ITEM_PORT);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> PORT = COMPONENTS.register(
            "nano_port", () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GadgetData>> GADGET = COMPONENTS.register(
            "nano_gadget", () -> DataComponentType.<GadgetData>builder().persistent(GadgetData.CODEC).networkSynchronized(GadgetData.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> UPGRADES = COMPONENTS.register(
            "nano_upgrades", () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RADIUS = COMPONENTS.register(
            "nano_radius", () -> DataComponentType.<Integer>builder().persistent(NanoCodecs.clamped(0, NanoSettings.HARD_MAX_RADIUS))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<NanoCanisterMenu>> MENU =
            MENUS.register("nano_canister", () -> IMenuTypeExtension.create(NanoCanisterMenu::new));

    public static final Map<String, DeferredItem<NanoUpgradeItem>> UPGRADE_ITEMS = new LinkedHashMap<>();

    static {
        String[] sizes = {"small", "medium", "large"};
        for (int t = 1; t <= 3; t++) {
            int tier = t;
            POCKET_BATTERIES.add(ITEMS.register("pocket_battery_" + sizes[t - 1] + "_sqc", () -> new PocketBatteryItem(new Item.Properties(), tier)));
        }
        for (NanoUpgradeItem.Type type : NanoUpgradeItem.Type.values()) {
            for (int level = 1; level <= type.maxLevel; level++) {
                int lv = level;
                UPGRADE_ITEMS.put(type.id + "_" + level, ITEMS.register("nano_upgrade_" + type.id + "_" + level + "_sqc",
                        () -> new NanoUpgradeItem(new Item.Properties().stacksTo(16), type, lv)));
            }
        }
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(NanoPower::registerCapabilities);
    }
}
