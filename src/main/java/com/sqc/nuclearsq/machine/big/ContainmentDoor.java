package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.machine.shapes.ContainmentShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ContainmentDoor {
    private ContainmentDoor() {}

    public static final int X0 = 16, X1 = 17, HEIGHT = 3, DEPTH = 2;

    public static boolean isDoor(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= 0 && y < HEIGHT && z >= 0 && z < DEPTH;
    }

    public static final int BASE = ContainmentShapes.BOXES.length;

    private static final int[] DOOR_SHAPES = doorShapes();

    public static final double[][][] BOXES = withOpenCopies();

    private static int[] doorShapes() {
        List<Integer> out = new ArrayList<>();
        for (int[] cell : ContainmentShapes.CELLS) {
            if (isDoor(cell[0], cell[1], cell[2]) && !out.contains(cell[3])) out.add(cell[3]);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double[][][] withOpenCopies() {
        double[][][] all = Arrays.copyOf(ContainmentShapes.BOXES, BASE + DOOR_SHAPES.length);
        for (int i = 0; i < DOOR_SHAPES.length; i++) all[BASE + i] = ContainmentShapes.BOXES[DOOR_SHAPES[i]];
        return all;
    }

    public static int open(int idx) {
        for (int i = 0; i < DOOR_SHAPES.length; i++) {
            if (DOOR_SHAPES[i] == idx) return BASE + i;
        }
        return idx;
    }

    public static boolean isOpenShape(int idx) {
        return idx >= BASE;
    }

    @Nullable
    public static BigMachineBlockEntity controllerOf(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 0, 3))) {
            if (level.getBlockState(p).getBlock() instanceof BigMachineControllerBlock c && c.machine() == BigMachine.CONTAINMENT
                    && level.getBlockEntity(p) instanceof BigMachineBlockEntity be && be.isDoorPos(pos)) {
                return be;
            }
        }
        return null;
    }
}
