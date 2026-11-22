package com.sqc.nuclearsq.apps;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class AuctionPayloads {
    private AuctionPayloads() {}

    public static final int OPEN = 0, CREATE = 1, BID = 2, BUYOUT = 3, CANCEL = 4, TAKE = 5;

    public record Action(BlockPos pos, int op, int id, long a, long b, int c) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "app_auction_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos, ByteBufCodecs.VAR_INT, Action::op, ByteBufCodecs.VAR_INT, Action::id,
                ByteBufCodecs.VAR_LONG, Action::a, ByteBufCodecs.VAR_LONG, Action::b, ByteBufCodecs.VAR_INT, Action::c, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Entry(int id, ItemStack stack, String seller, long price, long minBid, long buyout, int bids, long left, boolean mine,
                        boolean leading) {
        static void write(RegistryFriendlyByteBuf buf, Entry e) {
            ByteBufCodecs.VAR_INT.encode(buf, e.id);
            ItemStack.STREAM_CODEC.encode(buf, e.stack);
            ByteBufCodecs.stringUtf8(32).encode(buf, e.seller);
            ByteBufCodecs.VAR_LONG.encode(buf, e.price);
            ByteBufCodecs.VAR_LONG.encode(buf, e.minBid);
            ByteBufCodecs.VAR_LONG.encode(buf, e.buyout);
            ByteBufCodecs.VAR_INT.encode(buf, e.bids);
            ByteBufCodecs.VAR_LONG.encode(buf, e.left);
            buf.writeBoolean(e.mine);
            buf.writeBoolean(e.leading);
        }

        static Entry read(RegistryFriendlyByteBuf buf) {
            return new Entry(ByteBufCodecs.VAR_INT.decode(buf), ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), buf.readBoolean(), buf.readBoolean());
        }
    }

    public record State(BlockPos pos, boolean loggedIn, int link, long balance, List<Entry> entries, List<ItemStack> pickups, int elsewhere,
                        String message, boolean ok) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "app_auction_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public State decode(RegistryFriendlyByteBuf buf) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                boolean in = buf.readBoolean();
                int link = ByteBufCodecs.VAR_INT.decode(buf);
                long balance = ByteBufCodecs.VAR_LONG.decode(buf);
                int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 600);
                List<Entry> entries = new ArrayList<>();
                for (int i = 0; i < n; i++) entries.add(Entry.read(buf));
                int m = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 600);
                List<ItemStack> pickups = new ArrayList<>();
                for (int i = 0; i < m; i++) pickups.add(ItemStack.STREAM_CODEC.decode(buf));
                return new State(pos, in, link, balance, entries, pickups, ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf),
                        buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, State s) {
                BlockPos.STREAM_CODEC.encode(buf, s.pos());
                buf.writeBoolean(s.loggedIn());
                ByteBufCodecs.VAR_INT.encode(buf, s.link());
                ByteBufCodecs.VAR_LONG.encode(buf, s.balance());
                ByteBufCodecs.VAR_INT.encode(buf, s.entries().size());
                for (Entry e : s.entries()) Entry.write(buf, e);
                ByteBufCodecs.VAR_INT.encode(buf, s.pickups().size());
                for (ItemStack st : s.pickups()) ItemStack.STREAM_CODEC.encode(buf, st);
                ByteBufCodecs.VAR_INT.encode(buf, s.elsewhere());
                ByteBufCodecs.stringUtf8(32).encode(buf, s.message());
                buf.writeBoolean(s.ok());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
