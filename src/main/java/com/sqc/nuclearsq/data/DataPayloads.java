package com.sqc.nuclearsq.data;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DataPayloads {
    private DataPayloads() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, path);
    }

    public record Select(BlockPos source) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(id("data_select"));
        public static final StreamCodec<ByteBuf, Select> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Select::source, Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Control(BlockPos source, String key, double value) implements CustomPacketPayload {
        public static final Type<Control> TYPE = new Type<>(id("data_control"));
        public static final StreamCodec<ByteBuf, Control> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Control::source, ByteBufCodecs.stringUtf8(64), Control::key,
                ByteBufCodecs.DOUBLE, Control::value, Control::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Locate(BlockPos source) implements CustomPacketPayload {
        public static final Type<Locate> TYPE = new Type<>(id("data_locate"));
        public static final StreamCodec<ByteBuf, Locate> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Locate::source, Locate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Tree(int containerId, List<DataEntry> entries, DataTerminalMenu.Summary summary) implements CustomPacketPayload {
        public static final Type<Tree> TYPE = new Type<>(id("data_tree"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tree> STREAM_CODEC = StreamCodec.of((buf, t) -> {
            buf.writeVarInt(t.containerId);
            DataEntry.LIST_CODEC.encode(buf, t.entries);
            t.summary.write(buf);
        }, buf -> new Tree(buf.readVarInt(), DataEntry.LIST_CODEC.decode(buf), DataTerminalMenu.Summary.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Command(String line) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(id("console_command"));
        public static final StreamCodec<ByteBuf, Command> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Command::line, Command::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Output(List<Component> lines) implements CustomPacketPayload {
        public static final Type<Output> TYPE = new Type<>(id("console_output"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), Output::lines, Output::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
