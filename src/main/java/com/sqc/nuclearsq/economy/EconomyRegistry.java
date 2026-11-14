package com.sqc.nuclearsq.economy;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
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

public final class EconomyRegistry {
    private EconomyRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties device() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).sound(SoundType.METAL);
    }

    public static final DeferredBlock<BankServerBlock> SERVER = BLOCKS.register("bank_server_sqc", () -> new BankServerBlock(device()));
    public static final DeferredBlock<BankTerminalBlock> TERMINAL = BLOCKS.register("bank_terminal_sqc", () -> new BankTerminalBlock(device().noOcclusion()));
    public static final DeferredBlock<BankModemBlock> MODEM = BLOCKS.register("bank_modem_sqc", () -> new BankModemBlock(device()));
    static {
        ITEMS.registerSimpleBlockItem("bank_server_sqc", SERVER);
        ITEMS.registerSimpleBlockItem("bank_terminal_sqc", TERMINAL);
        ITEMS.registerSimpleBlockItem("bank_modem_sqc", MODEM);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankServerBlockEntity>> SERVER_BE =
            BLOCK_ENTITIES.register("bank_server", () -> BlockEntityType.Builder.of(BankServerBlockEntity::new, SERVER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankTerminalBlockEntity>> TERMINAL_BE =
            BLOCK_ENTITIES.register("bank_terminal", () -> BlockEntityType.Builder.of(BankTerminalBlockEntity::new, TERMINAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BankModemBlockEntity>> MODEM_BE =
            BLOCK_ENTITIES.register("bank_modem", () -> BlockEntityType.Builder.of(BankModemBlockEntity::new, MODEM.get()).build(null));

    public static final DeferredItem<Item> COIN = ITEMS.registerSimpleItem("coin_sqc");
    public static final DeferredItem<Item> COIN_ROLL = ITEMS.registerSimpleItem("coin_roll_sqc");
    public static final DeferredItem<Item> COIN_BAR = ITEMS.registerSimpleItem("coin_bar_sqc");

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
