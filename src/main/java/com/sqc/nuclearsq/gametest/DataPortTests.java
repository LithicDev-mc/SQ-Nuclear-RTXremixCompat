package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlock;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataPortTests {
    private DataPortTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "data_ports", timeoutTicks = 20)
    public static void everyMachineHasDataPort(GameTestHelper h) {
        StringBuilder missing = new StringBuilder();
        for (BigMachine m : BigMachine.values()) {
            boolean ok = false;
            for (BigMachine.Port p : m.ports) if (p.kind() == BigPortKind.TERMINAL) ok = true;
            if (!ok) missing.append(' ').append(m.id);
        }
        if (missing.length() > 0) throw new GameTestAssertException("Ohne Datenanschluss:" + missing);
        h.succeed();
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        BlockPos lo = h.absolutePos(min);
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) {
            for (BlockPos p : BlockPos.betweenClosed(c.pos().offset(-2, 0, -2), c.pos().offset(2, 2, 2))) {
                if (p.getY() >= lo.getY()) h.getLevel().setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static void wired(GameTestHelper h, BigMachineBlockEntity be) {
        BigMachine m = be.machine();
        BigMachine.Port data = m.port("data");
        if (data == null || data.kind() != BigPortKind.TERMINAL) throw new GameTestAssertException(m.id + ": kein Datenanschluss");
        BlockPos pos = m.structure.world(be.getBlockPos(), 0, data.x(), data.y(), data.z());
        if (!(h.getLevel().getBlockState(pos).getBlock() instanceof BigPortBlock pb) || pb.kind() != BigPortKind.TERMINAL) {
            throw new GameTestAssertException(m.id + ": kein Terminal-Port bei " + pos);
        }
        if (!DataSources.isPort(h.getLevel(), pos)) throw new GameTestAssertException(m.id + ": Datenanschluss nicht im Datennetz");
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!h.getLevel().getBlockState(pos.relative(d)).isAir() || !h.getLevel().getBlockState(pos.relative(d, 2)).isAir()) continue;
            h.getLevel().setBlockAndUpdate(pos.relative(d), DataRegistry.CABLE.get().defaultBlockState());
            BlockPos term = pos.relative(d, 2);
            h.getLevel().setBlockAndUpdate(term, DataRegistry.TERMINAL.get().defaultBlockState());
            List<DataSource> net = DataNet.scan(h.getLevel(), term);
            if (net.size() != 1 || !net.get(0).pos().equals(be.getBlockPos())) {
                throw new GameTestAssertException(m.id + ": Netz am Datenanschluss findet " + net.size() + " Anlagen statt der Maschine");
            }
            return;
        }
        StringBuilder around = new StringBuilder();
        for (Direction d : Direction.values()) around.append(' ').append(d).append('=').append(h.getLevel().getBlockState(pos.relative(d)).getBlock());
        throw new GameTestAssertException(m.id + ": kein Platz fuer ein Kabel am Datenanschluss" + around);
    }

    @GameTest(template = FLOOR, batch = "data_ports", timeoutTicks = 60)
    public static void dataPortJoinsNetwork(GameTestHelper h) {
        BigMachineBlockEntity pump = build(h, BigMachine.MAIN_COOLANT_PUMP, new BlockPos(5, 2, 5));
        BigMachineBlockEntity substation = build(h, BigMachine.SUBSTATION, new BlockPos(10, 2, 9));
        h.runAfterDelay(5, () -> {
            wired(h, pump);
            wired(h, substation);
            h.succeed();
        });
    }
}
