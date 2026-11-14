package com.sqc.nuclearsq.exo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ExoRegistry {
    private ExoRegistry() {}

    public static final int MAX_TIER = 5;

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register("exo", () -> {
        Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        def.put(ArmorItem.Type.HELMET, 2);
        def.put(ArmorItem.Type.CHESTPLATE, 6);
        def.put(ArmorItem.Type.LEGGINGS, 5);
        def.put(ArmorItem.Type.BOOTS, 2);
        def.put(ArmorItem.Type.BODY, 5);
        return new ArmorMaterial(def, 9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "exo"))), 0f, 0f);
    });

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIER = COMPONENTS.register("exo_tier",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(1, MAX_TIER)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY = COMPONENTS.register("exo_battery",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 1000)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ModuleEntry>>> MODULES = COMPONENTS.register("exo_modules",
            () -> DataComponentType.<List<ModuleEntry>>builder().persistent(ModuleEntry.CODEC.listOf())
                    .networkSynchronized(ModuleEntry.STREAM_CODEC.apply(ByteBufCodecs.list())).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACTIVE = COMPONENTS.register("exo_active",
            () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    public static final List<DeferredItem<ExoArmorItem>> ARMOR = new ArrayList<>();

    static {
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        for (int k = 0; k < types.length; k++) {
            ArmorItem.Type type = types[k];
            ARMOR.add(ITEMS.register("exo_" + names[k] + "_sqc",
                    () -> new ExoArmorItem(MATERIAL, type, new Item.Properties().durability(type.getDurability(15)))));
        }
    }

    public static final List<DeferredItem<ExoModule>> MODULE_ITEMS = new ArrayList<>();

    static {
        for (ExoModule.Kind k : ExoModule.Kind.values()) {
            for (int l = 1; l <= k.maxLevel; l++) {
                int lv = l;
                MODULE_ITEMS.add(ITEMS.register("exo_module_" + k.id + "_" + l + "_sqc", () -> new ExoModule(new Item.Properties(), k, lv)));
            }
        }
    }

    public static final DeferredItem<ExoCoreItem> CORE = ITEMS.register("exo_core_sqc",
            () -> new ExoCoreItem(new Item.Properties().component(TIER.get(), 1)));

    public static final DeferredHolder<MenuType<?>, MenuType<ExoStationMenu>> STATION_MENU = MENUS.register("exo_station",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new ExoStationMenu(id, inv, buf.readBlockPos())));

    public static boolean isPiece(ItemStack stack) {
        return ARMOR.stream().anyMatch(d -> stack.is(d.get()));
    }

    public static List<Item> tabItems() {
        List<Item> out = new ArrayList<>();
        ARMOR.forEach(i -> out.add(i.get()));
        out.add(CORE.get());
        MODULE_ITEMS.forEach(i -> out.add(i.get()));
        return out;
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> ExoCoreItem.storage(stack), CORE.get());
        });
    }
}
