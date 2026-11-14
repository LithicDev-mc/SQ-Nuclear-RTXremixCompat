package com.sqc.nuclearsq.turbine;

import com.sqc.nuclearsq.gamedata.GameData;

public final class ShaftValues {
    private ShaftValues() {}

    public static int createMaxRpm() { return GameData.cooling().shaft().createMaxRpm(); }

    public static int createRpmStep() { return Math.max(1, GameData.cooling().shaft().createRpmStep()); }

    public static int createSuPerFe() { return GameData.cooling().shaft().createSuPerFe(); }
}
