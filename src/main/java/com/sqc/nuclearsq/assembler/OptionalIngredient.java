package com.sqc.nuclearsq.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.registry.AssemblyRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

public final class OptionalIngredient implements ICustomIngredient {
    public static final MapCodec<OptionalIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(OptionalIngredient::items),
            TagKey.codec(Registries.ITEM).listOf().optionalFieldOf("tags", List.of()).forGetter(OptionalIngredient::tags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("fallback", List.of()).forGetter(OptionalIngredient::fallback)
    ).apply(i, OptionalIngredient::new));

    public static final IngredientType<OptionalIngredient> TYPE = new IngredientType<>(CODEC);

    private final List<ResourceLocation> items;
    private final List<TagKey<Item>> tags;
    private final List<ResourceLocation> fallback;
    private volatile List<Item> resolved;

    public OptionalIngredient(List<ResourceLocation> items, List<TagKey<Item>> tags, List<ResourceLocation> fallback) {
        this.items = List.copyOf(items);
        this.tags = List.copyOf(tags);
        this.fallback = List.copyOf(fallback);
    }

    public List<ResourceLocation> items() { return items; }
    public List<TagKey<Item>> tags() { return tags; }
    public List<ResourceLocation> fallback() { return fallback; }

    private List<Item> resolved() {
        List<Item> r = resolved;
        if (r == null) {
            r = existing(items);
            if (r.isEmpty()) r = existing(fallback);
            resolved = r;
        }
        return r;
    }

    private static List<Item> existing(List<ResourceLocation> ids) {
        List<Item> out = new ArrayList<>();
        for (ResourceLocation id : ids) BuiltInRegistries.ITEM.getOptional(id).ifPresent(out::add);
        return List.copyOf(out);
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Item it : resolved()) if (stack.is(it)) return true;
        for (TagKey<Item> tag : tags) if (stack.is(tag)) return true;
        return false;
    }

    @Override
    public Stream<ItemStack> getItems() {
        Stream<ItemStack> direct = resolved().stream().map(ItemStack::new);
        Stream<ItemStack> tagged = tags.stream().flatMap(t -> BuiltInRegistries.ITEM.getTag(t).stream()
                .flatMap(set -> set.stream().map(h -> new ItemStack(h.value()))));
        return Stream.concat(direct, tagged);
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public IngredientType<?> getType() {
        return AssemblyRegistry.OPTIONAL_INGREDIENT.get();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OptionalIngredient other && items.equals(other.items) && tags.equals(other.tags) && fallback.equals(other.fallback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, tags, fallback);
    }
}
