package com.sqc.nuclearsq.machine.shapes;

public final class DistillationColumnShapes {
    private DistillationColumnShapes() {}

    public static final int COUNT = 1;

    public static final double[][][] BOXES = {
            {{0, 0, 0, 16, 16, 16}},
    };

    public static final boolean[] LADDER = {false};

    public static final int[][] CELLS = {
            {0, 0, 0, 0}, {0, 0, 1, 0}, {0, 0, 2, 0}, {1, 0, 0, 0}, {1, 0, 1, 0}, {1, 0, 2, 0}, {1, 1, 1, 0}, {1, 2, 1, 0},
            {1, 3, 1, 0}, {1, 4, 1, 0}, {1, 5, 1, 0}, {2, 0, 0, 0}, {2, 0, 1, 0}, {2, 0, 2, 0},
    };

    public static final double[] MONITOR = {-0.6875, 0.625, 0.005, -0.25, 0.9375, 0.0625};
}
