package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.radiation.Shielding;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ShieldingBlockTests {
    private ShieldingBlockTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "shield_blocks", timeoutTicks = 20)
    public static void shieldingBlocksAreRecognized(GameTestHelper h) {
        double lead = Shielding.transmission(ProtectionRegistry.LEAD_BLOCK.get().defaultBlockState());
        if (lead != RadiationValues.SHIELD_LEAD) throw new GameTestAssertException("Bleiblock: " + lead);
        double concrete = Shielding.transmission(ProtectionRegistry.RADIATION_CONCRETE.get().defaultBlockState());
        if (concrete != RadiationValues.SHIELD_HEAVY_CONCRETE) throw new GameTestAssertException("Strahlenschutzbeton: " + concrete);
        double glass = Shielding.transmission(ProtectionRegistry.LEAD_GLASS.get().defaultBlockState());
        if (!(glass < 1.0)) throw new GameTestAssertException("Bleiglas schirmt nicht ab: " + glass);
        h.succeed();
    }
}
