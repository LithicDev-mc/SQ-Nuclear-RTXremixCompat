package com.sqc.nuclearsq.assembler;

import com.mojang.serialization.MapCodec;
import com.sqc.nuclearsq.registry.EquipmentRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record EquipmentRecipe(SmallPartsRecipe base) implements Recipe<SmallPartsRecipe.Input> {
    public static final MapCodec<EquipmentRecipe> CODEC = SmallPartsRecipe.CODEC.xmap(EquipmentRecipe::new, EquipmentRecipe::base);
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentRecipe> STREAM_CODEC =
            SmallPartsRecipe.STREAM_CODEC.map(EquipmentRecipe::new, EquipmentRecipe::base);

    @Override
    public boolean matches(SmallPartsRecipe.Input input, Level level) {
        return base.matches(input, level);
    }

    @Override
    public ItemStack assemble(SmallPartsRecipe.Input input, HolderLookup.Provider registries) {
        return base.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return base.canCraftInDimensions(width, height);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return base.getIngredients();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return base.getResultItem(registries);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EquipmentRegistry.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return EquipmentRegistry.TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<EquipmentRecipe> {
        @Override
        public MapCodec<EquipmentRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EquipmentRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
