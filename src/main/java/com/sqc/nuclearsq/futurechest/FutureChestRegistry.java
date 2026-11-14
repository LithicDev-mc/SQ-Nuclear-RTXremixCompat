package com.sqc.nuclearsq.futurechest;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FutureChestRegistry {
    private FutureChestRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CONTENTS = COMPONENTS.register(
            "future_chest_contents", () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> UPGRADES = COMPONENTS.register(
            "future_chest_upgrades", () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    public static final java.util.Map<QuantumUpgradeItem.Kind, DeferredItem<QuantumUpgradeItem>> UPGRADE_ITEMS = new java.util.EnumMap<>(QuantumUpgradeItem.Kind.class);

    static {
        for (QuantumUpgradeItem.Kind k : QuantumUpgradeItem.Kind.values()) UPGRADE_ITEMS.put(k, ITEMS.register(k.id, () -> new QuantumUpgradeItem(k)));
    }

    public static final DeferredBlock<FutureChestBlock> BLOCK = BLOCKS.register("future_chest_sqc",
            () -> new FutureChestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 12.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<FutureChestItem> ITEM = ITEMS.register("future_chest_sqc",
            () -> new FutureChestItem(BLOCK.get(), new net.minecraft.world.item.Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FutureChestBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "future_chest", () -> BlockEntityType.Builder.of(FutureChestBlockEntity::new, BLOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<FutureChestMenu>> MENU = MENUS.register("future_chest",
            () -> IMenuTypeExtension.create((id, inv, buf) -> {
                int cap = buf.readVarInt();
                return new FutureChestMenu(id, inv, new SimpleContainer(FutureChestBlockEntity.MAX_SIZE), new SimpleContainer(FutureChestBlockEntity.UPGRADE_SLOTS), cap);
            }));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(FutureChestRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(),
                (be, side) -> be.getBlockState().getValue(FutureChestBlock.UNFOLDED) ? new InvWrapper(be) : null);
    }
}
