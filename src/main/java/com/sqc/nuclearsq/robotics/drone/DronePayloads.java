package com.sqc.nuclearsq.robotics.drone;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DronePayloads {
    private DronePayloads() {}

    public record Fly(boolean mainHand) implements CustomPacketPayload {
        public static final Type<Fly> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "drone_fly"));
        public static final StreamCodec<ByteBuf, Fly> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Fly::mainHand, Fly::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(int droneId) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "drone_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, State::droneId, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Input(int forward, int strafe, int up, float yaw, float pitch, boolean stop) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "drone_input"));
        public static final StreamCodec<ByteBuf, Input> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::forward,
                ByteBufCodecs.VAR_INT, Input::strafe,
                ByteBufCodecs.VAR_INT, Input::up,
                ByteBufCodecs.FLOAT, Input::yaw,
                ByteBufCodecs.FLOAT, Input::pitch,
                ByteBufCodecs.BOOL, Input::stop,
                Input::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Scan(float here, long[] pos, float[] svH, SurveyMap map) implements CustomPacketPayload {
        public static final Type<Scan> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "drone_scan"));
        public static final StreamCodec<ByteBuf, Scan> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeFloat(p.here);
                    buf.writeInt(p.pos.length);
                    for (int i = 0; i < p.pos.length; i++) {
                        buf.writeLong(p.pos[i]);
                        buf.writeFloat(p.svH[i]);
                    }
                    SurveyMap.STREAM_CODEC.encode(buf, p.map);
                },
                buf -> {
                    float here = buf.readFloat();
                    int n = Math.max(0, Math.min(buf.readInt(), SurveyDroneEntity.SCAN_MAX));
                    long[] pos = new long[n];
                    float[] sv = new float[n];
                    for (int i = 0; i < n; i++) {
                        pos[i] = buf.readLong();
                        sv[i] = buf.readFloat();
                    }
                    return new Scan(here, pos, sv, SurveyMap.STREAM_CODEC.decode(buf));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
