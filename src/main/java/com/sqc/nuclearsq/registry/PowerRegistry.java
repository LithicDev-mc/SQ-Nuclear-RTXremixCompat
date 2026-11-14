package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.power.RtgBlock;
import com.sqc.nuclearsq.power.RtgBlockEntity;
import com.sqc.nuclearsq.power.RtgCapsuleItem;
import com.sqc.nuclearsq.power.SolarCollectorBlock;
import com.sqc.nuclearsq.power.SolarCollectorBlockEntity;
import com.sqc.nuclearsq.power.SolarPanelBlock;
import com.sqc.nuclearsq.power.SolarPanelBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

public final class PowerRegistry {
    private PowerRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel_sqc",
            () -> new SolarPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0f, 6.0f)
                    .sound(SoundType.GLASS).noOcclusion()));
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM = ITEMS.registerSimpleBlockItem("solar_panel_sqc", SOLAR_PANEL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL_BE =
            BLOCK_ENTITIES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL.get()).build(null));

    public static final DeferredBlock<SolarCollectorBlock> SOLAR_COLLECTOR = BLOCKS.register("solar_collector_sqc",
            () -> new SolarCollectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.5f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> SOLAR_COLLECTOR_ITEM = ITEMS.registerSimpleBlockItem("solar_collector_sqc", SOLAR_COLLECTOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarCollectorBlockEntity>> SOLAR_COLLECTOR_BE =
            BLOCK_ENTITIES.register("solar_collector", () -> BlockEntityType.Builder.of(SolarCollectorBlockEntity::new, SOLAR_COLLECTOR.get()).build(null));

    public static final DeferredBlock<RtgBlock> RTG = BLOCKS.register("rtg_sqc",
            () -> new RtgBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 8.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(RtgBlock.LOADED) ? 5 : 0)));
    public static final DeferredItem<BlockItem> RTG_ITEM = ITEMS.registerSimpleBlockItem("rtg_sqc", RTG);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RtgBlockEntity>> RTG_BE =
            BLOCK_ENTITIES.register("rtg", () -> BlockEntityType.Builder.of(RtgBlockEntity::new, RTG.get()).build(null));

    public static final DeferredItem<Item> ISOTOPE_CAPSULE = ITEMS.registerItem("isotope_capsule_sqc", RtgCapsuleItem::new);
    public static final DeferredItem<Item> PLUTONIUM_CAPSULE = ITEMS.registerItem("plutonium_capsule_sqc",
            p -> new RtgCapsuleItem(p, RtgBlockEntity.PEAK_PU238, RtgBlockEntity.STEP_TICKS_PU238));
    public static final DeferredItem<Item> STRONTIUM_CAPSULE = ITEMS.registerItem("strontium_capsule_sqc",
            p -> new RtgCapsuleItem(p, RtgBlockEntity.PEAK_SR, RtgBlockEntity.STEP_TICKS_SR));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(PowerRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SOLAR_PANEL_BE.get(), (be, side) -> be.port(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SOLAR_COLLECTOR_BE.get(), (be, side) -> be.port(side));
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> RtgBlockEntity.port(), RTG.get());
    }
}
