package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.RadiationSickness;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SicknessTests {
    private SicknessTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile TEST_PROFILE = new GameProfile(UUID.fromString("7a1c2e3f-4b5d-4e6f-9a01-b2c3d4e5f607"), "nsq_sick");

    @GameTest(template = FLOOR, batch = "sick_stage", timeoutTicks = 20)
    public static void stagesFollowRealThresholds(GameTestHelper h) {
        double[] dose = {0.1, 0.3, 1.2, 2.5, 3.5, 6, 9};
        for (int i = 0; i < dose.length; i++) {
            int s = RadiationSickness.stage(dose[i]);
            if (s != i) throw new GameTestAssertException("stage(" + dose[i] + ") = " + s + ", erwartet " + i);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "sick_damage", timeoutTicks = 20)
    public static void damageRisesWithDose(GameTestHelper h) {
        if (RadiationSickness.damagePerSecond(0.5) != 0) throw new GameTestAssertException("Schaden bei 0,5 Sv");
        if (RadiationSickness.damagePerSecond(2.0) != 0) throw new GameTestAssertException("Schaden bei 2 Sv");
        double[] dose = {3, 5, 8, 12};
        double last = 0;
        for (double d : dose) {
            double v = RadiationSickness.damagePerSecond(d);
            if (!(v > last)) throw new GameTestAssertException("Schaden steigt nicht bei " + d + " Sv: " + v + " <= " + last);
            last = v;
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "sick_vomit", timeoutTicks = 20)
    public static void vomitStopsPlayer(GameTestHelper h) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), TEST_PROFILE);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        sp.setPos(at.x, at.y, at.z);
        sp.removeAllEffects();
        if (!RadiationSickness.vomit(h.getLevel(), sp)) throw new GameTestAssertException("Anfall nicht ausgefuehrt");
        MobEffectInstance slow = sp.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow == null || slow.getAmplifier() < 5) throw new GameTestAssertException("Spieler bleibt nicht stehen: " + slow);
        if (sp.getEffect(MobEffects.CONFUSION) == null) throw new GameTestAssertException("Keine Uebelkeit nach dem Erbrechen");
        sp.removeAllEffects();

        RandomSource r = RandomSource.create(42L);
        if (RadiationSickness.vomitIntervalTicks(1, r) != 0) throw new GameTestAssertException("Erbrechen schon in Stufe 1");
        for (int i = 0; i < 50; i++) {
            int t = RadiationSickness.vomitIntervalTicks(2, r);
            if (t < 1200 || t > 2400) throw new GameTestAssertException("Abstand Stufe 2 ausserhalb 60-120 s: " + t);
        }
        h.succeed();
    }
}
