package com.sqc.nuclearsq.transport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TransportRegistry {
    private TransportRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final Map<TransportTier, DeferredBlock<ConduitBlock>> PIPES = new EnumMap<>(TransportTier.class);
    public static final Map<TransportTier, DeferredBlock<ConduitBlock>> CABLES = new EnumMap<>(TransportTier.class);
    public static final Map<TransportTier, DeferredBlock<ConduitBlock>> ITEM_PIPES = new EnumMap<>(TransportTier.class);
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    private static BlockBehaviour.Properties conduitProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f).sound(SoundType.METAL)
                .noOcclusion().dynamicShape();
    }

    static {
        for (TransportTier t : TransportTier.values()) {
            if (t.pipeRate > 0) PIPES.put(t, BLOCKS.register("pipe_" + t.name + "_sqc", () -> new ConduitBlock(conduitProps(), true, t)));
        }
        for (TransportTier t : TransportTier.values()) {
            CABLES.put(t, BLOCKS.register("cable_" + t.name + "_sqc", () -> new CableBlock(conduitProps(), t)));
        }
        for (TransportTier t : TransportTier.values()) {
            if (t.itemRate > 0) ITEM_PIPES.put(t, BLOCKS.register("item_pipe_" + t.name + "_sqc", () -> new ItemPipeBlock(conduitProps(), t)));
        }
        for (var m : List.of(PIPES, CABLES, ITEM_PIPES)) {
            for (var b : m.values()) BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(b));
        }
    }

    public static final DeferredBlock<BigPipeBlock> BIG_PIPE =
            BLOCKS.register("big_pipe_sqc", () -> new BigPipeBlock(conduitProps().strength(2.0f, 8.0f).noLootTable()));
    public static final DeferredItem<BlockItem> BIG_PIPE_ITEM =
            ITEMS.register("big_pipe_sqc", () -> new BigPipeItem(BIG_PIPE.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BigPipeBlockEntity>> BIG_PIPE_BE =
            BLOCK_ENTITIES.register("big_pipe", () -> BlockEntityType.Builder.of(BigPipeBlockEntity::new, BIG_PIPE.get()).build(null));

    public static final DeferredBlock<PowerGateBlock> POWER_GATE =
            BLOCKS.register("power_gate_sqc", () -> new PowerGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 8.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> POWER_GATE_ITEM = ITEMS.registerSimpleBlockItem(POWER_GATE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerGateBlockEntity>> POWER_GATE_BE =
            BLOCK_ENTITIES.register("power_gate", () -> BlockEntityType.Builder.of(PowerGateBlockEntity::new, POWER_GATE.get()).build(null));

    static {
        BLOCK_ITEMS.add(POWER_GATE_ITEM);
    }

    public static final DeferredBlock<ConduitFrameBlock> CONDUIT_FRAME =
            BLOCKS.register("conduit_frame_sqc", () -> new ConduitFrameBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> CONDUIT_FRAME_ITEM = ITEMS.registerSimpleBlockItem(CONDUIT_FRAME);

    static {
        BLOCK_ITEMS.add(CONDUIT_FRAME_ITEM);
    }

    public static final DeferredItem<WrenchItem> WRENCH =
            ITEMS.register("wrench_sqc", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConduitBlockEntity>> CONDUIT_BE =
            BLOCK_ENTITIES.register("conduit", () -> BlockEntityType.Builder.of(ConduitBlockEntity::new, conduits()).build(null));

    private static Block[] conduits() {
        List<Block> out = new ArrayList<>();
        PIPES.values().forEach(b -> out.add(b.get()));
        CABLES.values().forEach(b -> out.add(b.get()));
        ITEM_PIPES.values().forEach(b -> out.add(b.get()));
        out.add(com.sqc.nuclearsq.data.DataRegistry.CABLE.get());
        return out.toArray(Block[]::new);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TransportRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONDUIT_BE.get(), ConduitBlockEntity::fluidSide);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CONDUIT_BE.get(), ConduitBlockEntity::energySide);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONDUIT_BE.get(), ConduitBlockEntity::itemSide);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, POWER_GATE_BE.get(), PowerGateBlockEntity::energySide);
    }
}
