package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.machine.big.BigPortBlock;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.machine.big.BigShellBlock;
import com.sqc.nuclearsq.multiblock.PartBlockItem;

import java.util.EnumMap;
import java.util.Map;

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

public final class BigMachineRegistry {
    private BigMachineRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final Map<BigMachine, DeferredBlock<BigMachineControllerBlock>> CONTROLLERS = new EnumMap<>(BigMachine.class);
    public static final Map<BigMachine, DeferredItem<BlockItem>> CONTROLLER_ITEMS = new EnumMap<>(BigMachine.class);
    public static final Map<BigPortKind, DeferredBlock<BigPortBlock>> PORTS = new EnumMap<>(BigPortKind.class);
    public static final Map<BigPortKind, DeferredItem<BlockItem>> PORT_ITEMS = new EnumMap<>(BigPortKind.class);

    static {
        for (BigMachine m : BigMachine.values()) {
            DeferredBlock<BigMachineControllerBlock> b = BLOCKS.register(m.controllerId(), () -> new BigMachineControllerBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 8.0f).noOcclusion(), m));
            CONTROLLERS.put(m, b);
            CONTROLLER_ITEMS.put(m, ITEMS.<BlockItem>register(m.controllerId(), () -> new PartBlockItem(b.get(), new Item.Properties())));
        }
        for (BigPortKind k : BigPortKind.values()) {
            DeferredBlock<BigPortBlock> b = BLOCKS.register(k.blockId(), () -> new BigPortBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 12.0f), k));
            PORTS.put(k, b);
            PORT_ITEMS.put(k, ITEMS.<BlockItem>register(k.blockId(), () -> new PartBlockItem(b.get(), new Item.Properties())));
        }
    }

    public static final Map<BigMachine, DeferredBlock<BigShellBlock>> SHELLS = new EnumMap<>(BigMachine.class);

    static {
        for (BigMachine m : BigMachine.values()) {
            SHELLS.put(m, BLOCKS.register(m.id + "_shell_sqc", () -> BigShellBlock.create(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5.0f, 3600000.0f).noLootTable().noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), m)));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BigMachineBlockEntity>> CONTROLLER_BE =
            BLOCK_ENTITIES.register("big_machine_controller", () -> BlockEntityType.Builder.of(BigMachineBlockEntity::new,
                    CONTROLLERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BigPortBlockEntity>> PORT_BE =
            BLOCK_ENTITIES.register("big_machine_port", () -> BlockEntityType.Builder.of(BigPortBlockEntity::new,
                    PORTS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(BigMachineRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PORT_BE.get(), (be, side) -> {
            if (be.kind() == BigPortKind.BIG_WATER) return null;
            var c = be.findController();
            return c == null ? null : c.fluidsAt(be.portName());
        });
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PORT_BE.get(), (be, side) -> {
            var c = be.findController();
            return c == null ? null : c.energyAt(be.portName());
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PORT_BE.get(), (be, side) -> {
            var c = be.findController();
            return c == null ? null : c.itemsAt(be.portName());
        });
    }
}
