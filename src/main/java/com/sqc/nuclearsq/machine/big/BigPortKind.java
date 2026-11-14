package com.sqc.nuclearsq.machine.big;

public enum BigPortKind {
    COOLANT("coolant"),
    WET_STEAM("wet_steam"),
    STEAM("steam"),
    WATER("water"),
    FEEDWATER("feedwater"),
    ENERGY("energy"),
    SIGNAL("signal"),
    ITEM("item"),
    TERMINAL("terminal"),
    HIGH_VOLTAGE("high_voltage"),
    PRIMARY("primary"),
    GAS("gas"),
    ACID("acid"),
    BIG_WATER("big_water");

    private final String id;

    BigPortKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String blockId() {
        return "machine_port_" + id + "_sqc";
    }

    public boolean isFluid() {
        return this != ENERGY && this != SIGNAL && this != ITEM && this != TERMINAL && this != HIGH_VOLTAGE;
    }

    public static BigPortKind of(String id) {
        for (BigPortKind k : values()) if (k.id.equals(id)) return k;
        throw new IllegalArgumentException("Unbekannter Port-Typ " + id);
    }
}
