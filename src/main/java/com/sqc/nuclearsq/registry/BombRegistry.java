package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.bomb.FallingBombEntity;
import com.sqc.nuclearsq.explosion.bomb.LargeNuclearBombBlock;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlock;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlockEntity;
import com.sqc.nuclearsq.explosion.bomb.TsarBombBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BombRegistry {
    private BombRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<NuclearBombBlock> BOMB = BLOCKS.register("nuclear_bomb_sqc",
            () -> new NuclearBombBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.NORMAL)));
    public static final DeferredItem<BlockItem> BOMB_ITEM = ITEMS.registerSimpleBlockItem("nuclear_bomb_sqc", BOMB,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredBlock<LargeNuclearBombBlock> LARGE_BOMB = BLOCKS.register("nuclear_bomb_large_sqc",
            () -> new LargeNuclearBombBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> LARGE_BOMB_ITEM = ITEMS.registerSimpleBlockItem("nuclear_bomb_large_sqc", LARGE_BOMB,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredBlock<TsarBombBlock> TSAR_BOMB = BLOCKS.register("nuclear_bomb_tsar_sqc",
            () -> new TsarBombBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(8.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> TSAR_BOMB_ITEM = ITEMS.registerSimpleBlockItem("nuclear_bomb_tsar_sqc", TSAR_BOMB,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NuclearBombBlockEntity>> BOMB_BE =
            BLOCK_ENTITIES.register("nuclear_bomb",
                    () -> BlockEntityType.Builder.of(NuclearBombBlockEntity::new, BOMB.get(), LARGE_BOMB.get(), TSAR_BOMB.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<FallingBombEntity>> FALLING_BOMB =
            ENTITIES.register("falling_nuclear_bomb", () -> EntityType.Builder.<FallingBombEntity>of(FallingBombEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.98f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build(NuclearSQ.MODID + ":falling_nuclear_bomb"));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
    }
}
