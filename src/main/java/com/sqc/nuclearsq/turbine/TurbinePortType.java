package com.sqc.nuclearsq.turbine;

public enum TurbinePortType {
    ENERGY("energy", 'E', 4, 1, 0, false),
    STEAM("steam", 'V', 3, 6, 6, true),
    WATER("water", 'W', 1, 0, 12, false);

    private final String id;
    public final char c;
    public final int x, y, z;
    public final boolean input;

    TurbinePortType(String id, char c, int x, int y, int z, boolean input) {
        this.id = id;
        this.c = c;
        this.x = x;
        this.y = y;
        this.z = z;
        this.input = input;
    }

    public String id() {
        return id;
    }
}
