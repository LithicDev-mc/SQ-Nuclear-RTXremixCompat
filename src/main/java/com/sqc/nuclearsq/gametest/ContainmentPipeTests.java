package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentPipeTests {
    private ContainmentPipeTests() {}

    private static final String FLOOR = "floor";

    private static MachineKits.Placement hull(GameTestHelper h, int y) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.CONTAINMENT.id), h.absolutePos(new BlockPos(34, y, 4)), Direction.SOUTH);
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        for (BlockPos p : pl.footprint()) chunks.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(blocked.size() + " Felder der Huelle belegt, z. B. " + blocked.get(0));
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "containment_pipe", timeoutTicks = 100)
    public static void primaryPortsSitAtLowestRing(GameTestHelper h) {
        BigMachine c = BigMachine.CONTAINMENT;
        for (String[] pair : new String[][] {{"relief_in", "relief_out", "160"}, {"hpi_in", "hpi_out", "320"}, {"emc_in", "emc_out", "80"}}) {
            BigMachine.Port a = c.port(pair[0]), b = c.port(pair[1]);
            check(a != null && b != null, "Port-Paar fehlt: " + pair[0]);
            check(a.y() == 0 && b.y() == 0, pair[0] + ": nicht am untersten Ring");
            check(Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1, pair[0] + ": Ports nicht nebeneinander");
            check(a.rate() == Integer.parseInt(pair[2]) && b.rate() == a.rate(), pair[0] + ": Rate " + a.rate() + "/" + b.rate() + " statt " + pair[2]);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "containment_pipe", timeoutTicks = 600)
    public static void primaryPipesLeadThroughWall(GameTestHelper h) {
        MachineKits.Placement pl = hull(h, 140);
        boolean[] filled = new boolean[1];
        h.succeedWhen(() -> {
            check(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be && be.isFormed(), "Huelle nicht aufgebaut");
            BigMachineBlockEntity be = (BigMachineBlockEntity) h.getLevel().getBlockEntity(pl.anchor());
            Fluid hot = ModFluids.HOT_PRIMARY_WATER.source.get(), cold = ModFluids.PRIMARY_WATER.source.get();
            if (!filled[0]) {
                check(be.tank("hpi_in").fill(new FluidStack(hot, 100), IFluidHandler.FluidAction.EXECUTE) == 0, "hpi_in nimmt heisses Primaerwasser");
                check(be.tank("relief_in").fill(new FluidStack(cold, 100), IFluidHandler.FluidAction.EXECUTE) == 0, "relief_in nimmt kaltes Primaerwasser");
                be.tank("relief_in").fill(new FluidStack(hot, 1600), IFluidHandler.FluidAction.EXECUTE);
                be.tank("hpi_in").fill(new FluidStack(cold, 3200), IFluidHandler.FluidAction.EXECUTE);
                be.tank("emc_in").fill(new FluidStack(cold, 800), IFluidHandler.FluidAction.EXECUTE);
                filled[0] = true;
            }
            check(be.tank("relief_out").getFluidAmount() + be.tank("relief_in").getFluidAmount() == 1600, "Abblasen verliert Fluessigkeit");
            check(be.tank("relief_out").getFluid().is(hot) || be.tank("relief_out").isEmpty(), "Abblasen: falsche Fluessigkeit aussen");
            check(be.tank("hpi_out").getFluid().is(cold) || be.tank("hpi_out").isEmpty(), "Hochdruck-Einspeisung: falsche Fluessigkeit innen");
            check(be.tank("relief_in").isEmpty() && be.tank("hpi_in").isEmpty() && be.tank("emc_in").isEmpty(), "Leitungen noch nicht durchgelaufen");
            check(be.tank("relief_out").getFluidAmount() == 1600 && be.tank("hpi_out").getFluidAmount() == 3200 && be.tank("emc_out").getFluidAmount() == 800,
                    "Menge kommt nicht an: " + be.tank("relief_out").getFluidAmount() + "/" + be.tank("hpi_out").getFluidAmount() + "/" + be.tank("emc_out").getFluidAmount());
        });
    }
}
