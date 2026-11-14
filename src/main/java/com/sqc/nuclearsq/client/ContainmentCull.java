package com.sqc.nuclearsq.client;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ContainmentCull {
    private static final double INNER_RADIUS = 14.6;
    private static final Map<BlockPos, Hull> HULLS = new HashMap<>();
    private static Level hullLevel;
    private static final Method DOOR_OPEN = findDoorOpen();

    private record Hull(BigMachineBlockEntity be, AABB box) {}

    private ContainmentCull() {}

    private static Method findDoorOpen() {
        try {
            return BigMachineBlockEntity.class.getMethod("doorOpen");
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static void register(BigMachineBlockEntity be, AABB box) {
        Level level = be.getLevel();
        if (level != hullLevel) {
            HULLS.clear();
            hullLevel = level;
        }
        HULLS.put(be.getBlockPos(), new Hull(be, box));
    }

    private static boolean doorOpen(BigMachineBlockEntity be) {
        if (DOOR_OPEN == null) return false;
        try {
            return Boolean.TRUE.equals(DOOR_OPEN.invoke(be));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static boolean cameraInside(AABB b, Vec3 cam) {
        double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
        return Math.hypot(cam.x - cx, cam.z - cz) < INNER_RADIUS && cam.y > b.minY - 1 && cam.y < b.maxY + 1;
    }

    public static boolean hidden(BigMachineBlockEntity be) {
        if (HULLS.isEmpty() || be.getLevel() != hullLevel) return false;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos p = be.getBlockPos();
        for (Iterator<Hull> it = HULLS.values().iterator(); it.hasNext(); ) {
            Hull h = it.next();
            if (h.be().isRemoved()) {
                it.remove();
                continue;
            }
            AABB b = h.box();
            if (p.getX() + 0.5 <= b.minX || p.getX() + 0.5 >= b.maxX || p.getZ() + 0.5 <= b.minZ || p.getZ() + 0.5 >= b.maxZ
                    || p.getY() < b.minY || p.getY() >= b.maxY) continue;
            return !cameraInside(b, cam) && !doorOpen(h.be());
        }
        return false;
    }
}
