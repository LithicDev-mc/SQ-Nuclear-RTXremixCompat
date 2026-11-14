package com.sqc.nuclearsq.assembler;

import java.util.Objects;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodBlockItem;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

public final class FuelChargeIngredient implements ICustomIngredient {
    public static final MapCodec<FuelChargeIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(FuelChargeIngredient::item),
            Codec.BOOL.fieldOf("full").forGetter(FuelChargeIngredient::full)
    ).apply(i, FuelChargeIngredient::new));

    public static final IngredientType<FuelChargeIngredient> TYPE = new IngredientType<>(CODEC);

    private final Item item;
    private final boolean full;

    public FuelChargeIngredient(Item item, boolean full) {
        this.item = item;
        this.full = full;
    }

    public Item item() { return item; }
    public boolean full() { return full; }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(item)) return false;
        return (FuelRodBlockItem.charge(stack) >= FuelRodBlockEntity.UNIT) == full;
    }

    @Override
    public Stream<ItemStack> getItems() {
        ItemStack stack = new ItemStack(item);
        if (!full) stack.set(ModDataComponents.FUEL_CHARGE.get(), 0);
        return Stream.of(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return AssemblyRegistry.FUEL_CHARGE_INGREDIENT.get();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FuelChargeIngredient other && item == other.item && full == other.full;
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, full);
    }
}
