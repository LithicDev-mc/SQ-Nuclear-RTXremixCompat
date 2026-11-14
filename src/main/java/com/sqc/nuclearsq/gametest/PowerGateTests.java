package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.transport.PowerGateBlock;
import com.sqc.nuclearsq.transport.PowerGateBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PowerGateTests {
    private PowerGateTests() {}

    private static final String FLOOR = "floor";

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    private static PowerGateBlockEntity gate(GameTestHelper h, BlockPos rel, Direction facing) {
        h.setBlock(rel, TransportRegistry.POWER_GATE.get().defaultBlockState().setValue(PowerGateBlock.FACING, facing));
        if (!(h.getBlockEntity(rel) instanceof PowerGateBlockEntity be)) throw new GameTestAssertException("kein Netztrenner-Block-Entity");
        return be;
    }

    private static IEnergyStorage cap(GameTestHelper h, BlockPos rel, Direction side) {
        return h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(rel), side);
    }

    @GameTest(template = FLOOR, batch = "power_gate", timeoutTicks = 40)
    public static void gateSidesAndRate(GameTestHelper h) {
        BlockPos p = new BlockPos(3, 2, 3);
        PowerGateBlockEntity be = gate(h, p, Direction.EAST);
        IEnergyStorage in = cap(h, p, Direction.WEST), out = cap(h, p, Direction.EAST);
        if (in == null || !in.canReceive()) fail("Eingang hinten fehlt");
        if (out == null || out.canReceive() || out.receiveEnergy(1000, true) != 0) fail("Ausgang vorn nimmt Strom an");
        if (cap(h, p, Direction.NORTH) != null || cap(h, p, Direction.UP) != null) fail("Seite hat einen Anschluss");
        be.setRatePct(50);
        int rate = PowerGateBlockEntity.MAX_RATE / 2;
        if (be.rate() != rate) fail("Rate " + be.rate() + " statt " + rate);
        if (in.receiveEnergy(rate * 3, false) != rate) fail("Eingang ueber der Durchsatzgrenze");
        if (in.receiveEnergy(1, false) != 0) fail("Zweite Aufnahme im selben Tick");
        be.setRatePct(1);
        if (be.ratePct() != PowerGateBlockEntity.STEP) fail("Durchsatz unter dem Mindestwert");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_gate", timeoutTicks = 60)
    public static void gateChainsAndRegulates(GameTestHelper h) {
        BlockPos pa = new BlockPos(3, 2, 3), pb = new BlockPos(4, 2, 3);
        PowerGateBlockEntity a = gate(h, pa, Direction.EAST), b = gate(h, pb, Direction.EAST);
        int cap = PowerGateBlockEntity.CAPACITY;
        a.setRatePct(25);
        int rate = a.rate();
        a.setThreshold(2, 10);
        a.setThreshold(3, 50);
        b.setThreshold(1, 20);
        b.setThreshold(0, 10);
        IEnergyStorage ain = cap(h, pa, Direction.WEST);
        for (int i = 0; i < 40; i++) {
            ain.receiveEnergy(rate, false);
            a.serverTick();
            b.serverTick();
        }
        long limit = (long) cap * 20 / 100;
        if (b.stored() > limit) fail("B ueber Laden-bis: " + b.stored() + " > " + limit);
        if (a.stored() * 100L < (long) cap * 10) fail("A unter der Reserve: " + a.stored());
        if (b.stored() <= 0) fail("Nichts angekommen");
        if (b.lastMoved() < 0 || a.lastMoved() > rate) fail("Weitergabe ueber der Rate: " + a.lastMoved());
        net.minecraft.nbt.CompoundTag tag = a.saveWithoutMetadata(h.getLevel().registryAccess());
        if (tag.getInt("Stored") != a.stored() || tag.getIntArray("Thresh").length != 4) fail("Speichern unvollstaendig");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_gate", timeoutTicks = 20)
    public static void gateThresholdsStayOrdered(GameTestHelper h) {
        PowerGateBlockEntity be = gate(h, new BlockPos(3, 2, 3), Direction.NORTH);
        if (be.threshold(0) != 100 || be.threshold(1) != 100 || be.threshold(2) != 0 || be.threshold(3) != 0) fail("Werte ab Werk");
        be.setThreshold(0, 40);
        be.setThreshold(1, 20);
        if (be.threshold(0) != 20 || be.threshold(1) != 20) fail("Laden unter/bis nicht geordnet");
        be.setThreshold(3, 30);
        be.setThreshold(2, 70);
        if (be.threshold(2) != 70 || be.threshold(3) != 70) fail("Sperre/Freigabe nicht geordnet");
        be.setThreshold(1, 500);
        if (be.threshold(1) != 100) fail("nicht auf 100 begrenzt");
        h.succeed();
    }
}
