package com.sqc.nuclearsq.data;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class DeskClick {
    private static final double BEHIND = 0.4;
    private static final double[][] ZONES = {ControlDesk.SCREEN_LEFT, ControlDesk.SCREEN_RIGHT, ControlDesk.ESTOP};

    private DeskClick() {}

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isSecondaryUseActive() || p.isSpectator()) return;
        Level level = e.getLevel();
        Vec3 eye = p.getEyePosition(), hit = e.getHitVec().getLocation(), d = hit.subtract(eye);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 end = hit.add(d.normalize().scale(BEHIND));
        BigMachineBlockEntity best = null;
        double[] bestZone = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos pos = e.getPos();
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
            BigMachineBlockEntity desk = ControlDesk.at(level, q);
            if (desk == null || desk.formedRot() < 0) continue;
            for (double[] z : ZONES) {
                AABB box = ControlDesk.worldBox(desk.getBlockPos(), desk.formedRot(), z);
                var c = box.clip(eye, end);
                if (c.isEmpty()) continue;
                double dist = c.get().distanceToSqr(eye);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = desk;
                    bestZone = z;
                }
            }
        }
        if (best == null || !level.mayInteract(p, best.getBlockPos())) return;
        if (p instanceof ServerPlayer sp) ControlDesk.click(sp, best, bestZone);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
