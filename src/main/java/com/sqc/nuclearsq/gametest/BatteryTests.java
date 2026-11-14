package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigBattery;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BatteryTests {
    private BatteryTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static BigBattery battery(BigMachineBlockEntity be) {
        if (be.battery() == null) throw new GameTestAssertException(be.machine().id + ": kein Akku-Speicher");
        return be.battery();
    }

    private static IEnergyStorage port(BigMachineBlockEntity be, String name) {
        IEnergyStorage s = be.energyAt(name);
        if (s == null) throw new GameTestAssertException(be.machine().id + "." + name + ": kein Strom-Anschluss");
        return s;
    }

    @GameTest(template = FLOOR, batch = "battery_charge", timeoutTicks = 60)
    public static void batteryChargesWithRateLimit(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        h.runAfterDelay(25, () -> {
            formed(be);
            IEnergyStorage in = port(be, "energy_in"), out = port(be, "energy_out");
            int rate = BigMachine.BATTERY_SMALL.port("energy_in").rate();
            int got = in.receiveEnergy(rate * 10, false);
            if (got != rate) throw new GameTestAssertException("Eingang nimmt " + got + " statt " + rate + " FE in einem Tick");
            if (in.receiveEnergy(rate, false) != 0) throw new GameTestAssertException("Eingang nimmt mehr als die Rate je Tick");
            if (in.extractEnergy(1000, true) != 0 || !in.canReceive() || in.canExtract()) throw new GameTestAssertException("Eingang gibt Strom ab");
            if (out.receiveEnergy(1000, true) != 0) throw new GameTestAssertException("Ausgang nimmt Strom an");
            if (be.energy().getEnergyStored() != rate) throw new GameTestAssertException("Speicher " + be.energy().getEnergyStored() + " statt " + rate);
            battery(be).setPortOn(0, false);
            h.runAfterDelay(2, () -> {
                if (in.receiveEnergy(1000, false) != 0) throw new GameTestAssertException("Abgeschalteter Eingang nimmt Strom an");
                battery(be).setPortOn(0, true);
                if (in.receiveEnergy(1000, false) != 1000) throw new GameTestAssertException("Eingang nach dem Einschalten zu");
                if (be.chargeLevel() != 1) throw new GameTestAssertException("Ladestand " + be.chargeLevel() + " statt 1");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "battery_discharge", timeoutTicks = 120)
    public static void batteryDischargesThroughOutput(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        BigMachineBlockEntity sink = TestSinks.battery(h, new BlockPos(5, 2, 4), Direction.WEST);
        int rate = BigMachine.BATTERY_SMALL.port("energy_out").rate();
        h.runAfterDelay(25, () -> {
            formed(be);
            be.energy().setStored(1_000_000);
            formed(sink);
            h.runAfterDelay(20, () -> {
                int moved = 1_000_000 - be.energy().getEnergyStored(), got = sink.energy().getEnergyStored();
                if (got <= 0) throw new GameTestAssertException("Ausgang gibt nichts ab");
                if (got != moved) throw new GameTestAssertException("Abgabe " + moved + " FE, angekommen " + got);
                if (moved > rate * 21) throw new GameTestAssertException("Ausgang schneller als " + rate + " FE/t: " + moved);
                battery(be).setMode(2);
                h.setBlock(new BlockPos(3, 2, 2), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
                h.runAfterDelay(3, () -> {
                    int before = sink.energy().getEnergyStored();
                    h.runAfterDelay(10, () -> {
                        if (sink.energy().getEnergyStored() != before) throw new GameTestAssertException("Redstone sperrt den Ausgang nicht");
                        if (!battery(be).held()) throw new GameTestAssertException("Akku meldet keine Sperre");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "battery_ports", timeoutTicks = 120)
    public static void batteryPortsSwitchDirection(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        BigMachineBlockEntity sink = TestSinks.battery(h, new BlockPos(2, 2, 4), Direction.EAST);
        h.runAfterDelay(25, () -> {
            formed(be);
            formed(sink);
            IEnergyStorage in = port(be, "energy_in"), out = port(be, "energy_out");
            int rate = BigMachine.BATTERY_SMALL.port("energy_in").rate();
            battery(be).setPortOut(0, true);
            battery(be).setPortOut(1, false);
            if (!battery(be).isOutput(BigMachine.BATTERY_SMALL.port("energy_in")) || battery(be).portOut(1)) {
                throw new GameTestAssertException("Richtung nicht umgestellt");
            }
            if (in.canReceive() || !in.canExtract() || in.receiveEnergy(1000, true) != 0) throw new GameTestAssertException("Umgedrehter Eingang nimmt noch auf");
            if (out.canExtract() || out.extractEnergy(1000, true) != 0) throw new GameTestAssertException("Umgedrehter Ausgang gibt noch ab");
            if (out.receiveEnergy(rate * 2, false) != rate) throw new GameTestAssertException("Umgedrehter Ausgang nimmt nicht mit der Rate auf");
            be.energy().setStored(1_000_000);
            var copy = battery(be).save();
            if (!copy.getBoolean("out0") || copy.getBoolean("out1")) throw new GameTestAssertException("Richtung wird nicht gespeichert: " + copy);
            h.runAfterDelay(20, () -> {
                int got = sink.energy().getEnergyStored();
                if (got <= 0) throw new GameTestAssertException("Umgedrehter Eingang gibt nichts ab");
                if (got > rate * 21) throw new GameTestAssertException("Abgabe schneller als " + rate + " FE/t: " + got);
                battery(be).setPortOut(0, false);
                battery(be).setPortOut(1, true);
                if (!in.canReceive() || out.canReceive()) throw new GameTestAssertException("Zurueckstellen wirkt nicht");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "battery_ports", timeoutTicks = 60)
    public static void batteriesHaveMonitorAndDataPort(GameTestHelper h) {
        BigMachineBlockEntity small = build(h, BigMachine.BATTERY_SMALL, new BlockPos(1, 2, 1));
        BigMachineBlockEntity medium = build(h, BigMachine.BATTERY_MEDIUM, new BlockPos(5, 2, 1));
        BigMachineBlockEntity large = build(h, BigMachine.BATTERY_LARGE, new BlockPos(1, 2, 5));
        h.runAfterDelay(25, () -> {
            for (BigMachineBlockEntity be : List.of(small, medium, large)) {
                formed(be);
                BigMachine m = be.machine();
                if (m.monitor == null) throw new GameTestAssertException(m.id + ": kein Monitor");
                if (m.monitor[1] > 1.5) throw new GameTestAssertException(m.id + ": Monitor nicht unten (" + m.monitor[1] + ")");
                BigMachine.Port data = m.port("data");
                if (data == null || data.kind() != com.sqc.nuclearsq.machine.big.BigPortKind.TERMINAL) {
                    throw new GameTestAssertException(m.id + ": kein Datenanschluss");
                }
                if (data.y() != m.h - 1) throw new GameTestAssertException(m.id + ": Datenanschluss nicht oben (y " + data.y() + ")");
                BlockPos pos = m.structure.world(be.getBlockPos(), 0, data.x(), data.y(), data.z());
                if (!(h.getLevel().getBlockState(pos).getBlock() instanceof com.sqc.nuclearsq.machine.big.BigPortBlock pb)
                        || pb.kind() != com.sqc.nuclearsq.machine.big.BigPortKind.TERMINAL) {
                    throw new GameTestAssertException(m.id + ": kein Terminal-Port bei " + pos);
                }
                if (!com.sqc.nuclearsq.data.DataSources.isPort(h.getLevel(), pos)) throw new GameTestAssertException(m.id + ": Datenanschluss nicht im Datennetz");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "battery_capacity", timeoutTicks = 60)
    public static void batteryCapacities(GameTestHelper h) {
        BigMachineBlockEntity small = build(h, BigMachine.BATTERY_SMALL, new BlockPos(1, 2, 1));
        BigMachineBlockEntity medium = build(h, BigMachine.BATTERY_MEDIUM, new BlockPos(5, 2, 1));
        BigMachineBlockEntity large = build(h, BigMachine.BATTERY_LARGE, new BlockPos(1, 2, 5));
        h.runAfterDelay(25, () -> {
            int[][] want = {{4_000_000, 4000}, {16_000_000, 16000}, {64_000_000, 131072}};
            List<BigMachineBlockEntity> all = List.of(small, medium, large);
            for (int i = 0; i < 3; i++) {
                BigMachineBlockEntity be = all.get(i);
                formed(be);
                if (be.energy().getMaxEnergyStored() != want[i][0]) {
                    throw new GameTestAssertException(be.machine().id + ": Speicher " + be.energy().getMaxEnergyStored() + " statt " + want[i][0]);
                }
                for (BigMachine.Port p : battery(be).ports()) {
                    if (p.rate() != want[i][1]) throw new GameTestAssertException(be.machine().id + "." + p.name() + ": Rate " + p.rate());
                }
                be.energy().setStored(want[i][0] - 100);
                int got = port(be, "energy_in").receiveEnergy(want[i][1], false);
                if (got != 100 || be.energy().getEnergyStored() != want[i][0]) throw new GameTestAssertException(be.machine().id + ": voll nimmt " + got);
                if (be.chargeLevel() != 8) throw new GameTestAssertException(be.machine().id + ": Ladestand voll = " + be.chargeLevel());
            }
            if (battery(large).ports().size() != 4 || port(large, "energy_in_2") == null || port(large, "energy_out_2") == null) {
                throw new GameTestAssertException("Grosser Akku: zweite Anschluesse fehlen");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "battery_kit", timeoutTicks = 80)
    public static void batteryKitKeepsCharge(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("battery_medium"), h.absolutePos(new BlockPos(4, 2, 3)), Direction.SOUTH);
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(25, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Kein Controller");
            formed(be);
            be.energy().setStored(1_234_567);
            MachineKits.dismantle(h.getLevel(), pl, true);
            List<ItemEntity> kits = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(6),
                    e -> e.getItem().is(KitRegistry.KITS.get("battery_medium").get()));
            if (kits.size() != 1) throw new GameTestAssertException("Kits: " + kits.size());
            ItemStack kit = kits.get(0).getItem();
            int fe = kit.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0);
            if (fe != 1_234_567) throw new GameTestAssertException("Ladung im Kit: " + fe);
            kits.get(0).discard();
            MachineKits.place(h.getLevel(), pl);
            MachineKits.restoreEnergy(h.getLevel(), pl, fe);
            h.runAfterDelay(25, () -> {
                if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity again)) throw new GameTestAssertException("Kein Controller");
                formed(again);
                if (again.energy().getEnergyStored() != 1_234_567) throw new GameTestAssertException("Ladung nach dem Aufstellen: " + again.energy().getEnergyStored());
                h.succeed();
            });
        });
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "battery_regulator", timeoutTicks = 60)
    public static void batteryRegulatorDefaultsAndSave(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        h.runAfterDelay(25, () -> {
            formed(be);
            BigBattery b = battery(be);
            if (b.threshold(0) != 100 || b.threshold(1) != 100 || b.threshold(2) != 0 || b.threshold(3) != 0) fail("Schwellen ab Werk falsch");
            if (!b.charging() || !b.releasing()) fail("Regler ab Werk nicht offen");
            b.setThreshold(0, 40);
            b.setThreshold(1, 20);
            if (b.threshold(0) != 20 || b.threshold(1) != 20) fail("Laden unter/bis nicht geordnet: " + b.threshold(0) + "/" + b.threshold(1));
            b.setThreshold(3, 30);
            b.setThreshold(2, 70);
            if (b.threshold(2) != 70 || b.threshold(3) != 70) fail("Sperre/Freigabe nicht geordnet");
            b.setThreshold(1, 500);
            if (b.threshold(1) != 100) fail("Schwelle nicht auf 100 begrenzt");
            b.setThreshold(0, 25);
            b.setThreshold(1, 75);
            b.setThreshold(2, 10);
            b.setThreshold(3, 60);
            net.minecraft.nbt.CompoundTag tag = b.save();
            b.setThreshold(0, 0);
            b.setThreshold(1, 0);
            b.load(tag);
            if (b.threshold(0) != 25 || b.threshold(1) != 75 || b.threshold(2) != 10 || b.threshold(3) != 60) fail("Schwellen nach dem Laden falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "battery_regulator", timeoutTicks = 60)
    public static void batteryChargesOnlyBetweenThresholds(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        h.runAfterDelay(25, () -> {
            formed(be);
            BigBattery b = battery(be);
            IEnergyStorage in = port(be, "energy_in");
            int cap = be.energy().getMaxEnergyStored(), rate = BigMachine.BATTERY_SMALL.port("energy_in").rate();
            be.energy().setStored(0);
            b.setThreshold(1, 50);
            b.setThreshold(0, 30);
            for (int i = 0; i < cap / rate; i++) {
                b.tick(false);
                in.receiveEnergy(rate, false);
            }
            b.tick(false);
            long half = cap * 50L / 100;
            if (be.energy().getEnergyStored() != half) fail("Speicher " + be.energy().getEnergyStored() + " statt " + half + " (Laden bis 50 %)");
            if (b.charging() || in.canReceive() || in.receiveEnergy(rate, true) != 0) fail("Eingang nimmt ueber der Schwelle weiter auf");
            be.energy().setStored((int) (cap * 40L / 100));
            b.tick(false);
            if (b.charging() || in.receiveEnergy(rate, true) != 0) fail("Zwischen den Schwellen darf er nicht laden (Hysterese)");
            be.energy().setStored((int) (cap * 30L / 100));
            b.tick(false);
            if (!b.charging() || in.receiveEnergy(rate, true) != rate) fail("Auf Laden-unter laedt er nicht wieder");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "battery_regulator", timeoutTicks = 60)
    public static void batteryReleasesOnlyAboveReserve(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BATTERY_SMALL, new BlockPos(3, 2, 3));
        h.runAfterDelay(25, () -> {
            formed(be);
            BigBattery b = battery(be);
            IEnergyStorage out = port(be, "energy_out");
            int cap = be.energy().getMaxEnergyStored(), rate = BigMachine.BATTERY_SMALL.port("energy_out").rate();
            be.energy().setStored(cap / 2);
            b.setThreshold(3, 60);
            b.setThreshold(2, 20);
            long reserve = cap * 20L / 100;
            for (int i = 0; i < cap; i++) {
                b.tick(false);
                if (out.extractEnergy(rate, false) == 0) break;
            }
            b.tick(false);
            if (be.energy().getEnergyStored() != reserve) fail("Speicher " + be.energy().getEnergyStored() + " statt Reserve " + reserve);
            if (b.releasing() || out.canExtract() || out.extractEnergy(rate, true) != 0) fail("Ausgang gibt unter der Sperre noch ab");
            be.energy().setStored((int) (cap * 50L / 100));
            b.tick(false);
            if (b.releasing() || out.extractEnergy(rate, true) != 0) fail("Zwischen Sperre und Freigabe darf er nicht abgeben (Hysterese)");
            be.energy().setStored((int) (cap * 60L / 100));
            b.tick(false);
            if (!b.releasing() || out.extractEnergy(rate, true) != rate) fail("Bei der Freigabe gibt er nicht wieder ab");
            h.succeed();
        });
    }
}
