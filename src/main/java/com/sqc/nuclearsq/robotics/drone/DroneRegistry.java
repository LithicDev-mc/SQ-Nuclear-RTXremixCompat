package com.sqc.nuclearsq.robotics.drone;

import java.util.List;
import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneRegistry {
    private DroneRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SurveyDroneEntity>> DRONE = ENTITIES.register("survey_drone",
            () -> EntityType.Builder.of(SurveyDroneEntity::new, MobCategory.MISC).sized(0.8f, 0.35f).clientTrackingRange(10).fireImmune().build("survey_drone"));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOUND = COMPONENTS.register("survey_tablet_drone",
            () -> DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BlockPos>>> ZONE = COMPONENTS.register("survey_tablet_zone",
            () -> DataComponentType.<List<BlockPos>>builder().persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SurveyMap>> MAP = COMPONENTS.register("survey_tablet_map",
            () -> DataComponentType.<SurveyMap>builder().persistent(SurveyMap.CODEC).networkSynchronized(SurveyMap.STREAM_CODEC).build());

    public static final DeferredItem<SurveyDroneItem> DRONE_ITEM = ITEMS.register("survey_drone_sqc", () -> new SurveyDroneItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SurveyTabletItem> TABLET = ITEMS.register("survey_tablet_sqc", () -> new SurveyTabletItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(DRONE.get(), SurveyDroneEntity.attributes().build()));
    }
}
