package com.sqc.nuclearsq.transport;

import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public interface GridNode {
    int MAX_SPAN = 64;
    int BLOCKS_PER_WIRE = 16;
    int SUBSTATION_RANGE = 6;

    BlockPos nodePos();

    List<BlockPos> links();

    List<Vec3> attachPoints();

    @Nullable
    default Vec3 earthPoint() { return null; }

    default int maxSpan() { return MAX_SPAN; }

    boolean addLink(BlockPos p);

    boolean removeLink(BlockPos p);

    default boolean delivers() { return false; }

    default int fromLine(int max) { return 0; }

    default int lineSpace() { return 0; }

    @Nullable
    static GridNode at(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof PowerMastBlock m) pos = pos.below(st.getValue(m.part()));
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GridNodeBlockEntity n) return n;
        if (be instanceof BigMachineBlockEntity c && c.substation() != null) return c.substation();
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - SUBSTATION_RANGE); cx <= SectionPos.blockToSectionCoord(pos.getX() + SUBSTATION_RANGE); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - SUBSTATION_RANGE); cz <= SectionPos.blockToSectionCoord(pos.getZ() + SUBSTATION_RANGE); cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity o : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(o instanceof BigMachineBlockEntity c) || c.machine() != BigMachine.SUBSTATION || c.substation() == null) continue;
                    if (o.getBlockPos().distManhattan(pos) > 3 * SUBSTATION_RANGE) continue;
                    if (c.cellPositions().contains(pos)) return c.substation();
                }
            }
        }
        return null;
    }

    @Nullable
    static GridNode node(Level level, BlockPos nodePos) {
        BlockEntity be = level.getBlockEntity(nodePos);
        if (be instanceof GridNodeBlockEntity n) return n;
        if (be instanceof BigMachineBlockEntity c) return c.substation();
        return null;
    }

    static int wireCost(BlockPos a, BlockPos b) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(a.distSqr(b)) / BLOCKS_PER_WIRE));
    }

    static int span(GridNode a, GridNode b) {
        return Math.min(a.maxSpan(), b.maxSpan());
    }

    static void link(GridNode a, GridNode b) {
        a.addLink(b.nodePos());
        b.addLink(a.nodePos());
    }

    static int unlinkAll(Level level, GridNode node) {
        int wires = 0;
        BlockPos me = node.nodePos();
        for (BlockPos o : List.copyOf(node.links())) {
            if (level.isClientSide) break;
            if (!level.isLoaded(o)) level.getChunk(o);
            GridNode other = node(level, o);
            if (other != null && other.removeLink(me)) wires += wireCost(me, o);
            node.removeLink(o);
        }
        return wires;
    }
}
