package com.sqc.nuclearsq.oil;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OilRegistry {
    private OilRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, NuclearSQ.MODID);

    public static final DeferredBlock<OilWellBlock> WELL = BLOCKS.register("oil_well_sqc", () -> new OilWellBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(0.6f, 3_600_000.0f).sound(SoundType.GRAVEL).pushReaction(PushReaction.BLOCK).noLootTable()));
    public static final DeferredItem<BlockItem> WELL_ITEM = ITEMS.registerSimpleBlockItem("oil_well_sqc", WELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilWellBlockEntity>> WELL_BE = BLOCK_ENTITIES.register("oil_well",
            () -> BlockEntityType.Builder.of(OilWellBlockEntity::new, WELL.get()).build(null));
    public static final DeferredHolder<Feature<?>, OilWellFeature> WELL_FEATURE = FEATURES.register("oil_well", OilWellFeature::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FEATURES.register(modBus);
    }
}
