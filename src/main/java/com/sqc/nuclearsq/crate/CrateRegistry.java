package com.sqc.nuclearsq.crate;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CrateRegistry {
    private CrateRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CONTENTS = COMPONENTS.register(
            "storage_crate_contents", () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> UPGRADE = COMPONENTS.register(
            "storage_crate_upgrade", () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    public static final DeferredBlock<StorageCrateBlock> BLOCK = BLOCKS.register("storage_crate_sqc",
            () -> new StorageCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 12.0f).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredItem<StorageCrateItem> ITEM = ITEMS.register("storage_crate_sqc",
            () -> new StorageCrateItem(BLOCK.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CrateUpgradeItem> UPGRADE_1 = ITEMS.register("storage_crate_upgrade_1_sqc", () -> new CrateUpgradeItem(1));
    public static final DeferredItem<CrateUpgradeItem> UPGRADE_2 = ITEMS.register("storage_crate_upgrade_2_sqc", () -> new CrateUpgradeItem(2));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCrateBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "storage_crate", () -> BlockEntityType.Builder.of(StorageCrateBlockEntity::new, BLOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<StorageCrateMenu>> MENU = MENUS.register("storage_crate",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new StorageCrateMenu(id, inv, new SimpleContainer(StorageCrateBlockEntity.MAX_SIZE),
                    new SimpleContainer(1), new net.minecraft.world.inventory.SimpleContainerData(StorageCrateMenu.DATA), buf.readVarInt())));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(CrateRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, st, be, side) -> {
            StorageCrateBlockEntity main = StorageCrateBlock.main(level, pos);
            return main == null ? null : main.handler(side);
        }, BLOCK.get());
    }
}
