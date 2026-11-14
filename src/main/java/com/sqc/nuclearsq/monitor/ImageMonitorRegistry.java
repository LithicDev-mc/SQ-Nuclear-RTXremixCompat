package com.sqc.nuclearsq.monitor;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ImageMonitorRegistry {
    private ImageMonitorRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NuclearSQ.MODID);

    public static final DeferredBlock<ImageMonitorBlock> MONITOR = BLOCKS.register("image_monitor_sqc",
            () -> new ImageMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    static {
        ITEMS.registerSimpleBlockItem("image_monitor_sqc", MONITOR);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImageMonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("image_monitor", () -> BlockEntityType.Builder.of(ImageMonitorBlockEntity::new, MONITOR.get()).build(null));

    public static final DeferredHolder<SoundEvent, SoundEvent> MONITOR_TOUCH = sound("monitor_touch");
    public static final DeferredHolder<SoundEvent, SoundEvent> MONITOR_ON = sound("monitor_on");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        var container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.SERVER, ImageMonitorConfig.SERVER_SPEC, "nuclearsq-monitor.toml");
        container.registerConfig(ModConfig.Type.CLIENT, ImageMonitorConfig.CLIENT_SPEC, "nuclearsq-monitor-client.toml");
        NeoForge.EVENT_BUS.addListener(ImageMonitorPayload::onChunkSent);
    }
}
