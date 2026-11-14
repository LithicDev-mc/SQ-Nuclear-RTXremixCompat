package com.sqc.nuclearsq.module;

public record ModuleBonus(int fans, int overclocks, int savers) {
    public static final ModuleBonus NONE = new ModuleBonus(0, 0, 0);

    public int activeOverclocks() {
        return Math.min(ModuleKind.MAX, Math.min(overclocks, fans));
    }

    public int activeSavers() {
        return Math.min(ModuleKind.MAX, savers);
    }

    public int speedPct() {
        return 100 + ModuleKind.SPEED_PCT * activeOverclocks();
    }

    public int energyPct() {
        return (100 + ModuleKind.OVERCLOCK_ENERGY_PCT * activeOverclocks()) * (100 - ModuleKind.SAVER_PCT * activeSavers()) / 100;
    }

    public int energy(int base) {
        return Math.max(1, (int) Math.ceil(base * energyPct() / 100.0));
    }

    public boolean any() {
        return fans + overclocks + savers > 0;
    }
}
