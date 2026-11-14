package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NetScanPayload(long[] cables, long[] devices, List<Machine> machines, boolean incomplete) implements CustomPacketPayload {
    public record Machine(BlockPos pos, BlockPos min, BlockPos max, BlockPos port, int state, Component name) {}

    public static final int MAX = 4096;

    public static final NetScanPayload EMPTY = new NetScanPayload(new long[0], new long[0], List.of(), false);

    public static final Type<NetScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "net_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                writeLongs(buf, p.cables);
                writeLongs(buf, p.devices);
                buf.writeInt(p.machines.size());
                for (Machine m : p.machines) {
                    buf.writeBlockPos(m.pos);
                    buf.writeBlockPos(m.min);
                    buf.writeBlockPos(m.max);
                    buf.writeBlockPos(m.port);
                    buf.writeByte(m.state);
                    ComponentSerialization.STREAM_CODEC.encode(buf, m.name);
                }
                buf.writeBoolean(p.incomplete);
            },
            buf -> {
                long[] cables = readLongs(buf);
                long[] devices = readLongs(buf);
                int n = Math.max(0, Math.min(buf.readInt(), MAX));
                List<Machine> machines = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    BlockPos pos = buf.readBlockPos(), min = buf.readBlockPos(), max = buf.readBlockPos(), port = buf.readBlockPos();
                    int state = buf.readByte();
                    machines.add(new Machine(pos, min, max, port, state, ComponentSerialization.STREAM_CODEC.decode(buf)));
                }
                return new NetScanPayload(cables, devices, machines, buf.readBoolean());
            });

    private static void writeLongs(RegistryFriendlyByteBuf buf, long[] a) {
        buf.writeInt(a.length);
        for (long l : a) buf.writeLong(l);
    }

    private static long[] readLongs(RegistryFriendlyByteBuf buf) {
        long[] a = new long[Math.max(0, Math.min(buf.readInt(), MAX))];
        for (int i = 0; i < a.length; i++) a[i] = buf.readLong();
        return a;
    }

    public boolean isEmpty() {
        return cables.length == 0 && devices.length == 0 && machines.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
