package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.DeconShowerBlock;
import com.sqc.nuclearsq.radiation.DeconShowerBlockEntity;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DeconFogTests {
    private DeconFogTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "fog_limit", timeoutTicks = 20)
    public static void fogParticlesBounded(GameTestHelper h) {
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            int off = DeconShowerBlock.fogParticles(false, r);
            if (off != 0) throw new GameTestAssertException("Nebel ohne aktive Dusche: " + off);
            int on = DeconShowerBlock.fogParticles(true, r);
            if (on < 1 || on > DeconShowerBlock.FOG_PER_TICK) throw new GameTestAssertException("Nebel ausserhalb der Grenze: " + on);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "fog_idle", timeoutTicks = 60)
    public static void showerIdleWithoutPlayer(GameTestHelper h) {
        BlockPos shower = new BlockPos(4, 5, 4);
        h.setBlock(shower, ProtectionRegistry.SHOWER.get());
        DeconShowerBlockEntity be = h.getBlockEntity(shower);
        be.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(25, () -> {
            if (h.getBlockState(shower).getValue(DeconShowerBlock.ACTIVE)) throw new GameTestAssertException("Dusche laeuft ohne Spieler");
            if (be.tank().getFluidAmount() != 1000) throw new GameTestAssertException("Dusche verbraucht Wasser ohne Spieler");
            h.succeed();
        });
    }
}
