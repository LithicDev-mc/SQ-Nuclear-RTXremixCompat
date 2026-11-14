package com.sqc.nuclearsq.transport;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum ConduitSide implements StringRepresentable {
    NONE("none"), ON("on"), PULL("pull"), PORT("port");

    private static final ConduitSide[] VALUES = values();

    private final String name;

    ConduitSide(String name) {
        this.name = name;
    }

    public static ConduitSide of(int conn, Direction d) {
        return VALUES[(conn >> (2 * d.ordinal())) & 3];
    }

    public static int with(int conn, Direction d, ConduitSide s) {
        return (conn & ~(3 << (2 * d.ordinal()))) | (s.ordinal() << (2 * d.ordinal()));
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
