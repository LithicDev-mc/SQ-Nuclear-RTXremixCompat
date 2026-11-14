package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodBlockItem;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorCasingBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.reactor.RecombinerBlock;
import com.sqc.nuclearsq.reactor.RecombinerBlockEntity;
import com.sqc.nuclearsq.reactor.XenonFilterBlock;
import com.sqc.nuclearsq.reactor.XenonFilterBlockEntity;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

public final class ReactorRegistry {
    private ReactorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 12.0f);
    }

    private static BlockBehaviour.Properties casingProps() {
        return props().noOcclusion();
    }

    public static final DeferredBlock<ReactorCasingBlock> CASING =
            BLOCKS.register("reactor_casing_sqc", () -> new ReactorCasingBlock(casingProps()));
    public static final DeferredItem<BlockItem> CASING_ITEM = ITEMS.register("reactor_casing_sqc",
            () -> new com.sqc.nuclearsq.multiblock.PartBlockItem(CASING.get(), new net.minecraft.world.item.Item.Properties()));

    public static final DeferredBlock<RecombinerBlock> RECOMBINER =
            BLOCKS.register("recombiner_sqc", () -> new RecombinerBlock(props().noOcclusion()));
    public static final DeferredItem<BlockItem> RECOMBINER_ITEM = ITEMS.registerSimpleBlockItem("recombiner_sqc", RECOMBINER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecombinerBlockEntity>> RECOMBINER_BE =
            BLOCK_ENTITIES.register("recombiner", () -> BlockEntityType.Builder.of(RecombinerBlockEntity::new, RECOMBINER.get()).build(null));

    public static final DeferredBlock<XenonFilterBlock> XENON_FILTER =
            BLOCKS.register("xenon_filter_sqc", () -> new XenonFilterBlock(props().noOcclusion()));
    public static final DeferredItem<BlockItem> XENON_FILTER_ITEM = ITEMS.registerSimpleBlockItem("xenon_filter_sqc", XENON_FILTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XenonFilterBlockEntity>> XENON_FILTER_BE =
            BLOCK_ENTITIES.register("xenon_filter", () -> BlockEntityType.Builder.of(XenonFilterBlockEntity::new, XENON_FILTER.get()).build(null));

    public static final DeferredItem<Item> URANIUM_FRAGMENT = ITEMS.registerSimpleItem("uranium_fragment_sqc");
    public static final DeferredItem<Item> URANIUM_OXIDE = ITEMS.registerSimpleItem("uranium_oxide_sqc");
    public static final DeferredItem<Item> URANIUM_OXIDE_DUST = ITEMS.registerSimpleItem("uranium_oxide_dust_sqc");
    public static final DeferredItem<Item> YELLOWCAKE = ITEMS.registerSimpleItem("yellowcake_sqc");
    public static final DeferredItem<Item> URANIUM_SLUDGE = ITEMS.registerSimpleItem("uranium_sludge_sqc");
    public static final DeferredItem<Item> UF6_CYLINDER = ITEMS.registerSimpleItem("uf6_cylinder_sqc");
    public static final DeferredItem<Item> URANIUM_PELLET = ITEMS.registerSimpleItem("uranium_pellet_sqc");
    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust_sqc");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust_sqc");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust_sqc");
    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.registerSimpleItem("enriched_uranium_sqc");
    public static final DeferredItem<Item> FUEL_ROD = ITEMS.registerSimpleItem("fuel_rod_sqc");

    private static BlockBehaviour.Properties slimProps() {
        return props().noOcclusion();
    }

    private static BlockBehaviour.Properties rodProps() {
        return slimProps().lightLevel(st -> st.hasProperty(ReactorProps.GLOWING) && st.getValue(ReactorProps.GLOWING) && !st.getValue(ReactorProps.DRIVEN) ? 4 : 0);
    }

    public static final DeferredBlock<FuelRodBlock> FUEL_ROD_BLOCK =
            BLOCKS.register("fuel_rod_block_sqc", () -> new FuelRodBlock(slimProps()));
    public static final DeferredItem<Item> FUEL_ROD_BLOCK_ITEM =
            ITEMS.registerItem("fuel_rod_block_sqc", p -> new FuelRodBlockItem(FUEL_ROD_BLOCK.get(), p));

    public static final DeferredBlock<FuelRodBlock> FUEL_ROD_BLOCK_REINFORCED =
            BLOCKS.register("fuel_rod_block_reinforced_sqc", () -> new FuelRodBlock(slimProps(), 2));
    public static final DeferredItem<Item> FUEL_ROD_BLOCK_REINFORCED_ITEM =
            ITEMS.registerItem("fuel_rod_block_reinforced_sqc", p -> new FuelRodBlockItem(FUEL_ROD_BLOCK_REINFORCED.get(), p));
    public static final DeferredBlock<FuelRodBlock> FUEL_ROD_BLOCK_HARDENED =
            BLOCKS.register("fuel_rod_block_hardened_sqc", () -> new FuelRodBlock(slimProps(), 3));
    public static final DeferredItem<Item> FUEL_ROD_BLOCK_HARDENED_ITEM =
            ITEMS.registerItem("fuel_rod_block_hardened_sqc", p -> new FuelRodBlockItem(FUEL_ROD_BLOCK_HARDENED.get(), p));
    public static final DeferredBlock<FuelRodBlock> FUEL_ROD_BLOCK_EXPERIMENTAL =
            BLOCKS.register("fuel_rod_block_experimental_sqc", () -> new FuelRodBlock(slimProps(), 4));
    public static final DeferredItem<Item> FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM =
            ITEMS.registerItem("fuel_rod_block_experimental_sqc", p -> new FuelRodBlockItem(FUEL_ROD_BLOCK_EXPERIMENTAL.get(), p));

    public static final DeferredBlock<FuelRodHeadBlock> FUEL_ROD_HEAD =
            BLOCKS.register("fuel_rod_head_sqc", () -> new FuelRodHeadBlock(rodProps(), 1));
    public static final DeferredItem<BlockItem> FUEL_ROD_HEAD_ITEM = ITEMS.registerSimpleBlockItem("fuel_rod_head_sqc", FUEL_ROD_HEAD);
    public static final DeferredBlock<FuelRodHeadBlock> FUEL_ROD_HEAD_REINFORCED =
            BLOCKS.register("fuel_rod_head_reinforced_sqc", () -> new FuelRodHeadBlock(rodProps(), 2));
    public static final DeferredItem<BlockItem> FUEL_ROD_HEAD_REINFORCED_ITEM =
            ITEMS.registerSimpleBlockItem("fuel_rod_head_reinforced_sqc", FUEL_ROD_HEAD_REINFORCED);
    public static final DeferredBlock<FuelRodHeadBlock> FUEL_ROD_HEAD_HARDENED =
            BLOCKS.register("fuel_rod_head_hardened_sqc", () -> new FuelRodHeadBlock(rodProps(), 3));
    public static final DeferredItem<BlockItem> FUEL_ROD_HEAD_HARDENED_ITEM =
            ITEMS.registerSimpleBlockItem("fuel_rod_head_hardened_sqc", FUEL_ROD_HEAD_HARDENED);
    public static final DeferredBlock<FuelRodHeadBlock> FUEL_ROD_HEAD_EXPERIMENTAL =
            BLOCKS.register("fuel_rod_head_experimental_sqc", () -> new FuelRodHeadBlock(rodProps(), 4));
    public static final DeferredItem<BlockItem> FUEL_ROD_HEAD_EXPERIMENTAL_ITEM =
            ITEMS.registerSimpleBlockItem("fuel_rod_head_experimental_sqc", FUEL_ROD_HEAD_EXPERIMENTAL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelRodBlockEntity>> FUEL_ROD_BE =
            BLOCK_ENTITIES.register("fuel_rod",
                    () -> BlockEntityType.Builder.of(FuelRodBlockEntity::new, FUEL_ROD_BLOCK.get(), FUEL_ROD_BLOCK_REINFORCED.get(),
                            FUEL_ROD_BLOCK_HARDENED.get(), FUEL_ROD_BLOCK_EXPERIMENTAL.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, XENON_FILTER_BE.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, XENON_FILTER_BE.get(), (be, side) -> be.items());
        });
    }
}
