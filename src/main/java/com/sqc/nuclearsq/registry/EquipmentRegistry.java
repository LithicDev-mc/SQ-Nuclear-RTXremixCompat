package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EquipmentRegistry {
    private EquipmentRegistry() {}

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, NuclearSQ.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<EquipmentRecipe>> TYPE =
            RECIPE_TYPES.register("equipment_assembly",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "equipment_assembly")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EquipmentRecipe>> SERIALIZER =
            RECIPE_SERIALIZERS.register("equipment_assembly", EquipmentRecipe.Serializer::new);

    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
