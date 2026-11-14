package com.sqc.nuclearsq.nanobot.client;

import org.joml.Matrix3f;
import org.joml.Quaternionf;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Surface {
    private static final Surface[] ALL = new Surface[6];

    static {
        for (Direction n : Direction.values()) ALL[n.ordinal()] = new Surface(n);
    }

    public final Direction n, ex, ez;
    public final Quaternionf quat = new Quaternionf();

    private Surface(Direction n) {
        this.n = n;
        switch (n) {
            case UP -> {
                ex = Direction.EAST;
                ez = Direction.SOUTH;
            }
            case DOWN -> {
                ex = Direction.EAST;
                ez = Direction.NORTH;
            }
            default -> {
                ez = Direction.UP;
                ex = switch (n) {
                    case EAST -> Direction.SOUTH;
                    case WEST -> Direction.NORTH;
                    case SOUTH -> Direction.WEST;
                    default -> Direction.EAST;
                };
            }
        }
        quat.setFromNormalized(new Matrix3f(ex.getStepX(), ex.getStepY(), ex.getStepZ(), n.getStepX(), n.getStepY(), n.getStepZ(),
                ez.getStepX(), ez.getStepY(), ez.getStepZ()));
    }

    public static Surface of(Direction n) {
        return ALL[n.ordinal()];
    }

    boolean isFloor() {
        return n == Direction.UP;
    }

    public Vec3 toLocal(Vec3 w) {
        return new Vec3(w.x * ex.getStepX() + w.y * ex.getStepY() + w.z * ex.getStepZ(),
                w.x * n.getStepX() + w.y * n.getStepY() + w.z * n.getStepZ(),
                w.x * ez.getStepX() + w.y * ez.getStepY() + w.z * ez.getStepZ());
    }

    public Vec3 toWorld(double lx, double ly, double lz) {
        return new Vec3(ex.getStepX() * lx + n.getStepX() * ly + ez.getStepX() * lz,
                ex.getStepY() * lx + n.getStepY() * ly + ez.getStepY() * lz,
                ex.getStepZ() * lx + n.getStepZ() * ly + ez.getStepZ() * lz);
    }

    Vec3 vecLocal(Vec3 w) {
        return toLocal(w);
    }

    AABB box(double lx, double ly, double lz, double hw, double lo, double hi) {
        Vec3 a = toWorld(lx - hw, ly + lo, lz - hw), b = toWorld(lx + hw, ly + hi, lz + hw);
        return new AABB(a.x, a.y, a.z, b.x, b.y, b.z);
    }

    Vec3 heading(double h) {
        return toWorld(Math.sin(h), 0, Math.cos(h));
    }

    double headingOf(Vec3 dir) {
        Vec3 l = toLocal(dir);
        return Math.atan2(l.x, l.z);
    }

    int sign() {
        return n.getAxisDirection().getStep();
    }
}
