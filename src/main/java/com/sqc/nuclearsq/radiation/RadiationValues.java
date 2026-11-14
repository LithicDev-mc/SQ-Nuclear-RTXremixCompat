package com.sqc.nuclearsq.radiation;

public final class RadiationValues {
    private RadiationValues() {}

    public static final double AMBIENT_SV_H = 2.7e-7;
    public static final double DOSE_TIME_SCALE = 2400.0;
    public static final double DOSE_DECAY = 0.9995;

    public static final double SV_FIRST_SIGNS = 0.25;
    public static final double SV_NAUSEA = 1.0;
    public static final double SV_SEVERE = 2.0;
    public static final double SV_DAMAGE = 3.0;
    public static final double SV_LETHAL = 5.0;
    public static final double SV_FATAL = 8.0;
    public static final double DOSIMETER_DAY_LIMIT_SV = 0.25;

    public static final double EXPLODED_CORE_SV_H = 200.0;
    public static final double FALLOUT_SV_H = 0.05;

    public static final double FRESH_ROD_SV_H = 0.0001;
    public static final double SPENT_ROD_SV_H = 0.5;
    public static final double COOLED_ROD_SV_H = 0.005;
    public static final double REACTOR_POWER_SV_H = 3.0;
    public static final double REACTOR_LEAK_SV_H = 0.05;
    public static final double TURBINE_SV_H = 0.002;
    public static final double HOT_PRIMARY_SV_H = 0.0005;
    public static final double PURIFIER_SV_H = 0.002;
    public static final double RTG_SV_H = 0.0005;
    public static final double RESIDUE_SV_H = 0.0002;

    public static final double RESIDUE_CARRY_SV_H = 0.0001;
    public static final double HOT_ROD_CARRY_SV_H = 0.05;
    public static final double COOLED_CARRY_SV_H = 0.0005;
    public static final double CANISTER_CARRY_SV_H = 0.01;
    public static final double POOL_SV_H = 0.00002, DRY_SV_H = 0.1;

    public static final float RESIDUE_GROUND_SV_H = 0.01f;
    public static final float MAX_GROUND_SV_H = 0.05f;
    public static final float LEAK_DEPOSIT_SV_H = 0.002f;
    public static final int LEAK_DEPOSIT_RANGE = 5;
    public static final double BODY_MAX_SV_H = 0.02;
    public static final double BODY_DECAY = 0.998;
    public static final double BODY_SPREAD = 0.1;
    public static final double BODY_SPREAD_MIN_SV_H = 0.0002;
    public static final double BODY_PICKUP = 0.2;
    public static final float GROUND_CLEAN_SV_H = 1e-5f;
    public static final double LEAK_AIR_BODY_SV_H = 0.0005;
    public static final double SOURCE_DEPOSIT_CHANCE = 0.1;
    public static final double SOURCE_DEPOSIT_FRACTION = 0.001;
    public static final int SOURCE_DEPOSIT_RANGE = 2;
    public static final double SOURCE_DEPOSIT_MIN_SV_H = 0.05;

    public static final double MONITOR_ALARM_SV_H = 1e-5;
    public static final double AREA_ALARM_SV_H = 1e-5;
    public static final int AREA_SENSOR_RADIUS = 8;

    public static final int IODINE_TICKS = 20 * 60 * 5;
    public static final double IODINE_FACTOR = 0.3;

    public static final double MEDICINE_REDUCE_SV = 3.0;
    public static final int MEDICINE_PROTECT_TICKS = 20 * 60 * 2;
    public static final double MEDICINE_PROTECT_FACTOR = 0.5;

    public static final double SHIELD_LEAD = 0.001;
    public static final double SHIELD_STEEL = 0.02;
    public static final double SHIELD_HULL = 0.1;
    public static final double SHIELD_HEAVY_CONCRETE = 0.01;
    public static final double SHIELD_LEAD_GLASS = 0.02;
    public static final double SHIELD_CONCRETE = 0.05;
    public static final double SHIELD_STONE = 0.08;
    public static final double SHIELD_SOIL = 0.15;
    public static final double SHIELD_WATER = 0.2;
    public static final double SHIELD_OTHER_SOLID = 0.3;
    public static final double SHIELD_WOOD = 0.6;
    public static final double SHIELD_LIGHT = 0.8;
    public static final double SHIELD_PARTIAL = 0.85;
    public static final double SCATTER_FACTOR = 0.2;
    public static final int SCATTER_RANGE = 12;
    public static final int SCATTER_MAX_NODES = 16000;
    public static final double FALLOUT_ROOF_FACTOR = 0.2;
}
