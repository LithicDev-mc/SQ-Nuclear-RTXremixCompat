package com.sqc.nuclearsq.machine.shapes;

public final class WindTurbineLargeShapes {
    private WindTurbineLargeShapes() {}

    public static final int COUNT = 3;

    public static final double[][][] BOXES = {
            {{1, 0, 1, 16, 3, 15}},
            {{0, 0, 1, 16, 3, 15}, {2, 3, 2, 14, 15, 12}, {4, 8, 0, 12, 13, 1}},
            {{0, 0, 1, 15, 3, 15}},
    };

    public static final boolean[] LADDER = {false, false, false};

    public static final int[][] CELLS = {
            {16, 0, 0, 0}, {17, 0, 0, 1}, {18, 0, 0, 2},
    };

    public static final double[] MONITOR = {0.25, 0.5, 0.005, 0.75, 0.8125, 0.0625};
}
