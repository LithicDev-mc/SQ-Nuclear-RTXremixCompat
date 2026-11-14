package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.spent.CastorCaskItem;
import com.sqc.nuclearsq.spent.LargeCastorBlock;
import com.sqc.nuclearsq.spent.LargeCastorBlockEntity;

import net.minecraft.core.registries.Registries;
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

public final class SpentFuelRegistry {
    private SpentFuelRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredItem<Item> SPENT_FUEL = ITEMS.registerSimpleItem("spent_fuel_sqc");

    public static final DeferredBlock<LargeCastorBlock> LARGE_CASTOR = BLOCKS.register("castor_large_sqc",
            () -> new LargeCastorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredItem<CastorCaskItem> LARGE_CASTOR_ITEM =
            ITEMS.registerItem("castor_large_sqc", p -> new CastorCaskItem(LARGE_CASTOR.get(), p.stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeCastorBlockEntity>> LARGE_CASTOR_BE =
            BLOCK_ENTITIES.register("castor_large", () -> BlockEntityType.Builder.of(LargeCastorBlockEntity::new, LARGE_CASTOR.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SpentFuelRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            LargeCastorBlockEntity m = LargeCastorBlock.entity(level, pos, state);
            return m == null ? null : m.items();
        }, LARGE_CASTOR.get());
    }
}
