package com.sqc.nuclearsq.robotics.decon;

import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DeconRegistry {
    private DeconRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DeconRobotEntity>> ROBOT = ENTITIES.register("decon_robot",
            () -> EntityType.Builder.of(DeconRobotEntity::new, MobCategory.MISC).sized(1.3f, 1.75f).eyeHeight(1.6f).clientTrackingRange(10)
                    .fireImmune().build("decon_robot"));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOUND = COMPONENTS.register("decon_remote_bound",
            () -> DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<net.minecraft.core.BlockPos>>> ZONE = COMPONENTS.register("decon_remote_zone",
            () -> DataComponentType.<java.util.List<net.minecraft.core.BlockPos>>builder().persistent(net.minecraft.core.BlockPos.CODEC.listOf())
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());

    public static final DeferredItem<DeconRobotItem> ROBOT_ITEM = ITEMS.register("decon_robot_sqc",
            () -> new DeconRobotItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RemoteControlItem> REMOTE = ITEMS.register("decon_remote_sqc",
            () -> new RemoteControlItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RUBBLE = ITEMS.register("contaminated_rubble_sqc", () -> new Item(new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(DeconRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ROBOT.get(), DeconRobotEntity.attributes().build());
    }
}
