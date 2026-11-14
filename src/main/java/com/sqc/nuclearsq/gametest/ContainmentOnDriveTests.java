package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.RodShaft;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentOnDriveTests {
    private ContainmentOnDriveTests() {}

    private static final String FLOOR = "floor";

    private static MachineKits.Placement place(GameTestHelper h, MachineKits.Placement pl) {
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) {
            BlockPos b = blocked.get(0);
            throw new GameTestAssertException(pl.def().id() + ": " + blocked.size() + " Felder belegt, z. B. " + b + " " + h.getLevel().getBlockState(b));
        }
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    private static BigMachineBlockEntity controller(GameTestHelper h, MachineKits.Placement pl) {
        if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be)) {
            throw new GameTestAssertException(pl.def().id() + ": kein Controller");
        }
        return be;
    }

    private static void rod(GameTestHelper h, BigMachineBlockEntity drive, int shaft, int headY) {
        BlockPos head = drive.rodBase(drive.shafts()[shaft]).above(headY);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
    }

    @GameTest(template = FLOOR, batch = "containment_on_drive", timeoutTicks = 1000)
    public static void containmentSitsOnRodDriveAndPassesRods(GameTestHelper h) {
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
        BigMachineBlockEntity drive = controller(h, drivePl);
        int inlet = RodShaft.FULL_HEAD_Y + 2;
        int head = inlet + FuelRodHeadBlock.ROD_BLOCKS;
        MachineKits.Placement[] cont = new MachineKits.Placement[1];
        h.runAfterDelay(25, () -> {
            if (!drive.isFormed()) throw new GameTestAssertException("Antrieb nicht aufgebaut");
            if (drive.inletY() != RodShaft.FULL_HEAD_Y) throw new GameTestAssertException("Einlass ohne Huelle bei " + drive.inletY());
            while (drive.energy().receiveEnergy(1000, false) > 0) { }
            drive.tank("hydraulic_in").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
            BlockPos top = BigMachine.CONTROL_ROD_DRIVE.structure.world(drive.getBlockPos(), drivePl.rot(), 1, BigMachine.CONTROL_ROD_DRIVE.h - 1, 3);
            MachineKits.Placement pl = MachineKits.placement(h.getLevel(), MachineKits.def(BigMachine.CONTAINMENT.id), top, Direction.WEST);
            BlockPos want = BigMachineBlockEntity.containmentAnchor(drive.getBlockPos(), drivePl.rot());
            if (!pl.anchor().equals(want) || pl.rot() != drivePl.rot()) {
                throw new GameTestAssertException("Huelle rastet nicht ein: " + pl.anchor() + "/" + pl.rot() + ", erwartet " + want + "/" + drivePl.rot());
            }
            cont[0] = place(h, pl);
        });
        h.runAfterDelay(55, () -> {
            BigMachineBlockEntity c = controller(h, cont[0]);
            if (!c.isFormed()) throw new GameTestAssertException("Huelle um den Antrieb nicht aufgebaut (" + c.matched() + "/" + c.total() + ")");
            int topC = BigMachine.CONTAINMENT.structure.world(c.getBlockPos(), cont[0].rot(), 0, BigMachine.CONTAINMENT.h - 1, 0).getY();
            int topD = BigMachine.CONTROL_ROD_DRIVE.structure.world(drive.getBlockPos(), drivePl.rot(), 0, BigMachine.CONTROL_ROD_DRIVE.h - 1, 0).getY();
            if (topD != topC - 1) throw new GameTestAssertException("Antrieb endet auf Hoehe " + topD + ", Luke liegt auf " + topC);
            BlockPos slab = BigMachine.CONTAINMENT.structure.world(c.getBlockPos(), cont[0].rot(), 13, BigMachineBlockEntity.CONTAINMENT_DRIVE_Y - 1, 14);
            MachineKits.Placement dp = MachineKits.placement(h.getLevel(), MachineKits.def(BigMachine.CONTROL_ROD_DRIVE.id), slab, Direction.EAST);
            if (!dp.anchor().equals(drive.getBlockPos()) || dp.rot() != drivePl.rot()) {
                throw new GameTestAssertException("Antriebs-Kit rastet nicht auf dem Sockel ein: " + dp.anchor() + "/" + dp.rot() + ", erwartet " + drive.getBlockPos());
            }
            if (drive.containmentOnTop() != c) throw new GameTestAssertException("Antrieb erkennt die Huelle nicht");
            if (drive.inletY() != inlet) throw new GameTestAssertException("Einlass bei " + drive.inletY() + ", erwartet " + inlet);
            BlockPos bottom = drive.rodBase(drive.shafts()[0]).above(inlet);
            if (bottom.getY() != topC + 1) throw new GameTestAssertException("Stab-Unterkante auf " + bottom.getY() + ", Luke auf " + topC);
            if (!MachineKits.free(h.getLevel(), bottom)) throw new GameTestAssertException("Luke belegt: " + h.getLevel().getBlockState(bottom));
            rod(h, drive, 0, head);
        });
        h.runAfterDelay(70, () -> {
            if (drive.shafts()[0].headY() != head) throw new GameTestAssertException("Stab auf der Huelle nicht gefunden (" + drive.shafts()[0].headY() + ")");
            drive.setShaftLocked(0, true);
            drive.setRodTarget(15);
        });
        h.runAfterDelay(900, () -> {
            float shown = drive.shafts()[0].shown();
            if (shown < head - RodShaft.FULL_HEAD_Y - 0.01f) throw new GameTestAssertException("Stab nicht ganz durch die Huelle eingezogen: " + shown);
            for (long c : chunks) h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            h.succeed();
        });
    }
}
