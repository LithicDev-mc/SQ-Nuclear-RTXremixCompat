package com.sqc.nuclearsq.radiation;

public final class RadiationState {
    private RadiationState() {}

    public static volatile float dose = 0f;
    public static volatile long lastMs = 0L;

    public static void set(float d) {
        dose = d;
        lastMs = System.currentTimeMillis();
    }

    public static float current() {
        return System.currentTimeMillis() - lastMs > 4000L ? 0f : dose;
    }
}
