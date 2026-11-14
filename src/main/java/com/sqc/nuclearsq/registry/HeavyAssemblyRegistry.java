package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.machine.big.BigCrafterMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HeavyAssemblyRegistry {
    private HeavyAssemblyRegistry() {}

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, NuclearSQ.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<HeavyAssemblyRecipe>> TYPE =
            RECIPE_TYPES.register("heavy_assembly",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "heavy_assembly")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeavyAssemblyRecipe>> SERIALIZER =
            RECIPE_SERIALIZERS.register("heavy_assembly", HeavyAssemblyRecipe.Serializer::new);

    public static final DeferredHolder<MenuType<?>, MenuType<BigCrafterMenu>> CRAFTER_MENU =
            MENUS.register("big_crafter", () -> IMenuTypeExtension.create(BigCrafterMenu::new));

    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        MENUS.register(modBus);
    }
}
