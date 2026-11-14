package com.sqc.nuclearsq.exo;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.common.Tags;

public final class ExoOre {
    private ExoOre() {}

    public static final int[] RANGE = {0, 8, 16, 24, 32, 40};
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    @Nullable
    public static BlockPos nearest(ServerLevel level, BlockPos center, int r) {
        BlockPos best = null;
        double bestD = (double) r * r;
        int minY = center.getY() - r, maxY = center.getY() + r;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    int base = (chunk.getMinSection() + si) << 4;
                    if (base + 15 < minY || base > maxY || !sections[si].maybeHas(s -> s.is(Tags.Blocks.ORES))) continue;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (!sections[si].getBlockState(x, y, z).is(Tags.Blocks.ORES)) continue;
                        BlockPos p = new BlockPos((cx << 4) + x, base + y, (cz << 4) + z);
                        double d = p.distSqr(center);
                        if (d < bestD) {
                            bestD = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    static String arrow(ServerPlayer sp, BlockPos target) {
        double dx = target.getX() + 0.5 - sp.getX(), dz = target.getZ() + 0.5 - sp.getZ();
        float diff = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - sp.getYRot());
        return ARROWS[Math.floorMod(Math.round(diff / 45f), 8)];
    }

    public static void show(ServerPlayer sp, int level) {
        int r = RANGE[Math.min(RANGE.length - 1, level)];
        BlockPos ore = nearest(sp.serverLevel(), sp.blockPosition(), r);
        Component msg;
        if (ore == null) {
            msg = Component.translatable("message.nuclearsq.exo_ore.none", r);
        } else {
            int dy = ore.getY() - sp.blockPosition().getY();
            msg = Component.translatable("message.nuclearsq.exo_ore.found", arrow(sp, ore) + (dy > 2 ? "▲" : dy < -2 ? "▼" : ""),
                    Math.round(Math.sqrt(ore.distSqr(sp.blockPosition()))), sp.serverLevel().getBlockState(ore).getBlock().getName());
        }
        sp.displayClientMessage(msg, true);
    }
}
