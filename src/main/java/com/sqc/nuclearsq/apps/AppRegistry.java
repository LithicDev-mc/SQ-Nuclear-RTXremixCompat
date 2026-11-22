package com.sqc.nuclearsq.apps;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AppRegistry {
    private AppRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    private static BlockBehaviour.Properties monitor() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).sound(SoundType.METAL)
                .noOcclusion().lightLevel(s -> 4);
    }

    public static final DeferredBlock<LoginMonitorBlock> LOGIN_MONITOR = BLOCKS.register("login_monitor_sqc", () -> new LoginMonitorBlock(monitor()));
    static {
        ITEMS.registerSimpleBlockItem("login_monitor_sqc", LOGIN_MONITOR);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoginMonitorBlockEntity>> LOGIN_MONITOR_BE =
            BLOCK_ENTITIES.register("login_monitor", () -> BlockEntityType.Builder.of(LoginMonitorBlockEntity::new, LOGIN_MONITOR.get()).build(null));

    public static final DeferredBlock<AuctionMonitorBlock> AUCTION_MONITOR = BLOCKS.register("auction_monitor_sqc", () -> new AuctionMonitorBlock(monitor()));
    static {
        ITEMS.registerSimpleBlockItem("auction_monitor_sqc", AUCTION_MONITOR);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AuctionMonitorBlockEntity>> AUCTION_MONITOR_BE =
            BLOCK_ENTITIES.register("auction_monitor", () -> BlockEntityType.Builder.of(AuctionMonitorBlockEntity::new, AUCTION_MONITOR.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
