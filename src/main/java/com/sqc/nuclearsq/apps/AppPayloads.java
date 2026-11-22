package com.sqc.nuclearsq.apps;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class AppPayloads {
    private AppPayloads() {}

    public static final int OPEN = 0, CREATE = 1, LOGIN = 2, LOGOUT = 3, CHANGE = 4;

    private static final StreamCodec<ByteBuf, String> PW = ByteBufCodecs.stringUtf8(64);

    public record Action(BlockPos pos, int op, String a, String b, String c) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "app_login_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos, ByteBufCodecs.VAR_INT, Action::op, PW, Action::a, PW, Action::b, PW, Action::c,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(BlockPos pos, String name, boolean account, boolean loggedIn, int link, String message, boolean ok,
                        int lockSeconds, int minutesLeft) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "app_login_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public State decode(ByteBuf buf) {
                return new State(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.stringUtf8(32).decode(buf), buf.readBoolean(), buf.readBoolean(),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf), buf.readBoolean(),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, State s) {
                BlockPos.STREAM_CODEC.encode(buf, s.pos());
                ByteBufCodecs.stringUtf8(32).encode(buf, s.name());
                buf.writeBoolean(s.account());
                buf.writeBoolean(s.loggedIn());
                ByteBufCodecs.VAR_INT.encode(buf, s.link());
                ByteBufCodecs.stringUtf8(64).encode(buf, s.message());
                buf.writeBoolean(s.ok());
                ByteBufCodecs.VAR_INT.encode(buf, s.lockSeconds());
                ByteBufCodecs.VAR_INT.encode(buf, s.minutesLeft());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Session(boolean loggedIn, String name) implements CustomPacketPayload {
        public static final Type<Session> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "app_session"));
        public static final StreamCodec<ByteBuf, Session> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Session::loggedIn, ByteBufCodecs.stringUtf8(32), Session::name, Session::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
