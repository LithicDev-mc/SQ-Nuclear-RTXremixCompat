package com.sqc.nuclearsq.compat.sable;

import java.util.List;

import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlockEntity;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

final class SablePhysics {
    private SablePhysics() {}

    static boolean makePhysical(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, Vec3 motion) {
        int x0 = anchor.getX(), y0 = anchor.getY(), z0 = anchor.getZ(), x1 = x0, y1 = y0, z1 = z0;
        for (BlockPos p : blocks) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        ServerSubLevel body = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks,
                new BoundingBox3i(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1)));
        if (body == null || body.isRemoved()) return false;
        if (level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof NuclearBombBlockEntity be) be.markOwnBody();
        if (motion.lengthSqr() > 1.0e-6) {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle != null) handle.addLinearAndAngularVelocity(new Vector3d(motion.x * 20, motion.y * 20, motion.z * 20), new Vector3d());
        }
        return true;
    }
}
