package com.sqc.nuclearsq.radiation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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

public final class ProtectionRegistry {
    private ProtectionRegistry() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, NuclearSQ.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final int TIERS = 3;
    private static final int[] DURABILITY = {12, 20, 30};

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(int tier) {
        return MATERIALS.register("hazmat_" + tier, () -> {
            Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
            def.put(ArmorItem.Type.HELMET, 1);
            def.put(ArmorItem.Type.CHESTPLATE, tier + 1);
            def.put(ArmorItem.Type.LEGGINGS, tier);
            def.put(ArmorItem.Type.BOOTS, 1);
            def.put(ArmorItem.Type.BODY, tier + 1);
            return new ArmorMaterial(def, 8, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "hazmat_" + tier))), 0f, 0f);
        });
    }

    public static final List<List<DeferredItem<HazmatArmorItem>>> SUIT = new ArrayList<>();

    static {
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        for (int t = 1; t <= TIERS; t++) {
            Holder<ArmorMaterial> mat = material(t);
            int tier = t;
            List<DeferredItem<HazmatArmorItem>> set = new ArrayList<>();
            for (int k = 0; k < types.length; k++) {
                ArmorItem.Type type = types[k];
                set.add(ITEMS.register("hazmat_" + t + "_" + names[k] + "_sqc", () -> new HazmatArmorItem(mat, type, tier,
                        new Item.Properties().durability(type.getDurability(DURABILITY[tier - 1])))));
            }
            SUIT.add(set);
        }
    }

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RESPIRATOR_MATERIAL = MATERIALS.register("respirator", () -> {
        Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        def.put(ArmorItem.Type.HELMET, 1);
        def.put(ArmorItem.Type.CHESTPLATE, 0);
        def.put(ArmorItem.Type.LEGGINGS, 0);
        def.put(ArmorItem.Type.BOOTS, 0);
        def.put(ArmorItem.Type.BODY, 0);
        return new ArmorMaterial(def, 8, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "respirator"))), 0f, 0f);
    });
    public static final DeferredItem<RespiratorItem> RESPIRATOR =
            ITEMS.register("respirator_mask_sqc", () -> new RespiratorItem(RESPIRATOR_MATERIAL, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RespiratorFilterItem> RESPIRATOR_FILTER =
            ITEMS.register("respirator_filter_sqc", () -> new RespiratorFilterItem(new Item.Properties().stacksTo(16), false));
    public static final DeferredItem<RespiratorFilterItem> RESPIRATOR_FILTER_IODINE =
            ITEMS.register("respirator_filter_iodine_sqc", () -> new RespiratorFilterItem(new Item.Properties().stacksTo(16), true));

    public static final DeferredItem<DosimeterItem> DOSIMETER =
            ITEMS.register("dosimeter_sqc", () -> new DosimeterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DeconSprayerItem> SPRAYER =
            ITEMS.register("decon_sprayer_sqc", () -> new DeconSprayerItem(new Item.Properties().durability(DeconSprayerItem.CHARGES)));

    public static final DeferredBlock<DeconShowerBlock> SHOWER = BLOCKS.register("decon_shower_sqc",
            () -> new DeconShowerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> SHOWER_ITEM = ITEMS.registerSimpleBlockItem("decon_shower_sqc", SHOWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeconShowerBlockEntity>> SHOWER_BE =
            BLOCK_ENTITIES.register("decon_shower", () -> BlockEntityType.Builder.of(DeconShowerBlockEntity::new, SHOWER.get()).build(null));

    public static final DeferredBlock<ContaminationMonitorBlock> MONITOR = BLOCKS.register("contamination_monitor_sqc",
            () -> new ContaminationMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(st -> st.getValue(ContaminationMonitorBlock.ALARM) ? 7 : 3)));
    public static final DeferredItem<BlockItem> MONITOR_ITEM = ITEMS.registerSimpleBlockItem("contamination_monitor_sqc", MONITOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContaminationMonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("contamination_monitor",
                    () -> BlockEntityType.Builder.of(ContaminationMonitorBlockEntity::new, MONITOR.get()).build(null));

    public static final DeferredBlock<Block> LEAD_BLOCK = BLOCKS.register("lead_block_sqc",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .strength(5.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> LEAD_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("lead_block_sqc", LEAD_BLOCK);
    public static final DeferredBlock<Block> RADIATION_CONCRETE = BLOCKS.register("radiation_concrete_sqc",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.5f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> RADIATION_CONCRETE_ITEM = ITEMS.registerSimpleBlockItem("radiation_concrete_sqc", RADIATION_CONCRETE);
    public static final DeferredBlock<TransparentBlock> LEAD_GLASS = BLOCKS.register("lead_glass_sqc",
            () -> new TransparentBlock(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.6f, 6.0f)
                    .sound(SoundType.GLASS).noOcclusion().isValidSpawn((st, lv, pos, type) -> false)
                    .isRedstoneConductor((st, lv, pos) -> false).isSuffocating((st, lv, pos) -> false)
                    .isViewBlocking((st, lv, pos) -> false)));
    public static final DeferredItem<BlockItem> LEAD_GLASS_ITEM = ITEMS.registerSimpleBlockItem("lead_glass_sqc", LEAD_GLASS);

    public static final DeferredItem<IodineItem> IODINE =
            ITEMS.register("iodine_tablets_sqc", () -> new IodineItem(new Item.Properties().stacksTo(16)));

    public static List<Item> tabItems() {
        List<Item> out = new ArrayList<>();
        for (var set : SUIT) for (var i : set) out.add(i.get());
        out.add(RESPIRATOR.get());
        out.add(RESPIRATOR_FILTER.get());
        out.add(RESPIRATOR_FILTER_IODINE.get());
        out.add(DOSIMETER.get());
        out.add(SPRAYER.get());
        out.add(SHOWER_ITEM.get());
        out.add(MONITOR_ITEM.get());
        out.add(IODINE.get());
        out.add(LEAD_BLOCK_ITEM.get());
        out.add(RADIATION_CONCRETE_ITEM.get());
        out.add(LEAD_GLASS_ITEM.get());
        return out;
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SHOWER_BE.get(), (be, side) -> be.input());
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ProtectionRegistry::capabilities);
    }
}
