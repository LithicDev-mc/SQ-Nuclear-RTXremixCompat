package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class HuntReichweiteTests {
    private HuntReichweiteTests() {}

    private static MachineKits.Placement place(GameTestHelper h, MachineKits.Placement pl) {
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(pl.def().id() + ": " + blocked.size() + " Felder belegt");
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    @GameTest(template = "floor", batch = "hunt_reichweite", timeoutTicks = 300)
    public static void pumpOnContainmentFloorFeedsDrive(GameTestHelper h) {
        MachineKits.Placement drivePl = MachineKits.placement(MachineKits.def(BigMachine.CONTROL_ROD_DRIVE.id),
                h.absolutePos(new BlockPos(34, 60, 4)), Direction.SOUTH);
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        int reach = (BigMachine.CONTAINMENT.w - BigMachine.CONTROL_ROD_DRIVE.w) / 2 + 2;
        for (BlockPos p : drivePl.footprint()) {
            for (int dx = -reach; dx <= reach; dx += reach) {
                for (int dz = -reach; dz <= reach; dz += reach) chunks.add(net.minecraft.world.level.ChunkPos.asLong((p.getX() + dx) >> 4, (p.getZ() + dz) >> 4));
            }
        }
        for (long c : chunks) h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), true);
        place(h, drivePl);
        BigMachineBlockEntity drive = (BigMachineBlockEntity) h.getLevel().getBlockEntity(drivePl.anchor());
        MachineKits.Placement[] cont = new MachineKits.Placement[1];
        BigMachineBlockEntity[] pump = new BigMachineBlockEntity[1];
        h.runAfterDelay(25, () -> {
            if (!drive.isFormed()) throw new GameTestAssertException("Antrieb nicht aufgebaut");
            BlockPos top = BigMachine.CONTROL_ROD_DRIVE.structure.world(drive.getBlockPos(), drivePl.rot(), 1, BigMachine.CONTROL_ROD_DRIVE.h - 1, 3);
            cont[0] = place(h, MachineKits.placement(h.getLevel(), MachineKits.def(BigMachine.CONTAINMENT.id), top, Direction.WEST));
        });
        h.runAfterDelay(55, () -> {
            BigMachineBlockEntity c = (BigMachineBlockEntity) h.getLevel().getBlockEntity(cont[0].anchor());
            if (!c.isFormed()) throw new GameTestAssertException("Huelle nicht aufgebaut");
            if (drive.containmentOnTop() != c) throw new GameTestAssertException("Antrieb erkennt die Huelle nicht");
            BlockPos min = BigMachine.CONTAINMENT.structure.world(c.getBlockPos(), cont[0].rot(), 9, 0, 14);
            BigMachine m = BigMachine.MAIN_COOLANT_PUMP;
            BlockPos ctrl = min.subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
            for (MultiblockStructure.Cell cell : m.structure.cells(ctrl, 0)) {
                if (!h.getLevel().getBlockState(cell.pos()).isAir()) throw new GameTestAssertException("Pumpenfeld belegt: " + cell.pos());
                h.getLevel().setBlockAndUpdate(cell.pos(), cell.block().defaultBlockState());
            }
            pump[0] = (BigMachineBlockEntity) h.getLevel().getBlockEntity(ctrl);
            while (pump[0].energy().receiveEnergy(1000, false) > 0) { }
            pump[0].tank("primary_in").fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), 64000), IFluidHandler.FluidAction.EXECUTE);
        });
        int[] flow = {0};
        h.onEachTick(() -> {
            if (pump[0] != null) {
                flow[0] = Math.max(flow[0], drive.pumpFlow());
                pump[0].tank("primary_out").drain(100000, IFluidHandler.FluidAction.EXECUTE);
            }
        });
        h.runAfterDelay(100, () -> {
            BigMachineBlockEntity p = pump[0];
            if (!p.isFormed()) throw new GameTestAssertException("Pumpe nicht aufgebaut");
            net.minecraft.world.phys.Vec3 pc = net.minecraft.world.phys.Vec3.atCenterOf(
                    m(p).structure.world(p.getBlockPos(), 0, m(p).w / 2, m(p).h / 2, m(p).d / 2));
            double dist = drive.coreCenter().distanceTo(pc);
            double range = BigMachineBlockEntity.REACTOR_RANGE + 1.5 + 2.5;
            for (long ch : chunks) h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(ch), net.minecraft.world.level.ChunkPos.getZ(ch), false);
            if (flow[0] <= 0) throw new GameTestAssertException("HUNT: Pumpe in der Huelle meldet keinen Durchfluss (pumpFlow "
                    + flow[0] + "), Abstand Pumpenmitte-Kernmitte " + String.format("%.2f", dist) + " > Reichweite " + range
                    + ", Pumpe laeuft=" + p.isRunning() + ", Zustand=" + p.stateKey());
            h.succeed();
        });
    }

    private static BigMachine m(BigMachineBlockEntity be) { return be.machine(); }
}
