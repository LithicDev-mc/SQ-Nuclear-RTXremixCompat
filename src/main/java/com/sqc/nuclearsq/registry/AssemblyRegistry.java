package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.OptionalIngredient;
import com.sqc.nuclearsq.explosion.ReactorDetonatorBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AssemblyRegistry {
    private AssemblyRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, NuclearSQ.MODID);
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, NuclearSQ.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> TYPE =
            RECIPE_TYPES.register("assembly", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "assembly")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyRecipe>> SERIALIZER =
            RECIPE_SERIALIZERS.register("assembly", AssemblyRecipe.Serializer::new);
    public static final DeferredHolder<IngredientType<?>, IngredientType<OptionalIngredient>> OPTIONAL_INGREDIENT =
            INGREDIENT_TYPES.register("optional", () -> OptionalIngredient.TYPE);
    public static final DeferredHolder<IngredientType<?>, IngredientType<com.sqc.nuclearsq.assembler.FuelChargeIngredient>> FUEL_CHARGE_INGREDIENT =
            INGREDIENT_TYPES.register("fuel_charge", () -> com.sqc.nuclearsq.assembler.FuelChargeIngredient.TYPE);

    public static final DeferredItem<net.minecraft.world.item.Item> ASSEMBLER_ITEM =
            DeferredItem.createItem(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "reactor_assembler_kit_sqc"));

    public static final DeferredBlock<Block> DETONATOR = BLOCKS.register("reactor_detonator_sqc",
            () -> new ReactorDetonatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f, 6.0f)));
    public static final DeferredItem<BlockItem> DETONATOR_ITEM = ITEMS.registerSimpleBlockItem("reactor_detonator_sqc", DETONATOR);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        INGREDIENT_TYPES.register(modBus);
    }
}
