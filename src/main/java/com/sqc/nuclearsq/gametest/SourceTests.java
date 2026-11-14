package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SourceTests {
    private SourceTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "source_rod", timeoutTicks = 20)
    public static void spentRodRadiatesMoreThanFresh(GameTestHelper h) {
        int u = FuelRodBlockEntity.UNIT;
        double fresh = FuelRodBlockEntity.svH(u, false), half = FuelRodBlockEntity.svH(u / 2, false);
        double spent = FuelRodBlockEntity.svH(0, false), cooled = FuelRodBlockEntity.svH(0, true);
        if (!(fresh < half && half < spent)) {
            throw new GameTestAssertException("Reihenfolge frisch " + fresh + " < halb " + half + " < leer " + spent + " verletzt");
        }
        if (!(cooled < spent)) throw new GameTestAssertException("Abgeklungen " + cooled + " nicht unter abgebrannt " + spent);
        h.succeed();
    }
}
