package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ReprocessingRegistry {
    private ReprocessingRegistry() {}

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, NuclearSQ.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, NuclearSQ.MODID);

    public static final java.util.Map<ReprocessingRecipe.Kind, DeferredHolder<RecipeType<?>, RecipeType<ReprocessingRecipe>>> TYPES =
            new java.util.EnumMap<>(ReprocessingRecipe.Kind.class);
    public static final java.util.Map<ReprocessingRecipe.Kind, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ReprocessingRecipe>>> SERIALIZERS =
            new java.util.EnumMap<>(ReprocessingRecipe.Kind.class);

    static {
        for (ReprocessingRecipe.Kind kind : ReprocessingRecipe.Kind.values()) {
            TYPES.put(kind, RECIPE_TYPES.register(kind.id,
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, kind.id))));
            SERIALIZERS.put(kind, RECIPE_SERIALIZERS.register(kind.id, () -> new ReprocessingRecipe.Serializer(kind)));
        }
    }

    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
