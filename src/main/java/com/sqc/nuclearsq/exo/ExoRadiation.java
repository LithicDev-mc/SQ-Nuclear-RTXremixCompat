package com.sqc.nuclearsq.exo;

import net.minecraft.world.entity.LivingEntity;

public final class ExoRadiation {
    private ExoRadiation() {}

    public static final double[] SHIELD = {0, 0.2, 0.35, 0.5, 0.65, 0.8};
    public static final double[] DUST = {0, 0.3, 0.4, 0.5, 0.6, 0.7};
    public static final double[] AIR = {0, 0.5, 0.65, 0.8, 0.9, 0.95};

    public static int level(LivingEntity e) {
        return Math.min(ExoModule.MAX_LEVEL, ExoEvents.level(e, ExoModule.Kind.RAD));
    }

    public static double shield(LivingEntity e) {
        return SHIELD[level(e)];
    }

    public static double dust(LivingEntity e) {
        return DUST[level(e)];
    }

    public static double air(LivingEntity e) {
        return AIR[level(e)];
    }
}
