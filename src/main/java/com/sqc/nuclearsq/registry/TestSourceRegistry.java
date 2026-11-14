package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.CreativeSourceBlock;
import com.sqc.nuclearsq.machine.CreativeSourceBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
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

public final class TestSourceRegistry {
    private TestSourceRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 12.0f);
    }

    public static final DeferredBlock<CreativeSourceBlock> ENERGY_SOURCE =
            BLOCKS.register("creative_energy_source_sqc", () -> new CreativeSourceBlock(props(), true));
    public static final DeferredItem<BlockItem> ENERGY_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("creative_energy_source_sqc", ENERGY_SOURCE);

    public static final DeferredBlock<CreativeSourceBlock> FLUID_SOURCE =
            BLOCKS.register("creative_fluid_source_sqc", () -> new CreativeSourceBlock(props(), false));
    public static final DeferredItem<BlockItem> FLUID_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("creative_fluid_source_sqc", FLUID_SOURCE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeSourceBlockEntity>> SOURCE_BE =
            BLOCK_ENTITIES.register("creative_source",
                    () -> BlockEntityType.Builder.of(CreativeSourceBlockEntity::new, ENERGY_SOURCE.get(), FLUID_SOURCE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TestSourceRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) ->
                be instanceof CreativeSourceBlockEntity s ? s.energy : null, ENERGY_SOURCE.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                be instanceof CreativeSourceBlockEntity s ? s.tank : null, FLUID_SOURCE.get());
    }
}
