package com.sqc.nuclearsq.door;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DoorRegistry {
    private DoorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties steel(float hardness, float blast) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(hardness, blast).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<DoorPanelBlock> HANGAR_PANEL = BLOCKS.register("hangar_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.HANGAR, 10));
    public static final DeferredBlock<DoorPanelBlock> AIRLOCK_PANEL = BLOCKS.register("airlock_door_panel_sqc",
            () -> new DoorPanelBlock(steel(5.0f, 30.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.AIRLOCK, 25));
    public static final DeferredBlock<DoorPanelBlock> SECTIONAL_PANEL = BLOCKS.register("sectional_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.SECTIONAL, 12));
    public static final DeferredBlock<DoorPanelBlock> GRILLE_PANEL = BLOCKS.register("grille_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.GRILLE, 10));
    public static final DeferredBlock<DoorPanelBlock> FOLDING_PANEL = BLOCKS.register("folding_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.FOLDING, 12));
    public static final DeferredBlock<DoorPanelBlock> VAULT_PANEL = BLOCKS.register("vault_door_panel_sqc",
            () -> new DoorPanelBlock(steel(8.0f, 1200.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.VAULT, 24));
    public static final DeferredBlock<DoorPanelBlock> LAB_PANEL = BLOCKS.register("lab_door_panel_sqc",
            () -> new DoorPanelBlock(steel(2.0f, 6.0f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.LAB, 25));
    public static final DeferredBlock<DoorPanelBlock> GLASS_PANEL = BLOCKS.register("glass_door_panel_sqc",
            () -> new DoorPanelBlock(steel(2.0f, 6.0f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.GLASS, 25));
    public static final DeferredBlock<DoorPanelBlock> SHIELD_PANEL = BLOCKS.register("shield_door_panel_sqc",
            () -> new DoorPanelBlock(steel(6.0f, 600.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.SHIELD, 30));
    public static final DeferredBlock<DoorPanelBlock> COLD_PANEL = BLOCKS.register("cold_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.COLD, 30));
    public static final DeferredBlock<DoorPanelBlock> CAGE_PANEL = BLOCKS.register("cage_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.CAGE, 25));
    public static final DeferredBlock<DoorPanelBlock> SLIDE_PANEL = BLOCKS.register("slide_door_panel_sqc",
            () -> new DoorPanelBlock(steel(2.0f, 6.0f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.SLIDE, 20));
    public static final DeferredBlock<DoorPanelBlock> INDUSTRIAL_PANEL = BLOCKS.register("industrial_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.INDUSTRIAL, 12));
    public static final DeferredBlock<DoorPanelBlock> PERFORATED_PANEL = BLOCKS.register("perforated_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.PERFORATED, 10));
    public static final DeferredBlock<DoorPanelBlock> SPEED_PANEL = BLOCKS.register("speed_door_panel_sqc",
            () -> new DoorPanelBlock(steel(3.0f, 12.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.SPEED, 4));
    public static final DeferredBlock<DoorPanelBlock> LIFT_PANEL = BLOCKS.register("lift_door_panel_sqc",
            () -> new DoorPanelBlock(steel(6.0f, 600.0f).noOcclusion().pushReaction(PushReaction.BLOCK), DoorPanelBlock.Kind.LIFT, 20));
    public static final DeferredBlock<DoorControllerBlock> CONTROLLER = BLOCKS.register("door_control_sqc",
            () -> new DoorControllerBlock(steel(3.0f, 12.0f)));

    public static final DeferredBlock<AirlockBlock> AIRLOCK = BLOCKS.register("airlock_control_sqc", () -> new AirlockBlock(steel(3.0f, 12.0f)));
    public static final DeferredItem<BlockItem> AIRLOCK_ITEM = ITEMS.registerSimpleBlockItem(AIRLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirlockBlockEntity>> AIRLOCK_BE =
            BLOCK_ENTITIES.register("airlock_control", () -> BlockEntityType.Builder.of(AirlockBlockEntity::new, AIRLOCK.get()).build(null));

    public static final DeferredItem<BlockItem> CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<BlockItem> HANGAR_PANEL_ITEM = ITEMS.registerSimpleBlockItem(HANGAR_PANEL);
    public static final DeferredItem<BlockItem> AIRLOCK_PANEL_ITEM = ITEMS.registerSimpleBlockItem(AIRLOCK_PANEL);
    public static final DeferredItem<BlockItem> VAULT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(VAULT_PANEL);
    public static final DeferredItem<BlockItem> SECTIONAL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(SECTIONAL_PANEL);
    public static final DeferredItem<BlockItem> GRILLE_PANEL_ITEM = ITEMS.registerSimpleBlockItem(GRILLE_PANEL);
    public static final DeferredItem<BlockItem> FOLDING_PANEL_ITEM = ITEMS.registerSimpleBlockItem(FOLDING_PANEL);
    public static final DeferredItem<BlockItem> LAB_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LAB_PANEL);
    public static final DeferredItem<BlockItem> GLASS_PANEL_ITEM = ITEMS.registerSimpleBlockItem(GLASS_PANEL);
    public static final DeferredItem<BlockItem> SHIELD_PANEL_ITEM = ITEMS.registerSimpleBlockItem(SHIELD_PANEL);
    public static final DeferredItem<BlockItem> COLD_PANEL_ITEM = ITEMS.registerSimpleBlockItem(COLD_PANEL);
    public static final DeferredItem<BlockItem> CAGE_PANEL_ITEM = ITEMS.registerSimpleBlockItem(CAGE_PANEL);
    public static final DeferredItem<BlockItem> SLIDE_PANEL_ITEM = ITEMS.registerSimpleBlockItem(SLIDE_PANEL);
    public static final DeferredItem<BlockItem> INDUSTRIAL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(INDUSTRIAL_PANEL);
    public static final DeferredItem<BlockItem> PERFORATED_PANEL_ITEM = ITEMS.registerSimpleBlockItem(PERFORATED_PANEL);
    public static final DeferredItem<BlockItem> SPEED_PANEL_ITEM = ITEMS.registerSimpleBlockItem(SPEED_PANEL);
    public static final DeferredItem<BlockItem> LIFT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LIFT_PANEL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DoorBlockEntity>> CONTROLLER_BE =
            BLOCK_ENTITIES.register("door_control", () -> BlockEntityType.Builder.of(DoorBlockEntity::new, CONTROLLER.get(), HANGAR_PANEL.get(), AIRLOCK_PANEL.get(), VAULT_PANEL.get(),
                    SECTIONAL_PANEL.get(), GRILLE_PANEL.get(), FOLDING_PANEL.get(), LAB_PANEL.get(), GLASS_PANEL.get(), SHIELD_PANEL.get(), COLD_PANEL.get(),
                    CAGE_PANEL.get(), SLIDE_PANEL.get(), INDUSTRIAL_PANEL.get(), PERFORATED_PANEL.get(), SPEED_PANEL.get(), LIFT_PANEL.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(DoorPanelBlock::leftClick);
    }
}
