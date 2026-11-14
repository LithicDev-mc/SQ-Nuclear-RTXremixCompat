package com.sqc.nuclearsq.reactor;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class Cherenkov {
    private Cherenkov() {}

    public static final int MAX = 15, DEFAULT = 10;
    private static final int RANGE = 12;

    public interface Holder {
        int cherenkov();

        void setCherenkov(int level);

        boolean cherenkovCovers(BlockPos pos);
    }

    @Nullable
    public static BlockEntity find(Level level, BlockPos pos) {
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - RANGE); cx <= SectionPos.blockToSectionCoord(pos.getX() + RANGE); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - RANGE); cz <= SectionPos.blockToSectionCoord(pos.getZ() + RANGE); cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof Holder h && be.getBlockPos().distManhattan(pos) <= 3 * RANGE && h.cherenkovCovers(pos)) return be;
                }
            }
        }
        return null;
    }

    public static void apply(Player player, BlockPos holderPos, int value) {
        Level level = player.level();
        if (!level.isLoaded(holderPos) || !(level.getBlockEntity(holderPos) instanceof Holder h)) return;
        if (player.distanceToSqr(holderPos.getCenter()) > (RANGE + 6) * (RANGE + 6)) return;
        if (h instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be && player instanceof net.minecraft.server.level.ServerPlayer sp
                && !be.access().can(sp, be.owner(), com.sqc.nuclearsq.machine.MachineAccess.USE)) return;
        int n = Mth.clamp(value, 0, MAX);
        h.setCherenkov(n);
        showLevel(player, n);
    }

    public static void showLevel(Player player, int n) {
        Component bar = Component.literal("|".repeat(n)).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("|".repeat(MAX - n)).withStyle(ChatFormatting.DARK_GRAY));
        player.displayClientMessage(Component.translatable("message.nuclearsq.cherenkov", n, MAX).append(" ").append(bar), true);
    }
}
