package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.PowerRegistry;
import com.sqc.nuclearsq.registry.TestSourceRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentCableTests {
    private ContainmentCableTests() {}

    private static final String FLOOR = "floor";

    private static MachineKits.Placement hull(GameTestHelper h, int y) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.CONTAINMENT.id), h.absolutePos(new BlockPos(34, y, 4)), Direction.SOUTH);
        keepLoaded(h, pl);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(blocked.size() + " Felder der Huelle belegt, z. B. " + blocked.get(0));
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    private static void keepLoaded(GameTestHelper h, MachineKits.Placement pl) {
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        for (BlockPos p : pl.footprint()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) chunks.add(ChunkPos.asLong((p.getX() >> 4) + dx, (p.getZ() >> 4) + dz));
            }
        }
        for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
    }

    private static BlockPos port(MachineKits.Placement pl, String name) {
        BigMachine.Port p = BigMachine.CONTAINMENT.port(name);
        if (p == null) throw new GameTestAssertException("Huelle ohne Port " + name);
        return BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), p.x(), p.y(), p.z());
    }

    private static BigMachineBlockEntity controller(GameTestHelper h, MachineKits.Placement pl) {
        if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || !be.isFormed()) {
            throw new GameTestAssertException("Huelle nicht aufgebaut");
        }
        return be;
    }

    private static String dump(GameTestHelper h, MachineKits.Placement pl, BlockPos out, BlockPos in, Direction inward, BigMachineBlockEntity sink) {
        var lv = h.getLevel();
        BigMachineBlockEntity hull = lv.getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity b ? b : null;
        BlockPos bp = in.relative(inward);
        IEnergyStorage cap = lv.getCapability(Capabilities.EnergyStorage.BLOCK, bp, inward.getOpposite());
        IEnergyStorage capOut = lv.getCapability(Capabilities.EnergyStorage.BLOCK, out, inward);
        return " [hull energy=" + (hull == null ? "-" : hull.energy().getEnergyStored()) + " formed=" + (hull != null && hull.isFormed())
                + " innerPortBE=" + lv.getBlockEntity(in) + " sinkInPortBE=" + lv.getBlockEntity(bp) + " sinkInCap=" + (cap == null ? "null" : cap.canReceive() + "/" + cap.getEnergyStored())
                + " outerCap=" + (capOut == null ? "null" : capOut.canReceive() + "/" + capOut.getEnergyStored())
                + " srcBlock=" + lv.getBlockState(out.relative(inward.getOpposite())).getBlock()
                + " srcTicking=" + lv.isPositionEntityTicking(out.relative(inward.getOpposite())) + "]";
    }

    private static boolean formed(GameTestHelper h, MachineKits.Placement pl) {
        return h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be && be.isFormed();
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "containment_cable", timeoutTicks = 600)
    public static void dataCableLeadsThroughWall(GameTestHelper h) {
        MachineKits.Placement pl = hull(h, 60);
        boolean[] built = new boolean[1];
        BlockPos[] ends = new BlockPos[2];
        h.succeedWhen(() -> {
            keepLoaded(h, pl);
            check(formed(h, pl), "Huelle nicht aufgebaut");
            BlockPos out = port(pl, "data"), in = port(pl, "data_inner");
            if (!built[0]) {
                check(out.getY() == pl.anchor().getY(), "Datenanschluss nicht am untersten Ring");
                Direction inward = Direction.fromDelta(in.getX() - out.getX(), 0, in.getZ() - out.getZ());
                check(inward != null, "Datenanschluesse nicht nebeneinander: " + out + " " + in);
                h.getLevel().setBlockAndUpdate(in.relative(inward), DataRegistry.CABLE.get().defaultBlockState());
                h.getLevel().setBlockAndUpdate(in.relative(inward, 2), PowerRegistry.SOLAR_PANEL.get().defaultBlockState());
                h.getLevel().setBlockAndUpdate(out.relative(inward.getOpposite()), DataRegistry.CABLE.get().defaultBlockState());
                ends[0] = out.relative(inward.getOpposite(), 2);
                ends[1] = in.relative(inward, 2);
                h.getLevel().setBlockAndUpdate(ends[0], DataRegistry.TERMINAL.get().defaultBlockState());
                built[0] = true;
            }
            List<DataSource> net = DataNet.scan(h.getLevel(), ends[0]);
            boolean hull = net.stream().anyMatch(s -> s.pos().equals(pl.anchor()));
            boolean panel = net.stream().anyMatch(s -> s.pos().equals(ends[1]));
            check(hull && panel, "Netz aussen findet Huelle=" + hull + ", Anlage innen=" + panel + " (" + net.size() + " Anlagen)");
        });
    }

    @GameTest(template = FLOOR, batch = "containment_cable", timeoutTicks = 800)
    public static void powerCableLeadsThroughWall(GameTestHelper h) {
        MachineKits.Placement pl = hull(h, 100);
        BlockPos[] sinkAt = new BlockPos[1];
        h.succeedWhen(() -> {
            keepLoaded(h, pl);
            check(formed(h, pl), "Huelle nicht aufgebaut");
            BlockPos out = port(pl, "energy_in"), in = port(pl, "energy_out");
            Direction inward = Direction.fromDelta(in.getX() - out.getX(), 0, in.getZ() - out.getZ());
            if (sinkAt[0] == null) {
                check(out.getY() == pl.anchor().getY(), "Stromanschluss nicht am untersten Ring");
                check(inward != null, "Stromanschluesse nicht nebeneinander: " + out + " " + in);
                h.getLevel().setBlockAndUpdate(out.relative(inward.getOpposite()), TestSourceRegistry.ENERGY_SOURCE.get().defaultBlockState());
                sinkAt[0] = TestSinks.batteryAt(h, in.relative(inward), inward.getOpposite()).getBlockPos();
            }
            if (!(h.getLevel().getBlockEntity(sinkAt[0]) instanceof BigMachineBlockEntity sink) || !sink.isFormed()) {
                throw new GameTestAssertException("Akku innen nicht aufgebaut");
            }
            check(sink.energy().getEnergyStored() > 0, "Strom kommt innen nicht an" + dump(h, pl, out, in, inward, sink));
            IEnergyStorage outer = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, out, inward);
            check(outer != null && !outer.canExtract() && outer.extractEnergy(1000, true) == 0, "Aeusserer Stromanschluss gibt Strom ab");
        });
    }
}
