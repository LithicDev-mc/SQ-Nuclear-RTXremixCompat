package com.sqc.nuclearsq.robotics;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
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

public final class RoboticsRegistry {
    private RoboticsRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.5f, 12.0f).sound(SoundType.METAL).noOcclusion();
    }

    public static final DeferredBlock<RobotArmBlock> ARM = BLOCKS.register("robot_arm_sqc", () -> new RobotArmBlock(props(), 1));
    public static final DeferredBlock<RobotArmBlock> HEAVY_ARM = BLOCKS.register("heavy_robot_arm_sqc", () -> new RobotArmBlock(props(), 2));
    public static final DeferredItem<BlockItem> ARM_ITEM = ITEMS.registerSimpleBlockItem("robot_arm_sqc", ARM);
    public static final DeferredItem<BlockItem> HEAVY_ARM_ITEM = ITEMS.registerSimpleBlockItem("heavy_robot_arm_sqc", HEAVY_ARM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RobotArmBlockEntity>> ARM_BE = BLOCK_ENTITIES.register("robot_arm",
            () -> BlockEntityType.Builder.of(RobotArmBlockEntity::new, ARM.get(), HEAVY_ARM.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<RobotArmMenu>> ARM_MENU = MENUS.register("robot_arm",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new RobotArmMenu(id, inv, buf.readBlockPos())));

    public static final DeferredBlock<RodGripperBlock> ROD_GRIPPER = BLOCKS.register("rod_gripper_sqc", () -> new RodGripperBlock(props()));
    public static final DeferredItem<BlockItem> ROD_GRIPPER_ITEM = ITEMS.registerSimpleBlockItem("rod_gripper_sqc", ROD_GRIPPER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RodGripperBlockEntity>> GRIPPER_BE = BLOCK_ENTITIES.register("rod_gripper",
            () -> BlockEntityType.Builder.of(RodGripperBlockEntity::new, ROD_GRIPPER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<RodGripperMenu>> GRIPPER_MENU = MENUS.register("rod_gripper",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new RodGripperMenu(id, inv, buf.readBlockPos())));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> LINK_ARM = COMPONENTS.register("link_arm",
            () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> LINK_PENDING = COMPONENTS.register("link_pending",
            () -> DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG).build());

    public static final DeferredItem<Item> ARM_UPGRADE = ITEMS.register("robot_arm_upgrade_sqc",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<LinkToolItem> LINK_TOOL = ITEMS.register("link_tool_sqc",
            () -> new LinkToolItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(RoboticsRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ARM_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GRIPPER_BE.get(), (be, side) -> be.energy());
    }
}
