package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record Pattern(List<Part> inputs, Part output, List<Part> remainders) {
    public record Part(ItemStack item, int count) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(Part::item),
                Codec.intRange(1, 4096).fieldOf("count").forGetter(Part::count)).apply(i, Part::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Part::item, ByteBufCodecs.VAR_INT, Part::count, Part::new);

        public Part {
            item = item.copyWithCount(1);
        }

        public ItemKey key() {
            return ItemKey.of(item);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Part p && p.count == count && ItemStack.isSameItemSameComponents(p.item, item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ItemStack.hashItemAndComponents(item), count);
        }
    }

    public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
            Part.CODEC.listOf().fieldOf("inputs").forGetter(Pattern::inputs),
            Part.CODEC.fieldOf("output").forGetter(Pattern::output),
            Part.CODEC.listOf().optionalFieldOf("remainders", List.of()).forGetter(Pattern::remainders)).apply(i, Pattern::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Pattern> STREAM_CODEC = StreamCodec.composite(
            Part.STREAM_CODEC.apply(ByteBufCodecs.list()), Pattern::inputs, Part.STREAM_CODEC, Pattern::output,
            Part.STREAM_CODEC.apply(ByteBufCodecs.list()), Pattern::remainders, Pattern::new);

    public Pattern {
        inputs = List.copyOf(inputs);
        remainders = List.copyOf(remainders);
    }

    public static List<Part> merge(List<ItemStack> stacks) {
        List<Part> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            boolean done = false;
            for (int k = 0; k < out.size() && !done; k++) {
                Part p = out.get(k);
                if (ItemStack.isSameItemSameComponents(p.item, s)) {
                    out.set(k, new Part(p.item, p.count + s.getCount()));
                    done = true;
                }
            }
            if (!done) out.add(new Part(s, s.getCount()));
        }
        return out;
    }

    public ItemKey outputKey() {
        return output.key();
    }
}
