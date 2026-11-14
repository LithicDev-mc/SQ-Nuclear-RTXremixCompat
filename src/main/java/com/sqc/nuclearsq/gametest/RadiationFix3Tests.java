package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.registry.ModEffects;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationFix3Tests {
    private RadiationFix3Tests() {}

    private static final String FLOOR = "floor";
    private static final double HULL_AXIS_MIN_RATIO = 5;
    private static final double HULL_SIDE_MIN_RATIO = 3;
    private static final double DOSE_TOLERANCE = 0.05;

    @GameTest(template = FLOOR, batch = "rad_fix3_hull", timeoutTicks = 120)
    public static void holeInReactorHullRaisesRoomBehind(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        var casing = ReactorRegistry.CASING.get().defaultBlockState();
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) {
            for (int y = 3; y <= 9; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos srcRel = new BlockPos(4, 5, 7);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dy != 0 || dz != 0) h.setBlock(srcRel.offset(dx, dy, dz), casing);
        }
        BlockPos src = h.absolutePos(srcRel);
        Vec3 axis = Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 5, 7)));
        Vec3 side = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 5, 11)));
        double closedAxis = RadiationManager.sourceRate(lv, src, 1.0, axis);
        double closedSide = RadiationManager.sourceRate(lv, src, 1.0, side);
        h.setBlock(srcRel.east(), Blocks.AIR);
        h.runAfterDelay(45, () -> {
            double openAxis = RadiationManager.sourceRate(lv, src, 1.0, axis);
            double openSide = RadiationManager.sourceRate(lv, src, 1.0, side);
            String got = String.format("zu %.3g/%.3g, offen Achse %.3g, seitlich %.3g", closedAxis, closedSide, openAxis, openSide);
            if (!(closedAxis > 0 && openAxis >= closedAxis * HULL_AXIS_MIN_RATIO)) throw new GameTestAssertException("Achse nicht deutlich hoeher: " + got);
            if (!(closedSide > 0 && openSide >= closedSide * HULL_SIDE_MIN_RATIO)) throw new GameTestAssertException("Seitlich nicht hoeher: " + got);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rad_fix3_medicine", timeoutTicks = 20)
    public static void medicineReducesAndSlowsDose(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = cleanPlayer(h);
        BlockPos src = sp.blockPosition().offset(2, 1, 0);
        try {
            RadiationManager.setDose(sp, RadiationValues.MEDICINE_REDUCE_SV + 1);
            RadiationManager.reduceDose(sp, RadiationValues.MEDICINE_REDUCE_SV);
            if (Math.abs(RadiationManager.doseOf(sp) - 1) > 1e-9) throw new GameTestAssertException("Nach Medizin " + RadiationManager.doseOf(sp) + " Sv statt 1");
            RadiationManager.setDose(sp, 0);

            double without = rateOnce(lv, sp, src);
            RadiationManager.takeMedicine(sp);
            double with = rateOnce(lv, sp, src);
            if (!(without > 0)) throw new GameTestAssertException("Keine Strahlung am Messpunkt");
            if (Math.abs(with / without - RadiationValues.MEDICINE_PROTECT_FACTOR) > 1e-6) {
                throw new GameTestAssertException("Mit Medizin " + with / without + " statt " + RadiationValues.MEDICINE_PROTECT_FACTOR);
            }
            if (!sp.hasEffect(ModEffects.RADIATION_PROTECTION)) throw new GameTestAssertException("Kein Effekt-Symbol");
            if (!(RadiationManager.medicineTicksLeft(sp) > 0)) throw new GameTestAssertException("Keine Restzeit");
            if (DosimeterItem.payload(sp).medicineTicks() <= 0) throw new GameTestAssertException("Dosimeter zeigt keine Medizin");
            sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.MEDICINE_UNTIL, lv.getGameTime() - 1);
            double later = rateOnce(lv, sp, src);
            if (Math.abs(later - without) > without * 1e-6) throw new GameTestAssertException("Nach Ablauf " + later / without + " statt 1,0");
            h.succeed();
        } finally {
            cleanup(sp);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_fix3_iodine", timeoutTicks = 20)
    public static void iodineReducesUptake(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = cleanPlayer(h);
        BlockPos feet = sp.blockPosition();
        try {
            Contamination.add(lv, feet, 1e-4f);
            RadiationManager.tickOnce(lv, sp);
            double without = Contamination.body(sp);
            Contamination.setBody(sp, 0);
            RadiationManager.takeIodine(sp);
            RadiationManager.tickOnce(lv, sp);
            double with = Contamination.body(sp);
            if (!(without > 0)) throw new GameTestAssertException("Keine Aufnahme vom Boden");
            if (Math.abs(with / without - RadiationValues.IODINE_FACTOR) > 1e-3) {
                throw new GameTestAssertException("Aufnahme mit Iod " + with / without + " statt " + RadiationValues.IODINE_FACTOR);
            }
            if (!sp.hasEffect(ModEffects.IODINE)) throw new GameTestAssertException("Kein Effekt-Symbol");
            if (DosimeterItem.payload(sp).iodineTicks() <= 0) throw new GameTestAssertException("Dosimeter zeigt kein Iod");
            h.succeed();
        } finally {
            Contamination.clean(lv, feet, 1, 1f);
            cleanup(sp);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_fix3_dose", timeoutTicks = 20)
    public static void constantRateGivesExpectedDose(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = cleanPlayer(h);
        BlockPos src = sp.blockPosition().offset(2, 1, 0);
        double svH = 0.05;
        int n = 600;
        try {
            Vec3 body = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
            double expectRate = svH / Vec3.atCenterOf(src).distanceToSqr(body);
            for (int i = 0; i < n; i++) {
                RadiationManager.emit(lv, src, svH);
                RadiationManager.tickOnce(lv, sp);
                double[] r = RadiationManager.takeReceived(sp);
                if (r == null || Math.abs(r[1] - expectRate) > expectRate * DOSE_TOLERANCE) {
                    throw new GameTestAssertException("Dosisleistung " + (r == null ? "fehlt" : r[1]) + " statt " + expectRate);
                }
            }
            double decay = RadiationValues.DOSE_DECAY;
            double expect = expectRate / 3600.0 * RadiationValues.DOSE_TIME_SCALE * (1 - Math.pow(decay, n)) / (1 - decay);
            double got = RadiationManager.doseOf(sp);
            if (Math.abs(got - expect) > expect * DOSE_TOLERANCE) {
                throw new GameTestAssertException(String.format("Dosis nach %d s: %.4g Sv statt %.4g Sv", n, got, expect));
            }
            h.succeed();
        } finally {
            cleanup(sp);
        }
    }

    private static double rateOnce(ServerLevel lv, ServerPlayer sp, BlockPos src) {
        RadiationManager.emit(lv, src, 0.5);
        RadiationManager.tickOnce(lv, sp);
        double[] r = RadiationManager.takeReceived(sp);
        if (r == null) throw new GameTestAssertException("Keine Aufnahme gemeldet");
        return r[1];
    }

    private static ServerPlayer cleanPlayer(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        cleanup(sp);
        return sp;
    }

    private static void cleanup(ServerPlayer sp) {
        RadiationManager.setDose(sp, 0);
        Contamination.setBody(sp, 0);
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.MEDICINE_UNTIL);
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSIMETER);
        sp.removeAllEffects();
    }
}
