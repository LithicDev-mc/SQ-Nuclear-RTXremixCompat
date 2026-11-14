package com.sqc.nuclearsq.exo;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.VeinMiner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class ExoMining {
    private ExoMining() {}

    public static final int[] AREA_SIZE = {0, 2, 3, 5};
    public static final int[] VEIN_LIMIT = {0, 6, 12, 24, 48, 96};
    public static final int COST = 5;

    private static boolean running;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (running || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer sp) || !sp.isShiftKeyDown() || !(event.getLevel() instanceof Level level)) return;
        int area = Math.min(AREA_SIZE.length - 1, ExoEvents.level(sp, ExoModule.Kind.AREA));
        int vein = Math.min(VEIN_LIMIT.length - 1, ExoEvents.level(sp, ExoModule.Kind.VEIN));
        BlockState state = event.getState();
        BlockPos origin = event.getPos().immutable();
        if ((area == 0 && vein == 0) || state.isAir() || state.getDestroySpeed(level, origin) < 0) return;
        List<BlockPos> extra = area > 0 ? area(sp, origin, AREA_SIZE[area]) : VeinMiner.collect(level, origin, state.getBlock(), VEIN_LIMIT[vein] - 1);
        if (extra.isEmpty()) return;
        boolean hadTool = !sp.getMainHandItem().isEmpty();
        sp.server.tell(new TickTask(sp.server.getTickCount() + 1, () -> {
            if (sp.isRemoved() || sp.level() != level || level.getBlockState(origin).is(state.getBlock())) return;
            running = true;
            try {
                for (BlockPos p : extra) {
                    if (sp.isRemoved() || (hadTool && sp.getMainHandItem().isEmpty())) break;
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.getDestroySpeed(level, p) < 0 || !s.getFluidState().isEmpty() || level.getBlockEntity(p) != null
                            || !sp.hasCorrectToolForDrops(s) || (area == 0 && !s.is(state.getBlock()))) continue;
                    if (!sp.isCreative() && !ExoCoreItem.drain(sp, COST)) break;
                    sp.gameMode.destroyBlock(p);
                }
            } finally {
                running = false;
            }
        }));
    }

    private static List<BlockPos> area(ServerPlayer sp, BlockPos origin, int size) {
        HitResult hit = sp.pick(sp.blockInteractionRange() + 1, 1f, false);
        Direction face = hit instanceof BlockHitResult b && b.getBlockPos().equals(origin) ? b.getDirection() : sp.getDirection().getOpposite();
        return shape(origin, face, hit instanceof BlockHitResult b ? b.getLocation().subtract(Vec3.atCenterOf(origin)) : Vec3.ZERO, size);
    }

    public static List<BlockPos> shape(BlockPos origin, Direction face, Vec3 off, int size) {
        List<Direction.Axis> axes = new ArrayList<>();
        for (Direction.Axis a : Direction.Axis.values()) if (a != face.getAxis()) axes.add(a);
        int[] r0 = range(size, off.get(axes.get(0))), r1 = range(size, off.get(axes.get(1)));
        List<BlockPos> out = new ArrayList<>();
        for (int i : r0) {
            for (int j : r1) {
                if (i == 0 && j == 0) continue;
                out.add(origin.offset(Direction.get(Direction.AxisDirection.POSITIVE, axes.get(0)).getNormal().multiply(i))
                        .offset(Direction.get(Direction.AxisDirection.POSITIVE, axes.get(1)).getNormal().multiply(j)));
            }
        }
        return out;
    }

    private static int[] range(int size, double frac) {
        if (size == 2) return frac >= 0 ? new int[] {0, 1} : new int[] {-1, 0};
        int[] r = new int[size];
        for (int i = 0; i < size; i++) r[i] = i - size / 2;
        return r;
    }
}
