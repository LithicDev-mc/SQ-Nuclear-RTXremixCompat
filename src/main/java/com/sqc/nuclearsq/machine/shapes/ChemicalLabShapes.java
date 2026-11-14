package com.sqc.nuclearsq.machine.shapes;

public final class ChemicalLabShapes {
    private ChemicalLabShapes() {}

    public static final int COUNT = 11;

    public static final double[][][] BOXES = {
            {{8, 0, 8, 16, 8, 16}},
            {{0, 0, 0, 8, 16, 16}, {8, 0, 0, 16, 8, 16}},
            {{8, 0, 0, 16, 8, 8}},
            {{0, 0, 0, 16, 16, 8}, {0, 0, 8, 16, 8, 16}},
            {{0, 0, 0, 16, 8, 16}},
            {{0, 0, 0, 16, 8, 8}},
            {{0, 0, 0, 16, 16, 8}},
            {{0, 8, 0, 16, 16, 16}},
            {{0, 0, 8, 8, 8, 16}},
            {{0, 0, 0, 8, 8, 16}, {8, 0, 0, 16, 16, 16}},
            {{0, 0, 0, 8, 8, 8}},
    };

    public static final boolean[] LADDER = {false, false, false, false, false, false, false, false, false, false, false};

    public static final int[][] CELLS = {
            {0, 0, 0, 0}, {0, 0, 1, 1}, {0, 0, 2, 2}, {1, 0, 0, 3}, {1, 0, 1, 4}, {1, 0, 2, 5}, {1, 1, 0, 6}, {1, 1, 1, 7},
            {2, 0, 0, 8}, {2, 0, 1, 9}, {2, 0, 2, 10},
    };

    public static final double[] MONITOR = {0.0625, 1, 0.005, 0.9375, 1.4375, 0.0625};
}
