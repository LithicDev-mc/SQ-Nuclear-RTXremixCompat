package com.sqc.nuclearsq.compat.powergrid;

import com.sqc.nuclearsq.NuclearSQ;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PowerGridRegistry {
    private PowerGridRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<GridTerminalBlock> TERMINAL = BLOCKS.register("powergrid_terminal_sqc",
            () -> new GridTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 8.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<Item> TERMINAL_ITEM = ITEMS.register("powergrid_terminal_sqc",
            () -> new BlockItem(TERMINAL.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridTerminalBlockEntity>> TERMINAL_BE =
            BLOCK_ENTITIES.register("powergrid_terminal",
                    () -> BlockEntityType.Builder.of(GridTerminalBlockEntity::new, TERMINAL.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
