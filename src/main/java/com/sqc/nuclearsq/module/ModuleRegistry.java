package com.sqc.nuclearsq.module;

import java.util.EnumMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
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

public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final Map<ModuleKind, DeferredBlock<ModuleBlock>> BLOCK = new EnumMap<>(ModuleKind.class);
    public static final Map<ModuleKind, DeferredItem<BlockItem>> ITEM = new EnumMap<>(ModuleKind.class);

    static {
        for (ModuleKind k : ModuleKind.values()) {
            DeferredBlock<ModuleBlock> b = BLOCKS.register(k.id, () -> ModuleBlock.create(k, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
            BLOCK.put(k, b);
            ITEM.put(k, ITEMS.register(k.id, () -> k.tall ? new DoubleHighBlockItem(b.get(), new Item.Properties())
                    : new BlockItem(b.get(), new Item.Properties())));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModuleBlockEntity>> MODULE_BE =
            BLOCK_ENTITIES.register("machine_module", () -> BlockEntityType.Builder.of(ModuleBlockEntity::new,
                    BLOCK.values().stream().map(DeferredBlock::get).toArray(ModuleBlock[]::new)).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
