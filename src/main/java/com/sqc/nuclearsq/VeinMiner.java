package com.sqc.nuclearsq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.TickTask;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class VeinMiner {
    private VeinMiner() {}

    private static final Set<UUID> HOLDING = new HashSet<>();
    private static boolean running;

    public static void setHeld(ServerPlayer player, boolean held) {
        if (held) HOLDING.add(player.getUUID());
        else HOLDING.remove(player.getUUID());
    }

    public static boolean isHeld(ServerPlayer player) {
        return HOLDING.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HOLDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (running || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer sp) || !isHeld(sp)) return;
        if (!NuclearConfig.VEIN_MINER.get() || !(event.getLevel() instanceof Level level)) return;
        BlockState state = event.getState();
        if (state.isAir() || state.getDestroySpeed(level, event.getPos()) < 0) return;
        List<BlockPos> vein = collect(level, event.getPos(), state.getBlock(), NuclearConfig.VEIN_MINER_LIMIT.get() - 1);
        if (vein.isEmpty()) return;
        ItemStack tool = sp.getMainHandItem();
        boolean hadTool = !tool.isEmpty();
        BlockPos origin = event.getPos().immutable();
        sp.server.tell(new TickTask(sp.server.getTickCount() + 1, () -> {
            if (sp.isRemoved() || sp.level() != level || level.getBlockState(origin).is(state.getBlock())) return;
            running = true;
            try {
                for (BlockPos p : vein) {
                    if (sp.isRemoved() || (hadTool && sp.getMainHandItem().isEmpty())) break;
                    if (!sp.level().getBlockState(p).is(state.getBlock())) continue;
                    sp.gameMode.destroyBlock(p);
                }
            } finally {
                running = false;
            }
        }));
    }

    public static List<BlockPos> collect(Level level, BlockPos start, Block block, int limit) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && out.size() < limit) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1 && out.size() < limit; dx++)
                for (int dy = -1; dy <= 1 && out.size() < limit; dy++)
                    for (int dz = -1; dz <= 1 && out.size() < limit; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = cur.offset(dx, dy, dz);
                        if (n.distManhattan(start) > 64 || !seen.add(n) || !level.isLoaded(n)) continue;
                        if (!level.getBlockState(n).is(block)) continue;
                        out.add(n);
                        queue.add(n);
                    }
        }
        return out;
    }
}
