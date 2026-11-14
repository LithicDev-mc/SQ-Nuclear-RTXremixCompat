package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FusionRegistry {
    private FusionRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final Map<String, DeferredBlock<Block>> ORE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<BlockItem>> ORE_ITEMS = new LinkedHashMap<>();
    public static final DeferredItem<Item> RAW_LITHIUM = ITEMS.registerSimpleItem("raw_lithium_sqc");
    public static final DeferredItem<Item> LITHIUM_INGOT = ITEMS.registerSimpleItem("lithium_ingot_sqc");

    static {
        for (boolean deep : new boolean[] {false, true}) {
            String id = (deep ? "deepslate_" : "") + "lithium_ore_sqc";
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                    .mapColor(deep ? MapColor.DEEPSLATE : MapColor.STONE)
                    .strength(deep ? 4.5f : 3.0f, 3.0f)
                    .sound(deep ? SoundType.DEEPSLATE : SoundType.STONE)
                    .requiresCorrectToolForDrops();
            DeferredBlock<Block> b = BLOCKS.register(id, () -> new DropExperienceBlock(ConstantInt.of(0), p));
            ORE_BLOCKS.put(id, b);
            ORE_ITEMS.put(id, ITEMS.registerSimpleBlockItem(id, b));
        }
    }

    public static List<ItemLike> tabItems() {
        List<ItemLike> out = new ArrayList<>();
        ORE_ITEMS.values().forEach(d -> out.add(d.get()));
        out.add(RAW_LITHIUM.get());
        out.add(LITHIUM_INGOT.get());
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
