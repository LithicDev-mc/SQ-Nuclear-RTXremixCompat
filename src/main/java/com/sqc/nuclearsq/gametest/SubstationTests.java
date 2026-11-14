package com.sqc.nuclearsq.gametest;


import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.TestSourceRegistry;
import com.sqc.nuclearsq.transport.GridNode;
import com.sqc.nuclearsq.transport.GridNodeBlockEntity;
import com.sqc.nuclearsq.transport.GridRegistry;
import com.sqc.nuclearsq.transport.PowerMastBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SubstationTests {
    private SubstationTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity substation(GameTestHelper h, BlockPos min) {
        MultiblockStructure s = BigMachine.SUBSTATION.structure;
        BlockPos ctrl = h.absolutePos(min).subtract(s.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : s.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Umspannwerk ohne Controller");
        return be;
    }

    private static BlockPos port(BigMachineBlockEntity be, String name) {
        BigMachine.Port p = be.machine().port(name);
        return be.machine().structure.world(be.getBlockPos(), 0, p.x(), p.y(), p.z());
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static BigMachineBlockEntity sinkAt(GameTestHelper h, BigMachineBlockEntity sub, String out, Direction side) {
        return TestSinks.batteryAt(h, port(sub, out).relative(side), side.getOpposite());
    }

    @GameTest(template = FLOOR, batch = "substation_out", timeoutTicks = 200)
    public static void substationFeedsCablesDirectly(GameTestHelper h) {
        BigMachineBlockEntity sub = substation(h, new BlockPos(1, 2, 3));
        h.getLevel().setBlockAndUpdate(port(sub, "energy_in_0").north(), TestSourceRegistry.ENERGY_SOURCE.get().defaultBlockState());
        BigMachineBlockEntity sink = sinkAt(h, sub, "energy_out_1", Direction.EAST);
        h.runAfterDelay(80, () -> {
            formed(sub);
            if (sub.substation().targets().size() != 1) throw new GameTestAssertException("Umspannwerk findet " + sub.substation().targets().size() + " Ziele statt 1 (sich selbst)");
            if (sink.energy().getEnergyStored() <= 0) throw new GameTestAssertException("FE kommt nicht am Akku an");
            if (sub.substation().supply() > sub.substation().lineRate()) throw new GameTestAssertException("Abgabe-Puffer ueber line_rate");
            var out = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, port(sub, "energy_out_1"), Direction.EAST);
            if (out == null || out.receiveEnergy(1000, true) != 0) throw new GameTestAssertException("Strom-Ausgang fehlt oder nimmt Strom an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "substation_line", timeoutTicks = 240)
    public static void overheadLineBetweenSubstations(GameTestHelper h) {
        BigMachineBlockEntity a = substation(h, new BlockPos(0, 2, 2)), b = substation(h, new BlockPos(10, 2, 2));
        h.getLevel().setBlockAndUpdate(port(a, "energy_in_0").north(), TestSourceRegistry.ENERGY_SOURCE.get().defaultBlockState());
        Block mast = GridRegistry.MAST.get();
        BlockPos base = new BlockPos(7, 2, 4);
        BlockState ms = mast.defaultBlockState();
        h.setBlock(base, ms);
        mast.setPlacedBy(h.getLevel(), h.absolutePos(base), ms, null, ItemStack.EMPTY);
        GridNodeBlockEntity m = h.getBlockEntity(base);
        GridNode.link(a.substation(), m);
        GridNode.link(m, b.substation());
        BigMachineBlockEntity sink = sinkAt(h, b, "energy_out_2", Direction.SOUTH);
        if (!h.getBlockState(base.above(PowerMastBlock.HEIGHT - 1)).is(mast)) throw new GameTestAssertException("Mast steht nicht in voller Hoehe");
        h.runAfterDelay(80, () -> {
            formed(a);
            formed(b);
            if (a.substation().targets().size() != 2) throw new GameTestAssertException("Umspannwerk A findet " + a.substation().targets().size() + " Ziele statt 2");
            if (sink.energy().getEnergyStored() <= 0) throw new GameTestAssertException("Strom kommt nicht ueber die Leitung am Akku an");
            if (GridNode.at(h.getLevel(), port(b, "energy_out_3")) != b.substation()) throw new GameTestAssertException("GridNode.at findet das Umspannwerk nicht ueber einen Port");
            h.destroyBlock(base.above(5));
            h.runAfterDelay(2, () -> {
                if (h.getBlockState(base).is(mast)) throw new GameTestAssertException("Mast faellt nicht ganz");
                if (!a.substation().links().isEmpty() || !b.substation().links().isEmpty()) throw new GameTestAssertException("Seile haengen noch am Umspannwerk");
                h.assertItemEntityPresent(GridRegistry.WIRE.get(), base, 3);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "substation_break", timeoutTicks = 60)
    public static void brokenSubstationDropsWires(GameTestHelper h) {
        BigMachineBlockEntity sub = substation(h, new BlockPos(1, 2, 1));
        Block mast = GridRegistry.MAST.get();
        BlockPos base = new BlockPos(12, 2, 10);
        h.setBlock(base, mast.defaultBlockState());
        GridNodeBlockEntity m = h.getBlockEntity(base);
        GridNode.link(sub.substation(), m);
        int cost = GridNode.wireCost(sub.getBlockPos(), h.absolutePos(base));
        BlockPos ctrl = sub.getBlockPos();
        h.runAfterDelay(25, () -> {
            h.getLevel().destroyBlock(ctrl, true);
            h.runAfterDelay(2, () -> {
                if (!m.links().isEmpty()) throw new GameTestAssertException("Mast haelt noch das Seil zum abgebauten Umspannwerk");
                int wires = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(ctrl).inflate(3))
                        .stream().filter(e -> e.getItem().is(GridRegistry.WIRE.get())).mapToInt(e -> e.getItem().getCount()).sum();
                if (wires != cost) throw new GameTestAssertException(wires + " statt " + cost + " Leiterseile");
                h.succeed();
            });
        });
    }
}
