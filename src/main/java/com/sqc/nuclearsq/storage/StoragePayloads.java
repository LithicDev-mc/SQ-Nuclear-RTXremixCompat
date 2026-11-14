package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class StoragePayloads {
    private StoragePayloads() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, path);
    }

    public static final int MAX_PROTO_BYTES = 1024;
    public static final int MAX_LIST_BYTES = 700_000;

    public static int size(ItemStack stack, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static int componentSize(DataComponentType<?> type, Object value, RegistryAccess access) {
        if (type.isTransient()) return 0;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            ((StreamCodec<RegistryFriendlyByteBuf, Object>) type.streamCodec()).encode(buf, value);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    public static ItemStack trim(ItemStack stack, RegistryAccess access) {
        if (size(stack, access) <= MAX_PROTO_BYTES) return stack;
        ItemStack out = stack.copy();
        var book = out.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        if (book != null) {
            out.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                    new net.minecraft.world.item.component.WrittenBookContent(book.title(), book.author(), book.generation(), List.of(), true));
        }
        for (int i = 0; i < 64 && size(out, access) > MAX_PROTO_BYTES; i++) {
            DataComponentType<?> big = null;
            int bigSize = 0;
            for (var e : out.getComponentsPatch().entrySet()) {
                if (e.getValue().isEmpty()) continue;
                int n = componentSize(e.getKey(), e.getValue().get(), access);
                if (n > bigSize) {
                    big = e.getKey();
                    bigSize = n;
                }
            }
            if (big == null) break;
            out.remove(big);
        }
        return out;
    }

    public static List<Entry> entries(Map<ItemKey, Long> stock, Map<ItemKey, Long> held, RegistryAccess access, Map<ItemKey, ItemStack> cache) {
        if (cache.size() > 20_000) cache.clear();
        List<Entry> list = new ArrayList<>();
        long bytes = 0;
        for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
            ItemStack shown = cache.computeIfAbsent(e.getKey(), k -> trim(k.stack(1), access));
            bytes += size(shown, access) + 12;
            if (bytes > MAX_LIST_BYTES) break;
            list.add(new Entry(shown, e.getValue(), held.getOrDefault(e.getKey(), 0L)));
        }
        return list;
    }

    public static ItemKey resolve(Map<ItemKey, Long> stock, ItemStack proto, RegistryAccess access) {
        ItemKey exact = ItemKey.of(proto);
        if (exact == null || stock.containsKey(exact)) return exact;
        for (ItemKey k : stock.keySet()) {
            if (k.proto().getItem() == proto.getItem() && ItemStack.isSameItemSameComponents(trim(k.proto(), access), proto)) return k;
        }
        return exact;
    }

    public record Entry(ItemStack stack, long count, long reserved) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::stack, ByteBufCodecs.VAR_LONG, Entry::count,
                ByteBufCodecs.VAR_LONG, Entry::reserved, Entry::new);

        public Entry(ItemStack stack, long count) {
            this(stack, count, 0);
        }
    }

    public record Items(int containerId, List<Entry> entries, int racks, int online, long used, long capacity)
            implements CustomPacketPayload {
        public static final Type<Items> TYPE = new Type<>(id("storage_items"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Items> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Items::containerId, Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Items::entries,
                ByteBufCodecs.VAR_INT, Items::racks, ByteBufCodecs.VAR_INT, Items::online,
                ByteBufCodecs.VAR_LONG, Items::used, ByteBufCodecs.VAR_LONG, Items::capacity, Items::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(int containerId, ItemStack proto, int mode) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("storage_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::containerId, ItemStack.OPTIONAL_STREAM_CODEC, Action::proto,
                ByteBufCodecs.VAR_INT, Action::mode, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Craftables(int containerId, List<ItemStack> protos, int jobs, net.minecraft.network.chat.Component message, int messageId)
            implements CustomPacketPayload {
        public static final Type<Craftables> TYPE = new Type<>(id("storage_craftables"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Craftables> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Craftables::containerId, ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Craftables::protos,
                ByteBufCodecs.VAR_INT, Craftables::jobs, net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC,
                Craftables::message, ByteBufCodecs.VAR_INT, Craftables::messageId, Craftables::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Order(int containerId, ItemStack proto, int amount) implements CustomPacketPayload {
        public static final Type<Order> TYPE = new Type<>(id("storage_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Order> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Order::containerId, ItemStack.STREAM_CODEC, Order::proto, ByteBufCodecs.VAR_INT, Order::amount,
                Order::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
