package com.sqc.nuclearsq.radiation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ContaminationMonitorBlockEntity extends BlockEntity {
    public static final double THRESHOLD_SV_H = RadiationValues.MONITOR_ALARM_SV_H;
    public static final int REACH = 2;
    public static final int BEEPS = 3;
    private static final int BEEP_GAP = 3;

    private final Set<UUID> present = new HashSet<>();
    private int beepsLeft;
    private int beepWait;

    public ContaminationMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ProtectionRegistry.MONITOR_BE.get(), pos, state);
    }

    public static double reading(Player p) {
        return Contamination.body(p) + ItemContamination.carried(p);
    }

    public AABB area() {
        Direction d = getBlockState().getValue(ContaminationMonitorBlock.FACING);
        BlockPos near = worldPosition.relative(d);
        BlockPos far = worldPosition.relative(d, REACH);
        return new AABB(near).minmax(new AABB(far)).expandTowards(0, -2, 0).expandTowards(0, 1, 0);
    }

    public void serverTick() {
        if (level == null) return;
        if (beepsLeft > 0 && --beepWait <= 0) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8f, 2.0f);
            beepsLeft--;
            beepWait = BEEP_GAP;
        }
        if (level.getGameTime() % 20 != Math.floorMod(worldPosition.asLong(), 20)) return;
        check(com.sqc.nuclearsq.compat.sable.SableCompat.entitiesIn(level, Player.class, area(), p -> !p.isSpectator()));
    }

    public boolean check(List<Player> players) {
        if (level == null) return false;
        boolean alarm = false;
        boolean newcomerClean = false;
        Set<UUID> now = new HashSet<>();
        for (Player p : players) {
            now.add(p.getUUID());
            double sv = reading(p);
            if (sv >= THRESHOLD_SV_H) {
                alarm = true;
                p.displayClientMessage(Component.translatable("message.nuclearsq.contamination_monitor.alarm",
                        RadiationManager.fmtRate(sv)), true);
            } else if (!present.contains(p.getUUID())) {
                newcomerClean = true;
                p.displayClientMessage(Component.translatable("message.nuclearsq.contamination_monitor.clean"), true);
            }
        }
        present.clear();
        present.addAll(now);
        if (alarm) {
            beepsLeft = BEEPS;
            beepWait = 0;
        } else if (newcomerClean) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7f, 0.7f);
        }
        BlockState st = getBlockState();
        if (st.getValue(ContaminationMonitorBlock.ALARM) != alarm) {
            level.setBlock(worldPosition, st.setValue(ContaminationMonitorBlock.ALARM, alarm), Block.UPDATE_ALL);
        }
        return alarm;
    }
}
