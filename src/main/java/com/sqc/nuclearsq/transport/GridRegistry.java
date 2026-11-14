package com.sqc.nuclearsq.transport;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GridRegistry {
    private GridRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<PowerMastBlock> MAST = BLOCKS.register("power_mast_sqc", () -> new PowerMastBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 10.0f).sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<LargePowerMastBlock> MAST_LARGE = BLOCKS.register("power_mast_large_sqc", () -> new LargePowerMastBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 10.0f).sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> MAST_ITEM = ITEMS.registerSimpleBlockItem(MAST);
    public static final DeferredItem<BlockItem> MAST_LARGE_ITEM = ITEMS.registerSimpleBlockItem(MAST_LARGE);
    public static final DeferredItem<OverheadWireItem> WIRE = ITEMS.register("overhead_wire_sqc", () -> new OverheadWireItem(new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridNodeBlockEntity>> NODE_BE =
            BLOCK_ENTITIES.register("grid_node", () -> BlockEntityType.Builder.of(GridNodeBlockEntity::new, MAST.get(), MAST_LARGE.get()).build(null));

    public static List<ItemLike> tabItems() {
        return List.of(MAST_ITEM.get(), MAST_LARGE_ITEM.get(), WIRE.get());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
