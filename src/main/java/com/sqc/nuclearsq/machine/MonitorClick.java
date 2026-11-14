package com.sqc.nuclearsq.machine;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class MonitorClick {
    private static final double BEHIND = 0.4;
    private static final int RANGE = 8;

    private MonitorClick() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isSecondaryUseActive() || p.isSpectator()) return;
        Vec3 eye = p.getEyePosition(), hit = e.getHitVec().getLocation();
        BlockEntity be = find(e.getLevel(), e.getPos(), eye, hit);
        if (be == null || !e.getLevel().mayInteract(p, be.getBlockPos())) return;
        if (p instanceof ServerPlayer sp) {
            ((MonitorTerminal) be).openMonitor(sp);
            com.sqc.nuclearsq.progression.GuideProgress.award(sp, "terminal");
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
    }

    @Nullable
    public static BlockEntity find(Level level, BlockPos pos, Vec3 eye, Vec3 hit) {
        Vec3 d = hit.subtract(eye);
        if (d.lengthSqr() < 1e-6) return null;
        Vec3 end = hit.add(d.normalize().scale(BEHIND));
        BlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - RANGE); cx <= SectionPos.blockToSectionCoord(pos.getX() + RANGE); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - RANGE); cz <= SectionPos.blockToSectionCoord(pos.getZ() + RANGE); cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof MonitorTerminal mt) || be.getBlockPos().distManhattan(pos) > 3 * RANGE) continue;
                    AABB box = mt.monitorWorldBox(be.getBlockPos());
                    if (box == null) continue;
                    var c = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
                    if (c.isEmpty()) continue;
                    double dist = c.get().distanceToSqr(eye);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = be;
                    }
                }
            }
        }
        return best;
    }
}
