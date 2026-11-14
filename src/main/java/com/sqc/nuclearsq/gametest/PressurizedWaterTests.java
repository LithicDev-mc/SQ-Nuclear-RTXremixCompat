package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PressurizedWaterTests {
    private PressurizedWaterTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "pwr_basics", timeoutTicks = 20)
    public static void primaryPortAndFluidsExist(GameTestHelper h) {
        var port = BigMachineRegistry.PORTS.get(BigPortKind.PRIMARY);
        if (port == null) throw new GameTestAssertException("Port-Typ PRIMARY ohne Block");
        String id = BuiltInRegistries.BLOCK.getKey(port.get()).getPath();
        if (!id.equals("machine_port_primary_sqc")) throw new GameTestAssertException("Primaeranschluss heisst " + id);
        if (!BigPortKind.PRIMARY.isFluid()) throw new GameTestAssertException("Primaeranschluss ist kein Fluessigkeits-Port");
        for (ModFluids.Entry e : new ModFluids.Entry[] {ModFluids.PRIMARY_WATER, ModFluids.HOT_PRIMARY_WATER}) {
            if (!e.sealed() || e.gas() || e.bucket != null) throw new GameTestAssertException(e.id + " hat einen Eimer oder ist ein Gas");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "pwr_basics", timeoutTicks = 80)
    public static void hotPrimaryPipeRadiates(GameTestHelper h) {
        var pipe = TransportRegistry.PIPES.get(TransportTier.BASIC).get();
        BlockPos hot = new BlockPos(1, 2, 1), cold = new BlockPos(4, 2, 1);
        h.setBlock(hot, pipe);
        h.setBlock(cold, pipe);
        h.runAfterDelay(2, () -> {
            ConduitBlockEntity ph = h.getBlockEntity(hot), pc = h.getBlockEntity(cold);
            int a = ph.fluidSide(null).fill(new FluidStack(ModFluids.HOT_PRIMARY_WATER.source.get(), 500), IFluidHandler.FluidAction.EXECUTE);
            int b = pc.fluidSide(null).fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), 500), IFluidHandler.FluidAction.EXECUTE);
            if (a <= 0 || b <= 0) throw new GameTestAssertException("Rohr nimmt kein Primaerwasser an: heiss " + a + ", kalt " + b);
            h.runAfterDelay(45, () -> {
                var sl = h.getLevel();
                double sh = RadiationManager.emitted(sl, h.absolutePos(hot)), sc = RadiationManager.emitted(sl, h.absolutePos(cold));
                if (sh <= 0) throw new GameTestAssertException("Rohr mit heissem Primaerwasser strahlt nicht");
                if (sc > 0) throw new GameTestAssertException("Rohr mit kaltem Primaerwasser strahlt: " + sc);
                h.succeed();
            });
        });
    }
}
