package com.sqc.nuclearsq.assembler;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record SmallPartsRecipe(List<Need> ingredients, ItemStack result, int time, int energyPerTick)
        implements Recipe<SmallPartsRecipe.Input> {

    public record Need(Ingredient ingredient, int count) {
        public static final Codec<Need> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Need::ingredient),
                Codec.intRange(1, 576).optionalFieldOf("count", 1).forGetter(Need::count)
        ).apply(i, Need::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Need> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Need::ingredient,
                ByteBufCodecs.VAR_INT, Need::count,
                Need::new);

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && ingredient.test(stack);
        }
    }

    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int i) {
            return items.get(i);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    public static final MapCodec<SmallPartsRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Need.CODEC.listOf().validate(l -> l.isEmpty() || l.size() > 9
                    ? DataResult.error(() -> "ingredients: 1 bis 9 Zutaten") : DataResult.success(l))
                    .fieldOf("ingredients").forGetter(SmallPartsRecipe::ingredients),
            ItemStack.CODEC.fieldOf("result").forGetter(SmallPartsRecipe::result),
            Codec.intRange(0, 72000).optionalFieldOf("time", 0).forGetter(SmallPartsRecipe::time),
            Codec.intRange(0, 100000).optionalFieldOf("energy_per_tick", 0).forGetter(SmallPartsRecipe::energyPerTick)
    ).apply(i, SmallPartsRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmallPartsRecipe> STREAM_CODEC = StreamCodec.composite(
            Need.STREAM_CODEC.apply(ByteBufCodecs.list()), SmallPartsRecipe::ingredients,
            ItemStack.STREAM_CODEC, SmallPartsRecipe::result,
            ByteBufCodecs.VAR_INT, SmallPartsRecipe::time,
            ByteBufCodecs.VAR_INT, SmallPartsRecipe::energyPerTick,
            SmallPartsRecipe::new);

    @javax.annotation.Nullable
    public int[][] plan(List<ItemStack> grid) {
        if (result.isEmpty()) return null;
        List<java.util.function.Predicate<ItemStack>> needs = new ArrayList<>();
        int[] counts = new int[ingredients.size()];
        for (int i = 0; i < counts.length; i++) {
            Need n = ingredients.get(i);
            needs.add(n::matches);
            counts[i] = n.count();
        }
        return FormlessMatch.plan(needs, counts, grid);
    }

    @javax.annotation.Nullable
    public static SmallPartsRecipe forItem(net.minecraft.world.item.crafting.RecipeManager recipes, net.minecraft.world.item.Item item) {
        for (var h : recipes.getAllRecipesFor(SmallPartsRegistry.TYPE.get())) {
            if (h.value().result.is(item)) return h.value();
        }
        return null;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return plan(input.items()) != null;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SmallPartsRegistry.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SmallPartsRegistry.TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<SmallPartsRecipe> {
        @Override
        public MapCodec<SmallPartsRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmallPartsRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
