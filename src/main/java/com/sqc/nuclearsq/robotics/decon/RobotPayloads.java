package com.sqc.nuclearsq.robotics.decon;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RobotPayloads {
    private RobotPayloads() {}

    public record ControlState(int robotId) implements CustomPacketPayload {
        public static final Type<ControlState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "decon_state"));
        public static final StreamCodec<ByteBuf, ControlState> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ControlState::robotId, ControlState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ControlInput(int forward, int turn, boolean stop, float pitch) implements CustomPacketPayload {
        public static final Type<ControlInput> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "decon_input"));
        public static final StreamCodec<ByteBuf, ControlInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ControlInput::forward,
                ByteBufCodecs.VAR_INT, ControlInput::turn,
                ByteBufCodecs.BOOL, ControlInput::stop,
                ByteBufCodecs.FLOAT, ControlInput::pitch,
                ControlInput::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
