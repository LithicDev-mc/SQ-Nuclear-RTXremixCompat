package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SmallPartsRegistry {
    private SmallPartsRegistry() {}

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, NuclearSQ.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<SmallPartsRecipe>> TYPE =
            RECIPE_TYPES.register("small_parts_assembly",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "small_parts_assembly")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmallPartsRecipe>> SERIALIZER =
            RECIPE_SERIALIZERS.register("small_parts_assembly", SmallPartsRecipe.Serializer::new);

    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
