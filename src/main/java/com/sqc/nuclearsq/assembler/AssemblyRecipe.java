package com.sqc.nuclearsq.assembler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.registry.AssemblyRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record AssemblyRecipe(List<Need> ingredients, ItemStack result, int time, int energyPerTick)
        implements Recipe<AssemblyRecipe.Input> {

    public static final TagKey<Item> REACTOR_PARTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "reactor_parts"));

    public record Need(Ingredient ingredient, int count) implements java.util.function.Predicate<ItemStack> {
        public static final Codec<Need> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Need::ingredient),
                Codec.intRange(1, 576).optionalFieldOf("count", 1).forGetter(Need::count)
        ).apply(i, Need::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Need> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Need::ingredient,
                ByteBufCodecs.VAR_INT, Need::count,
                Need::new);

        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && ingredient.test(stack);
        }

        public ItemStack display(long ms) {
            ItemStack[] all = ingredient.getItems();
            if (all.length == 0) return new ItemStack(net.minecraft.world.item.Items.BARRIER);
            return all[(int) ((ms / 1000) % all.length)].copyWithCount(1);
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

    public static final MapCodec<AssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Need.CODEC.listOf().validate(l -> l.isEmpty() || l.size() > 9
                    ? DataResult.error(() -> "ingredients: 1 bis 9 Zutaten") : DataResult.success(l))
                    .fieldOf("ingredients").forGetter(AssemblyRecipe::ingredients),
            ItemStack.CODEC.fieldOf("result").forGetter(AssemblyRecipe::result),
            Codec.intRange(0, 72000).optionalFieldOf("time", 0).forGetter(AssemblyRecipe::time),
            Codec.intRange(0, 100000).optionalFieldOf("energy_per_tick", 0).forGetter(AssemblyRecipe::energyPerTick)
    ).apply(i, AssemblyRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            Need.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblyRecipe::ingredients,
            ItemStack.STREAM_CODEC, AssemblyRecipe::result,
            ByteBufCodecs.VAR_INT, AssemblyRecipe::time,
            ByteBufCodecs.VAR_INT, AssemblyRecipe::energyPerTick,
            AssemblyRecipe::new);

    private int[] counts() {
        int[] c = new int[ingredients.size()];
        for (int i = 0; i < c.length; i++) c[i] = ingredients.get(i).count();
        return c;
    }

    @Nullable
    public int[][] plan(List<ItemStack> grid) {
        if (result.isEmpty()) return null;
        return FormlessMatch.plan(ingredients, counts(), grid);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return plan(input.items()) != null;
    }

    public int weight() {
        int sum = 0;
        for (Need n : ingredients) sum += n.count();
        return sum;
    }

    public static final Comparator<AssemblyRecipe> SPECIFIC_FIRST =
            Comparator.comparingInt((AssemblyRecipe r) -> r.ingredients.size()).thenComparingInt(AssemblyRecipe::weight).reversed();

    public static List<RecipeHolder<AssemblyRecipe>> all(RecipeManager recipes) {
        return recipes.getAllRecipesFor(AssemblyRegistry.TYPE.get());
    }

    @Nullable
    public static AssemblyRecipe find(RecipeManager recipes, List<ItemStack> grid) {
        AssemblyRecipe best = null;
        for (RecipeHolder<AssemblyRecipe> h : all(recipes)) {
            AssemblyRecipe r = h.value();
            if ((best == null || SPECIFIC_FIRST.compare(r, best) < 0) && r.plan(grid) != null) best = r;
        }
        return best;
    }

    @Nullable
    public static AssemblyRecipe forItem(RecipeManager recipes, Item item) {
        for (RecipeHolder<AssemblyRecipe> h : all(recipes)) {
            if (h.value().result.is(item)) return h.value();
        }
        return null;
    }

    public static List<ItemStack> results(RecipeManager recipes) {
        List<ItemStack> out = new ArrayList<>();
        for (RecipeHolder<AssemblyRecipe> h : all(recipes)) out.add(h.value().result);
        return out;
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
        return AssemblyRegistry.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AssemblyRegistry.TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<AssemblyRecipe> {
        @Override
        public MapCodec<AssemblyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
