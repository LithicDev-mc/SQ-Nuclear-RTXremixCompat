package com.sqc.nuclearsq.monitor;

import java.util.HashSet;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ImageMonitorInputPayload(BlockPos pos, int kind, int x, int y, int arg, String text) implements CustomPacketPayload {

    public static final Type<ImageMonitorInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "image_monitor_input"));

    public static final int TOUCH = 0;
    public static final int UP = 1;
    public static final int DRAG = 2;
    public static final int MOVE = 3;
    public static final int SCROLL = 4;
    public static final int KEY = 5;
    public static final int KEY_UP = 6;
    public static final int CHAR = 7;
    public static final int CYCLE = 8;
    public static final int FULL = 9;

    public static final double REACH = 10;

    private static final String[] NAMES = {"touch", "up", "drag", "move", "scroll", "key", "key_up", "char"};

    public static final StreamCodec<ByteBuf, ImageMonitorInputPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ImageMonitorInputPayload::pos,
            ByteBufCodecs.VAR_INT, ImageMonitorInputPayload::kind,
            ByteBufCodecs.VAR_INT, ImageMonitorInputPayload::x,
            ByteBufCodecs.VAR_INT, ImageMonitorInputPayload::y,
            ByteBufCodecs.VAR_INT, ImageMonitorInputPayload::arg,
            ByteBufCodecs.stringUtf8(8), ImageMonitorInputPayload::text,
            ImageMonitorInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImageMonitorInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(player, payload);
        });
    }

    public static void apply(ServerPlayer player, ImageMonitorInputPayload p) {
        if (p.kind() < 0 || p.kind() > FULL) return;
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(p.pos())) return;
        if (player.distanceToSqr(Vec3.atCenterOf(p.pos())) > REACH * REACH) return;
        ImageMonitorBlock.Screen screen = ImageMonitorBlock.screen(level, p.pos());
        if (screen == null || !(level.getBlockEntity(screen.origin()) instanceof ImageMonitorBlockEntity main)) return;
        if ((p.kind() == FULL || p.kind() == CYCLE) && !com.sqc.nuclearsq.network.PacketLimit.allow(player, "monitor_resolution", 4)) return;
        if (p.kind() == FULL) {
            boolean on = p.arg() == 1 && p.x() > 0 && p.y() > 0 && p.x() < 16384 && p.y() < 16384;
            main.setFull(screen, on ? p.x() : 0, on ? p.y() : 0);
            return;
        }
        if (p.kind() == CYCLE) {
            main.cyclePpb(screen);
            level.playSound(null, p.pos(), ImageMonitorRegistry.MONITOR_ON.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1f);
            int[] now = main.resolution(screen);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.nuclearsq.image_monitor.info",
                    screen.width(), screen.height(), now[0], now[1]), true);
            return;
        }
        int[] res = main.resolution(screen);
        int x = Math.max(1, Math.min(res[0], p.x()));
        int y = Math.max(1, Math.min(res[1], p.y()));
        Object[] args = switch (p.kind()) {
            case TOUCH -> new Object[]{x, y, player.getName().getString(), p.arg()};
            case UP, DRAG -> new Object[]{x, y, p.arg()};
            case MOVE -> new Object[]{x, y};
            case SCROLL -> new Object[]{x, y, p.arg() < 0 ? -1 : 1};
            case KEY -> new Object[]{p.arg() >> 1, (p.arg() & 1) != 0};
            case KEY_UP -> new Object[]{p.arg()};
            default -> new Object[]{p.text()};
        };
        Set<ImageMonitorBlockEntity.TouchListener> done = new HashSet<>();
        for (int i = 0; i < screen.width(); i++) {
            for (int j = 0; j < screen.height(); j++) {
                if (level.getBlockEntity(screen.origin().relative(screen.right(), i).relative(screen.up(), j)) instanceof ImageMonitorBlockEntity be) {
                    for (ImageMonitorBlockEntity.TouchListener l : be.touchListeners.values()) {
                        if (done.add(l)) l.input(NAMES[p.kind()], args);
                    }
                }
            }
        }
    }
}
