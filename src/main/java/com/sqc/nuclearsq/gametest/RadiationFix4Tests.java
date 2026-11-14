package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationFix4Tests {
    private RadiationFix4Tests() {}

    private static final String FLOOR = "floor";
    private static final double SIDE_TOP_MAX_RATIO = 1.5;

    @GameTest(template = FLOOR, batch = "rad_fix4_side", timeoutTicks = 60)
    public static void hullRadiatesToSidesLikeToTop(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        var casing = ReactorRegistry.CASING.get().defaultBlockState();
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) {
            for (int y = 3; y <= 11; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos srcRel = new BlockPos(7, 6, 7);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dy != 0 || dz != 0) h.setBlock(srcRel.offset(dx, dy, dz), casing);
        }
        BlockPos src = h.absolutePos(srcRel);
        Vec3 side = Vec3.atCenterOf(h.absolutePos(srcRel.east(4)));
        Vec3 side2 = Vec3.atCenterOf(h.absolutePos(srcRel.north(4)));
        Vec3 top = Vec3.atCenterOf(h.absolutePos(srcRel.above(4)));
        double free = 1.0 / 16.0;
        h.runAfterDelay(25, () -> {
            double s = RadiationManager.sourceRate(lv, src, 1.0, side);
            double s2 = RadiationManager.sourceRate(lv, src, 1.0, side2);
            double t = RadiationManager.sourceRate(lv, src, 1.0, top);
            String got = String.format("seitlich %.3g / %.3g, oben %.3g, ohne Huelle %.3g", s, s2, t, free);
            if (!(t > 0 && s > 0)) throw new GameTestAssertException("Keine Strahlung: " + got);
            if (Math.max(s, t) / Math.min(s, t) > SIDE_TOP_MAX_RATIO) throw new GameTestAssertException("Seitlich und oben ungleich: " + got);
            if (Math.max(s, s2) / Math.min(s, s2) > SIDE_TOP_MAX_RATIO) throw new GameTestAssertException("Seiten ungleich: " + got);
            if (!(s >= free * RadiationValues.SHIELD_HULL * 0.5)) throw new GameTestAssertException("Seitlich zu wenig: " + got);
            h.succeed();
        });
    }
}
