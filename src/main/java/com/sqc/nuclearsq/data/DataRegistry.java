package com.sqc.nuclearsq.data;

import java.util.List;
import java.util.function.Function;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DataRegistry {
    private DataRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);

    private static BlockBehaviour.Properties device() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).sound(SoundType.METAL)
                .noOcclusion();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = BLOCKS.register(id, () -> f.apply(p));
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final DeferredBlock<DataCableBlock> CABLE = block("data_cable_sqc", DataCableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5f).sound(SoundType.WOOL).noOcclusion().dynamicShape());
    public static final DeferredBlock<DataTerminalBlock> TERMINAL = block("data_terminal_sqc", DataTerminalBlock::new, device());
    public static final DeferredBlock<DataConsoleBlock> CONSOLE = block("data_console_sqc", DataConsoleBlock::new, device());
    public static final DeferredBlock<LogbookBlock> LOGBOOK = block("data_logbook_sqc", LogbookBlock::new, device());
    public static final DeferredBlock<PowerMonitorBlock> POWER = block("data_power_sqc", PowerMonitorBlock::new, device());
    public static final DeferredBlock<DataInstrumentBlock> GAUGE = block("data_gauge_sqc",
            p -> new DataInstrumentBlock(p, DataInstrumentBlock.GAUGE), device());
    public static final DeferredBlock<DataInstrumentBlock> BAR = block("data_bar_sqc",
            p -> new DataInstrumentBlock(p, DataInstrumentBlock.BAR), device());
    public static final DeferredBlock<DataInstrumentBlock> DISPLAY = block("data_display_sqc",
            p -> new DataInstrumentBlock(p, DataInstrumentBlock.DISPLAY), device());
    public static final DeferredBlock<DataMultiBlock> MULTI = block("data_multi_sqc", DataMultiBlock::new, device());
    public static final DeferredBlock<DataControlBlock> BUTTON = block("data_button_sqc",
            p -> new DataControlBlock(p, DataControlBlock.BUTTON), device());
    public static final DeferredBlock<DataControlBlock> LEVER = block("data_lever_sqc",
            p -> new DataControlBlock(p, DataControlBlock.LEVER), device());
    public static final DeferredBlock<DataControlBlock> SLIDER = block("data_slider_sqc",
            p -> new DataControlBlock(p, DataControlBlock.SLIDER), device());
    public static final DeferredBlock<ControlWallBlock> WALL = block("control_wall_sqc", ControlWallBlock::new,
            device().lightLevel(s -> 6));
    public static final DeferredBlock<AreaMonitorBlock> AREA_MONITOR = block("area_monitor_sqc", AreaMonitorBlock::new,
            device().lightLevel(s -> s.getValue(AreaMonitorBlock.ALARM) ? 7 : 0));
    public static final DeferredBlock<AreaSensorBlock> AREA_SENSOR = block("area_sensor_sqc", AreaSensorBlock::new,
            device().lightLevel(s -> s.getValue(AreaMonitorBlock.ALARM) ? 7 : 0));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataInstrumentBlockEntity>> INSTRUMENT_BE =
            BLOCK_ENTITIES.register("data_instrument", () -> BlockEntityType.Builder.of(DataInstrumentBlockEntity::new,
                    GAUGE.get(), BAR.get(), DISPLAY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataMultiBlockEntity>> MULTI_BE =
            BLOCK_ENTITIES.register("data_multi", () -> BlockEntityType.Builder.of(DataMultiBlockEntity::new, MULTI.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogbookBlockEntity>> LOGBOOK_BE =
            BLOCK_ENTITIES.register("data_logbook", () -> BlockEntityType.Builder.of(LogbookBlockEntity::new, LOGBOOK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerMonitorBlockEntity>> POWER_BE =
            BLOCK_ENTITIES.register("data_power", () -> BlockEntityType.Builder.of(PowerMonitorBlockEntity::new, POWER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataControlBlockEntity>> CONTROL_BE =
            BLOCK_ENTITIES.register("data_control", () -> BlockEntityType.Builder.of(DataControlBlockEntity::new,
                    BUTTON.get(), LEVER.get(), SLIDER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlWallBlockEntity>> WALL_BE =
            BLOCK_ENTITIES.register("control_wall", () -> BlockEntityType.Builder.of(ControlWallBlockEntity::new, WALL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AreaMonitorBlockEntity>> AREA_MONITOR_BE =
            BLOCK_ENTITIES.register("area_monitor", () -> BlockEntityType.Builder.of(AreaMonitorBlockEntity::new, AREA_MONITOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AreaSensorBlockEntity>> AREA_SENSOR_BE =
            BLOCK_ENTITIES.register("area_sensor", () -> BlockEntityType.Builder.of(AreaSensorBlockEntity::new, AREA_SENSOR.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<DataTerminalMenu>> TERMINAL_MENU =
            MENUS.register("data_terminal", () -> IMenuTypeExtension.create(DataTerminalMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<DataConsoleMenu>> CONSOLE_MENU =
            MENUS.register("data_console", () -> IMenuTypeExtension.create(DataConsoleMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<LogbookMenu>> LOGBOOK_MENU =
            MENUS.register("data_logbook", () -> IMenuTypeExtension.create(LogbookMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PowerMonitorMenu>> POWER_MENU =
            MENUS.register("data_power", () -> IMenuTypeExtension.create(PowerMonitorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<DataMultiMenu>> MULTI_MENU =
            MENUS.register("data_multi", () -> IMenuTypeExtension.create(DataMultiMenu::new));

    public static List<DeferredBlock<? extends Block>> blocks() {
        return List.of(CABLE, TERMINAL, CONSOLE, LOGBOOK, POWER, GAUGE, BAR, DISPLAY, MULTI, BUTTON, LEVER, SLIDER, WALL, AREA_MONITOR, AREA_SENSOR);
    }

    public static BlockItem item(DeferredBlock<? extends Block> b) {
        return (BlockItem) b.get().asItem();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
