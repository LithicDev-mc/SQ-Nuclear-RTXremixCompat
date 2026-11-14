package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.explosion.AshLayerBlock;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.multiblock.PartBlockItem;
import com.sqc.nuclearsq.turbine.TurbinePartBlock;
import com.sqc.nuclearsq.radiation.FalloutBlock;
import com.sqc.nuclearsq.radiation.GeigerCounterItem;
import com.sqc.nuclearsq.radiation.MedicineItem;
import com.sqc.nuclearsq.radiation.RadiationManager;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CatalogRegistry {
    private CatalogRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final Map<String, DeferredBlock<Block>> BLOCK_MAP = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEM_MAP = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> ITEM_MAP = new LinkedHashMap<>();

    static {
        for (String id : NuclearCatalog.BLOCKS) {
            DeferredBlock<Block> block = BLOCKS.register(id, () -> create(id));
            BLOCK_MAP.put(id, block);
            BLOCK_ITEM_MAP.put(id, PartBlockItem.CATALOG.contains(id)
                    ? ITEMS.<BlockItem>register(id, () -> new PartBlockItem(block.get(), new Item.Properties()))
                    : ITEMS.registerSimpleBlockItem(id, block));
        }
        for (String id : NuclearCatalog.ITEMS) {
            ITEM_MAP.put(id, ITEMS.registerSimpleItem(id));
        }
    }

    public static final DeferredBlock<Block> FALLOUT = BLOCKS.register("radioactive_fallout_sqc",
            () -> new FalloutBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(0.1f).sound(SoundType.GRAVEL).noOcclusion().noCollission()));
    public static final DeferredItem<BlockItem> FALLOUT_ITEM = ITEMS.registerSimpleBlockItem("radioactive_fallout_sqc", FALLOUT);

    public static final DeferredBlock<Block> ASH = BLOCKS.register("nuclear_ash_sqc",
            () -> new AshLayerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(0.1f).sound(SoundType.SAND).noOcclusion().noCollission().replaceable()));
    public static final DeferredItem<BlockItem> ASH_ITEM = ITEMS.registerSimpleBlockItem("nuclear_ash_sqc", ASH);

    public static final DeferredItem<GeigerCounterItem> GEIGER_COUNTER =
            ITEMS.register("geiger_counter_sqc", () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.sqc.nuclearsq.data.NetScannerItem> NET_SCANNER =
            ITEMS.register("net_scanner_sqc", () -> new com.sqc.nuclearsq.data.NetScannerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<MedicineItem> ANTI_RAD =
            ITEMS.register("anti_rad_medicine_sqc", () -> new MedicineItem(new Item.Properties().stacksTo(16)));

    private static boolean partialModel(String id) {
        return id.startsWith("pump_") || id.startsWith("fuel_rod_") || id.startsWith("translator_") || id.startsWith("moderator_");
    }

    private static Block create(String id) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(5.0f, 12.0f);
        if (partialModel(id)) props.noOcclusion();
        return hidesWhenFormed(NuclearSQ.MODID + ":" + id) ? new TurbinePartBlock(props) : new Block(props);
    }

    private static boolean hidesWhenFormed(String id) {
        if (MultiblockStructure.TURBINE.blockIds().contains(id)) return true;
        for (com.sqc.nuclearsq.machine.big.BigMachine m : com.sqc.nuclearsq.machine.big.BigMachine.values()) {
            if (m.structure.blockIds().contains(id)) return true;
        }
        return false;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
