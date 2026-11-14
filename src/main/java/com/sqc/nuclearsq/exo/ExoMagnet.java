package com.sqc.nuclearsq.exo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class ExoMagnet {
    private ExoMagnet() {}

    public static final int[] RADIUS = {0, 3, 5, 7, 10, 14};
    public static final int COST = 2;

    public static void pull(ServerPlayer sp) {
        int lvl = Math.min(ExoModule.MAX_LEVEL, ExoEvents.level(sp, ExoModule.Kind.MAGNET));
        if (lvl == 0 || sp.isSpectator()) return;
        double r = RADIUS[lvl];
        for (ItemEntity e : sp.level().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(r),
                i -> i.isAlive() && !i.hasPickUpDelay() && i.distanceToSqr(sp) > 1.0 && i.distanceToSqr(sp) <= r * r)) {
            if (!sp.isCreative() && !ExoCoreItem.drain(sp, COST)) return;
            e.setPos(sp.getX(), sp.getY(), sp.getZ());
            e.setDeltaMovement(Vec3.ZERO);
        }
    }
}
