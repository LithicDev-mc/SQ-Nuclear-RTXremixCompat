package com.sqc.nuclearsq.nanobot;

import java.util.UUID;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record NanoSwarmPayload(UUID owner, Vec3 center, int state, long[] targets, int[] progress, int speed) implements CustomPacketPayload {
    public static final long NONE = Long.MIN_VALUE;
    public static final int MAX_BOTS = 10;
    public static final Type<NanoSwarmPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "nano_swarm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NanoSwarmPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.owner);
                buf.writeDouble(p.center.x);
                buf.writeDouble(p.center.y);
                buf.writeDouble(p.center.z);
                buf.writeByte(p.state);
                int n = Math.min(p.count(), MAX_BOTS);
                buf.writeByte(n);
                for (int i = 0; i < n; i++) {
                    buf.writeLong(p.targets[i]);
                    buf.writeByte(p.progress[i]);
                }
                buf.writeByte(p.speed);
            },
            buf -> {
                UUID owner = buf.readUUID();
                Vec3 c = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int state = buf.readByte();
                int n = Math.max(0, Math.min(buf.readByte(), MAX_BOTS));
                long[] t = new long[n];
                int[] pr = new int[n];
                for (int i = 0; i < n; i++) {
                    t[i] = buf.readLong();
                    pr[i] = buf.readByte();
                }
                int speed = buf.readByte();
                return new NanoSwarmPayload(owner, c, state, t, pr, speed);
            });

    public int count() {
        return targets.length;
    }

    public static NanoSwarmPayload gone(UUID owner) {
        return new NanoSwarmPayload(owner, Vec3.ZERO, 0, new long[0], new int[0], 10);
    }

    public static NanoSwarmPayload goneAfterEntry(UUID owner) {
        return new NanoSwarmPayload(owner, Vec3.ZERO, 5, new long[0], new int[0], 10);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
