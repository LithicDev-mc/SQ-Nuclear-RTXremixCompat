package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.DosimeterPayload;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DeconSprayerItem;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationFixTests {
    private RadiationFixTests() {}

    private static final String FLOOR = "floor";
    private static final double AXIS_MIN_RATIO = 50;
    private static final double CORNER_MIN_RATIO = 10;

    @GameTest(template = FLOOR, batch = "rad_fix_hole", timeoutTicks = 120)
    public static void holeInLeadLetsRadiationThrough(GameTestHelper h) {
        var lv = h.getLevel();
        var lead = ProtectionRegistry.LEAD_BLOCK.get().defaultBlockState();
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) {
            h.setBlock(new BlockPos(x, 3, z), lead);
            for (int y = 4; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos srcRel = new BlockPos(5, 5, 5);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dy != 0 || dz != 0) h.setBlock(srcRel.offset(dx, dy, dz), lead);
        }
        BlockPos src = h.absolutePos(srcRel);
        Vec3 axis = Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 5, 5)));
        Vec3 corner = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 5, 8)));
        double closedAxis = RadiationManager.sourceRate(lv, src, 1.0, axis);
        double closedCorner = RadiationManager.sourceRate(lv, src, 1.0, corner);
        h.setBlock(new BlockPos(6, 5, 5), Blocks.AIR);
        h.runAfterDelay(45, () -> {
            double openAxis = RadiationManager.sourceRate(lv, src, 1.0, axis);
            double openCorner = RadiationManager.sourceRate(lv, src, 1.0, corner);
            String got = String.format("zu %.3g/%.3g, offen Achse %.3g, Ecke %.3g", closedAxis, closedCorner, openAxis, openCorner);
            if (!(openAxis >= closedAxis * AXIS_MIN_RATIO)) throw new GameTestAssertException("Achse nicht deutlich hoeher: " + got);
            if (!(openCorner >= closedCorner * CORNER_MIN_RATIO)) throw new GameTestAssertException("Ecke nicht hoeher: " + got);
            if (!(openCorner < openAxis)) throw new GameTestAssertException("Ecke nicht niedriger als Achse: " + got);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rad_fix_dosimeter", timeoutTicks = 40)
    public static void dosimeterValuesRise(GameTestHelper h) {
        var lv = h.getLevel();
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        sp.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(ProtectionRegistry.DOSIMETER.get()));
        BlockPos feet = sp.blockPosition();
        BlockPos src = feet.offset(2, 1, 0);
        try {
            RadiationManager.setDose(sp, 0);
            Contamination.setBody(sp, 0);
            sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSIMETER);
            Contamination.add(lv, feet, 0.02f);
            DosimeterPayload[] seen = new DosimeterPayload[2];
            for (int i = 0; i < 2; i++) {
                RadiationManager.emit(lv, src, 0.5);
                RadiationManager.tickOnce(lv, sp);
                double[] r = RadiationManager.takeReceived(sp);
                if (r == null || !(r[0] > 0)) throw new GameTestAssertException("Keine Aufnahme gemeldet");
                DosimeterItem.tick(sp, r[0], r[1]);
                seen[i] = DosimeterItem.payload(sp);
            }
            DosimeterPayload a = seen[0], b = seen[1];
            if (!(a.dose() > 0 && b.dose() > a.dose())) throw new GameTestAssertException("Koerperdosis steigt nicht: " + a.dose() + " -> " + b.dose());
            if (!(a.today() > 0 && b.today() > a.today())) throw new GameTestAssertException("Tagesdosis steigt nicht: " + a.today() + " -> " + b.today());
            if (!(a.body() > 0 && b.body() > a.body())) throw new GameTestAssertException("Koerperwert steigt nicht: " + a.body() + " -> " + b.body());
            if (!(b.rate() > RadiationValues.AMBIENT_SV_H * 10)) throw new GameTestAssertException("Ortsdosisleistung fehlt: " + b.rate());
            h.succeed();
        } finally {
            RadiationManager.setDose(sp, 0);
            Contamination.setBody(sp, 0);
            sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSIMETER);
            sp.getInventory().clearContent();
            Contamination.clean(lv, feet, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_fix_spray", timeoutTicks = 20)
    public static void oneSprayCleansFully(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos spot = h.absolutePos(new BlockPos(7, 3, 7));
        try {
            Contamination.add(lv, spot, RadiationValues.MAX_GROUND_SV_H);
            DeconSprayerItem.spray(lv, spot);
            float left = Contamination.zones(lv).get(spot);
            if (left > 0) throw new GameTestAssertException("Nach einem Stoss noch " + left);
            h.succeed();
        } finally {
            Contamination.clean(lv, spot, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_fix_spread", timeoutTicks = 20)
    public static void contaminatedPlayerSpreads(GameTestHelper h) {
        var lv = h.getLevel();
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        BlockPos feet = sp.blockPosition();
        try {
            RadiationManager.setDose(sp, 0);
            Contamination.clean(lv, feet, 1, 1f);
            Contamination.setBody(sp, 0.005);
            RadiationManager.tickOnce(lv, sp);
            float left = Contamination.zones(lv).get(feet);
            if (!(left >= Contamination.CLEAN_SV_H)) throw new GameTestAssertException("Keine Spur am Boden: " + left);
            if (!(Contamination.body(sp) < 0.005)) throw new GameTestAssertException("Koerper gibt nichts ab: " + Contamination.body(sp));
            h.succeed();
        } finally {
            RadiationManager.setDose(sp, 0);
            Contamination.setBody(sp, 0);
            Contamination.clean(lv, feet, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_fix_deposit", timeoutTicks = 20)
    public static void strongSourcesContaminateSurroundings(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos src = h.absolutePos(new BlockPos(11, 3, 11));
        try {
            for (int i = 0; i < 40; i++) RadiationManager.depositAround(lv, src, RadiationValues.FRESH_ROD_SV_H);
            if (sum(lv, src) > 0) throw new GameTestAssertException("Frischer Brennstab verschmutzt: " + sum(lv, src));
            for (int i = 0; i < 40; i++) RadiationManager.depositAround(lv, src, RadiationValues.SPENT_ROD_SV_H);
            if (!(sum(lv, src) > 0)) throw new GameTestAssertException("Abgebrannter Brennstab verschmutzt nichts");
            h.succeed();
        } finally {
            Contamination.clean(lv, src, 3, 1f);
        }
    }

    private static double sum(net.minecraft.server.level.ServerLevel lv, BlockPos c) {
        double s = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -3, -3), c.offset(3, 3, 3))) s += Contamination.zones(lv).get(p);
        return s;
    }
}
