package com.sqc.nuclearsq.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.registry.HeavyAssemblyRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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

public record HeavyAssemblyRecipe(List<String> pattern, Map<String, Ingredient> key, ItemStack result, int time, int energyPerTick,
                                  List<String> hardcorePattern)
        implements Recipe<HeavyAssemblyRecipe.Grid> {

    public static final int SIZE = 5;

    public record Grid(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int i) {
            return items.get(i);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    private static DataResult<List<String>> checkPattern(List<String> rows) {
        if (rows.isEmpty() || rows.size() > SIZE) return DataResult.error(() -> "pattern: 1 bis 5 Zeilen");
        int w = rows.get(0).length();
        for (String r : rows) {
            if (r.length() != w || w < 1 || w > SIZE) return DataResult.error(() -> "pattern: alle Zeilen gleich lang, 1 bis 5 Zeichen");
        }
        return DataResult.success(rows);
    }

    private static final Codec<Map<String, Ingredient>> KEY_CODEC = Codec.unboundedMap(
            Codec.string(1, 1).validate(s -> " ".equals(s) ? DataResult.error(() -> "key: Leerzeichen ist reserviert") : DataResult.success(s)),
            Ingredient.CODEC_NONEMPTY);

    public static final MapCodec<HeavyAssemblyRecipe> CODEC = RecordCodecBuilder.<HeavyAssemblyRecipe>mapCodec(i -> i.group(
            Codec.STRING.listOf().flatXmap(HeavyAssemblyRecipe::checkPattern, HeavyAssemblyRecipe::checkPattern)
                    .fieldOf("pattern").forGetter((HeavyAssemblyRecipe r) -> r.pattern),
            KEY_CODEC.fieldOf("key").forGetter(HeavyAssemblyRecipe::key),
            ItemStack.CODEC.fieldOf("result").forGetter(HeavyAssemblyRecipe::result),
            Codec.intRange(1, 72000).optionalFieldOf("time", 200).forGetter(HeavyAssemblyRecipe::time),
            Codec.intRange(0, 100000).optionalFieldOf("energy_per_tick", 100).forGetter(HeavyAssemblyRecipe::energyPerTick),
            Codec.STRING.listOf().optionalFieldOf("hardcore_pattern", List.of()).forGetter(HeavyAssemblyRecipe::hardcorePattern)
    ).apply(i, HeavyAssemblyRecipe::new)).validate(HeavyAssemblyRecipe::checkKeys);

    private static DataResult<HeavyAssemblyRecipe> checkKeys(HeavyAssemblyRecipe r) {
        if (!r.hardcorePattern.isEmpty() && checkPattern(r.hardcorePattern).error().isPresent()) return DataResult.error(() -> "hardcore_pattern: ungueltig");
        for (String row : r.hardcorePattern.isEmpty() ? r.pattern : concat(r.pattern, r.hardcorePattern)) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !r.key.containsKey(String.valueOf(c))) return DataResult.error(() -> "key: Zeichen '" + c + "' fehlt");
            }
        }
        return DataResult.success(r);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HeavyAssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), (HeavyAssemblyRecipe r) -> r.pattern,
            ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, Ingredient.CONTENTS_STREAM_CODEC), HeavyAssemblyRecipe::key,
            ItemStack.STREAM_CODEC, HeavyAssemblyRecipe::result,
            ByteBufCodecs.VAR_INT, HeavyAssemblyRecipe::time,
            ByteBufCodecs.VAR_INT, HeavyAssemblyRecipe::energyPerTick,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), HeavyAssemblyRecipe::hardcorePattern,
            HeavyAssemblyRecipe::new);

    @Override
    public List<String> pattern() {
        return hardcorePattern.isEmpty() || !NuclearConfig.hardcoreCosts() ? pattern : hardcorePattern;
    }

    public int width() { return pattern().get(0).length(); }
    public int height() { return pattern().size(); }

    public int[] offset(Grid grid) {
        for (int dy = 0; dy <= SIZE - height(); dy++) {
            for (int dx = 0; dx <= SIZE - width(); dx++) {
                if (matchesAt(grid, dx, dy)) return new int[] {dx, dy};
            }
        }
        return null;
    }

    private boolean matchesAt(Grid grid, int dx, int dy) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                ItemStack s = grid.getItem(y * SIZE + x);
                int px = x - dx, py = y - dy;
                char c = px >= 0 && py >= 0 && px < width() && py < height() ? pattern().get(py).charAt(px) : ' ';
                if (c == ' ') {
                    if (!s.isEmpty()) return false;
                } else if (s.isEmpty() || !key.get(String.valueOf(c)).test(s)) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<Integer> usedSlots(int[] offset) {
        List<Integer> out = new ArrayList<>();
        for (int py = 0; py < height(); py++) {
            for (int px = 0; px < width(); px++) {
                if (pattern().get(py).charAt(px) != ' ') out.add((py + offset[1]) * SIZE + px + offset[0]);
            }
        }
        return out;
    }

    @Override
    public boolean matches(Grid input, Level level) {
        return input.size() == SIZE * SIZE && offset(input) != null;
    }

    @Override
    public ItemStack assemble(Grid input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= width() && height >= height();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        out.addAll(key.values());
        return out;
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
        return HeavyAssemblyRegistry.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return HeavyAssemblyRegistry.TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<HeavyAssemblyRecipe> {
        @Override
        public MapCodec<HeavyAssemblyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, HeavyAssemblyRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
