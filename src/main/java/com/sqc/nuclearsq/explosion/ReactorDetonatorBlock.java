package com.sqc.nuclearsq.explosion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ReactorDetonatorBlock extends Block {
    public static final int SEARCH_RANGE = 50;
    private static final long ARM_TICKS = 100;
    private record Armed(net.minecraft.resources.ResourceKey<Level> dim, BlockPos pos, long time) {}

    private static final Map<UUID, Armed> ARMED = new HashMap<>();

    public ReactorDetonatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(level instanceof ServerLevel sl)) return InteractionResult.sidedSuccess(true);

        if (!mayUse(player)) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.detonator_creative_only")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.sidedSuccess(false);
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.sidedSuccess(false);
        BlockPos controller = findController(sl, pos);
        if (controller != null && !level.mayInteract(player, controller)) return InteractionResult.sidedSuccess(false);
        if (controller == null) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.detonator_none", SEARCH_RANGE), true);
            return InteractionResult.sidedSuccess(false);
        }
        long now = level.getGameTime();
        Armed armed = ARMED.get(player.getUUID());
        if (armed == null || now < armed.time() || now - armed.time() > ARM_TICKS
                || armed.dim() != sl.dimension() || !armed.pos().equals(pos)) {
            ARMED.put(player.getUUID(), new Armed(sl.dimension(), pos.immutable(), now));
            player.displayClientMessage(Component.translatable("message.nuclearsq.detonator_arm"), true);
            return InteractionResult.sidedSuccess(false);
        }
        ARMED.remove(player.getUUID());
        if (COUNTDOWNS.containsKey(player.getUUID())) return InteractionResult.sidedSuccess(false);
        COUNTDOWNS.put(player.getUUID(), new Countdown(sl.dimension(), controller, coreOf(sl, controller), COUNTDOWN_TICKS));
        return InteractionResult.sidedSuccess(false);
    }

    public static boolean mayUse(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    public static boolean isArmed(Player player) {
        return ARMED.containsKey(player.getUUID());
    }

    public static void forget(UUID player) {
        ARMED.remove(player);
    }

    private static Vec3 coreOf(ServerLevel level, BlockPos controller) {
        return level.getBlockEntity(controller) instanceof BigMachineBlockEntity be && be.core() != null
                ? be.coreCenter() : Vec3.atCenterOf(controller).add(0, 2.5, 0);
    }

    public static final int COUNTDOWN_TICKS = 60;

    private static final class Countdown {
        final net.minecraft.resources.ResourceKey<Level> dim;
        final BlockPos controller;
        final Vec3 core;
        int left;

        Countdown(net.minecraft.resources.ResourceKey<Level> dim, BlockPos controller, Vec3 core, int left) {
            this.dim = dim;
            this.controller = controller;
            this.core = core;
            this.left = left;
        }
    }

    private static final Map<UUID, Countdown> COUNTDOWNS = new HashMap<>();

    public static void clearCountdowns() {
        COUNTDOWNS.clear();
        ARMED.clear();
    }

    public static void tickCountdowns(net.minecraft.server.MinecraftServer server) {
        if (COUNTDOWNS.isEmpty()) return;
        var it = COUNTDOWNS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Countdown c = e.getValue();
            ServerLevel level = server.getLevel(c.dim);
            if (level == null) { it.remove(); continue; }
            c.left--;
            if (c.left <= 0) {
                it.remove();
                Vec3 core = level.isLoaded(c.controller) ? coreOf(level, c.controller) : c.core;
                NuclearExplosion.detonate(level, core);
                continue;
            }
            if (c.left % 10 == 0 || c.left == COUNTDOWN_TICKS - 1) {
                int sec = (c.left + 19) / 20;
                Component msg = Component.translatable("message.nuclearsq.detonator_countdown", sec)
                        .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
                boolean ping = c.left % 20 == 0 || c.left == COUNTDOWN_TICKS - 1;
                float pitch = 0.6f + 0.4f * (1f - c.left / (float) COUNTDOWN_TICKS);
                double range = NuclearExplosion.Blast.reactor().flashRange();
                for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(c.core) > range * range) continue;
                    p.displayClientMessage(msg, true);
                    if (ping) p.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, pitch);
                }
            }
        }
    }

    private static BlockPos findController(ServerLevel level, BlockPos origin) {
        BigMachineBlockEntity d = BigMachineBlockEntity.nearestDrive(level, origin, SEARCH_RANGE,
                m -> m.isFormed() && level.isLoaded(m.getBlockPos()));
        return d == null ? null : d.getBlockPos();
    }
}
