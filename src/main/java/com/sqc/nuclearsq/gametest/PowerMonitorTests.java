package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.PowerFlow;
import com.sqc.nuclearsq.data.PowerMonitorBlock;
import com.sqc.nuclearsq.data.PowerMonitorBlockEntity;
import com.sqc.nuclearsq.data.PowerSample;
import com.sqc.nuclearsq.data.PowerStats;
import com.sqc.nuclearsq.data.DataValue;
import com.sqc.nuclearsq.machine.MachineEnergy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PowerMonitorTests {
    private PowerMonitorTests() {}

    private static final String FLOOR = "floor";

    private static PowerMonitorBlockEntity monitor(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, DataRegistry.POWER.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof PowerMonitorBlockEntity be)) {
            throw new GameTestAssertException("Kein Strom-Monitor bei " + rel);
        }
        return be;
    }

    private static DataSource plant(BlockPos pos, String name, PowerFlow flow) {
        return new DataSource() {
            @Override public BlockPos pos() { return pos; }
            @Override public Component name() { return Component.literal(name); }
            @Override public String type() { return "test"; }
            @Override public int state() { return OK; }
            @Override public Component stateText() { return Component.literal("ok"); }
            @Override public List<DataValue> values() { return List.of(); }
            @Override public void open(ServerPlayer player) { }
            @Override public PowerFlow power() { return flow; }
        };
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "power_monitor_counter", timeoutTicks = 20)
    public static void machineEnergyCountsDrain(GameTestHelper h) {
        MachineEnergy e = new MachineEnergy(1000, 1000);
        e.receiveEnergy(500, false);
        check(e.drainedTotal() == 0, "Zaehler zaehlt Einspeisung");
        check(e.drain(120) == 120 && e.drainedTotal() == 120, "Zaehler nach drain 120: " + e.drainedTotal());
        check(e.drain(1000) == 380 && e.drainedTotal() == 500, "drain ueber den Vorrat zaehlt nur das Entnommene: " + e.drainedTotal());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_sample", timeoutTicks = 20)
    public static void samplesProductionConsumptionAndStorage(GameTestHelper h) {
        PowerMonitorBlockEntity be = monitor(h, new BlockPos(2, 2, 2));
        BlockPos a = new BlockPos(10, 1, 1), b = new BlockPos(11, 1, 1), c = new BlockPos(12, 1, 1), d = new BlockPos(13, 1, 1), s1 = new BlockPos(14, 1, 1);
        long[] used = {0, 0};
        List<DataSource> net = new ArrayList<>();
        net.add(plant(a, "Turbine", PowerFlow.producer(1200, 2000)));
        net.add(plant(b, "Turbine", PowerFlow.producer(800, 2000)));
        net.add(new DataSource() {
            final DataSource base = plant(c, "Assembler", null);
            @Override public BlockPos pos() { return c; }
            @Override public Component name() { return base.name(); }
            @Override public String type() { return "test"; }
            @Override public int state() { return OK; }
            @Override public Component stateText() { return base.stateText(); }
            @Override public List<DataValue> values() { return List.of(); }
            @Override public void open(ServerPlayer player) { }
            @Override public PowerFlow power() { return PowerFlow.consumer(used[0], 0); }
        });
        net.add(new DataSource() {
            @Override public BlockPos pos() { return d; }
            @Override public Component name() { return Component.literal("Rack"); }
            @Override public String type() { return "test"; }
            @Override public int state() { return OK; }
            @Override public Component stateText() { return Component.literal("ok"); }
            @Override public List<DataValue> values() { return List.of(); }
            @Override public void open(ServerPlayer player) { }
            @Override public PowerFlow power() { return PowerFlow.consumer(used[1], 0); }
        });
        net.add(plant(s1, "Akku", PowerFlow.storage(250_000, 1_000_000, 300, 100)));
        be.sample(net, 1000);
        PowerSample first = be.latest();
        check(first.prod() == 2000 && first.cons() == 0, "Erste Messung: Verbrauch darf nicht aus dem Nichts kommen: " + first);
        used[0] = 600;
        used[1] = 100;
        be.sample(net, 1020);
        PowerSample s = be.latest();
        check(s.prod() == 2000, "Erzeugung " + s.prod());
        check(s.cons() == 35, "Verbrauch " + s.cons());
        check(s.balance() == 1965, "Bilanz " + s.balance());
        check(s.stored() == 250_000 && s.capacity() == 1_000_000 && s.in() == 300 && s.out() == 100, "Speicher " + s);
        var sum = be.summary();
        check(sum.plants() == 5 && sum.peak() == 4000, "Anlagen/Spitze: " + sum.plants() + "/" + sum.peak());
        check(sum.producers().size() == 1 && sum.producers().get(0).count() == 2 && sum.producers().get(0).a() == 2000, "Erzeuger werden nach Namen gruppiert");
        check(sum.consumers().size() == 2 && sum.consumers().get(0).name().getString().equals("Assembler") && sum.consumers().get(0).a() == 30,
                "Verbraucher nach Groesse sortiert: " + sum.consumers());
        check(sum.storages().size() == 1 && sum.storages().get(0).b() == 1_000_000, "Speicherliste");
        used[0] = 10;
        be.sample(net, 1040);
        check(be.latest().cons() == 0, "Zaehler-Ruecksprung oder gleicher Stand ergibt Rate 0: " + be.latest().cons());
        used[0] = 210;
        be.sample(net, 1060);
        check(be.latest().cons() == 10, "Verbrauch nach dem Ruecksprung: " + be.latest().cons());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_history", timeoutTicks = 20)
    public static void historyIsBounded(GameTestHelper h) {
        PowerMonitorBlockEntity be = monitor(h, new BlockPos(2, 2, 2));
        long[] prod = {0};
        DataSource gen = new DataSource() {
            @Override public BlockPos pos() { return new BlockPos(9, 1, 1); }
            @Override public Component name() { return Component.literal("Gen"); }
            @Override public String type() { return "test"; }
            @Override public int state() { return OK; }
            @Override public Component stateText() { return Component.literal("ok"); }
            @Override public List<DataValue> values() { return List.of(); }
            @Override public void open(ServerPlayer player) { }
            @Override public PowerFlow power() { return PowerFlow.producer(prod[0], 1000); }
        };
        for (int i = 0; i < 60; i++) {
            prod[0] = i < 30 ? 100 : 300;
            be.sample(List.of(gen), 1000 + 20L * i);
        }
        check(be.fine().size() == 60 && be.fineCount() == 60, "Sekunden-Reihe " + be.fine().size());
        check(be.coarse().size() == 2 && be.coarse().get(0).prod() == 100 && be.coarse().get(1).prod() == 300,
                "Stunden-Reihe: " + be.coarse());
        for (int i = 0; i < 300; i++) be.sample(List.of(gen), 5000 + 20L * i);
        check(be.fine().size() == PowerMonitorBlockEntity.FINE_MAX, "Sekunden-Reihe unbegrenzt: " + be.fine().size());
        check(be.coarse().size() <= PowerMonitorBlockEntity.COARSE_MAX, "Stunden-Reihe unbegrenzt");
        check(be.fineCount() == 360, "Zaehler " + be.fineCount());
        check(be.fineSince(be.fineCount() - 5).size() == 5, "fineSince liefert " + be.fineSince(be.fineCount() - 5).size());
        check(be.fineSince(0).size() == PowerMonitorBlockEntity.FINE_MAX, "fineSince(0) liefert nicht die ganze Reihe");
        check(be.fineSince(be.fineCount()).isEmpty(), "fineSince am Ende nicht leer");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_save", timeoutTicks = 20)
    public static void savesHistoryAndWorldSeries(GameTestHelper h) {
        PowerMonitorBlockEntity be = monitor(h, new BlockPos(2, 2, 2));
        DataSource gen = plant(new BlockPos(9, 1, 1), "Gen", PowerFlow.producer(777, 1000));
        for (int i = 0; i < 70; i++) be.sample(List.of(gen), 1000 + 20L * i);
        var reg = h.getLevel().registryAccess();
        var tag = be.saveWithoutMetadata(reg);
        PowerMonitorBlockEntity copy = new PowerMonitorBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.setLevel(h.getLevel());
        copy.loadWithComponents(tag, reg);
        check(copy.fine().size() == 70 && copy.fineCount() == 70 && copy.latest().prod() == 777, "Verlauf nach dem Laden: " + copy.fine().size());
        check(copy.coarse().size() == be.coarse().size(), "Stunden-Reihe nach dem Laden");
        var upd = be.getUpdateTag(reg);
        check(upd.getLongArray("shown").length == PowerMonitorBlockEntity.WORLD_SAMPLES * PowerSample.FIELDS, "Update-Tag traegt nicht 60 Messungen");
        PowerMonitorBlockEntity client = new PowerMonitorBlockEntity(be.getBlockPos(), be.getBlockState());
        client.handleUpdateTag(upd, reg);
        check(client.shown().size() == PowerMonitorBlockEntity.WORLD_SAMPLES && client.shown().get(59).prod() == 777, "Client liest das Update nicht");
        check(!client.slowRange(), "Zeitraum schon umgeschaltet");
        be.toggleRange();
        check(be.slowRange() && be.worldSeries().size() == be.coarse().size(), "Halbstunden-Reihe am Bildschirm");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_stats", timeoutTicks = 20)
    public static void statsAreRight(GameTestHelper h) {
        List<PowerSample> none = List.of(new PowerSample(100, 50, 0, 0, 0, 0));
        check(PowerStats.eta(none).kind() == PowerStats.Eta.Kind.NONE, "Ohne Speicher keine Prognose");
        List<PowerSample> dis = new ArrayList<>();
        for (int i = 0; i < 12; i++) dis.add(new PowerSample(0, 500, 600_000, 1_000_000, 0, 500));
        PowerStats.Eta e = PowerStats.eta(dis);
        check(e.kind() == PowerStats.Eta.Kind.EMPTY_IN && e.seconds() == 60, "Entladen: " + e);
        List<PowerSample> chg = new ArrayList<>();
        for (int i = 0; i < 12; i++) chg.add(new PowerSample(900, 100, 400_000, 1_000_000, 800, 0));
        e = PowerStats.eta(chg);
        check(e.kind() == PowerStats.Eta.Kind.FULL_IN && e.seconds() == 38, "Laden: " + e);
        List<PowerSample> flat = List.of(new PowerSample(300, 300, 500_000, 1_000_000, 0, 0));
        check(PowerStats.eta(flat).kind() == PowerStats.Eta.Kind.STEADY, "Ruhender Speicher");
        List<PowerSample> empty = List.of(new PowerSample(0, 10, 0, 1_000_000, 0, 0));
        check(PowerStats.eta(empty).kind() == PowerStats.Eta.Kind.IS_EMPTY, "Leerer Speicher");
        check(PowerStats.compact(950).equals("950") && PowerStats.compact(12_400).equals("12.4k") && PowerStats.compact(-3_400_000).equals("-3.4M"), "compact");
        check(PowerStats.duration(35).equals("35 s") && PowerStats.duration(250).equals("4 min 10 s") && PowerStats.duration(7500).equals("2 h 05 min"), "duration");
        check(PowerSample.average(dis, 0, 12).out() == 500 && PowerSample.average(dis, 5, 12) == PowerSample.ZERO, "average");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_wall", timeoutTicks = 20)
    public static void groupsIntoOneScreen(GameTestHelper h) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(2, 2, 3);
        PowerMonitorBlockEntity first = monitor(h, a);
        h.setBlock(b, DataRegistry.POWER.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        var layout = PowerMonitorBlock.layout(h.getLevel(), h.absolutePos(b));
        check(layout.w() == 2 && layout.h() == 1, "Kein 2x1-Bildschirm: " + layout.w() + "x" + layout.h());
        check(PowerMonitorBlock.head(h.getLevel(), h.absolutePos(b)) == h.getLevel().getBlockEntity(layout.origin()), "head() liefert nicht den Ursprung");
        first.sample(List.of(plant(new BlockPos(9, 1, 1), "Gen", PowerFlow.producer(5, 5))), 100);
        if (!(h.getLevel().getBlockEntity(h.absolutePos(b)) instanceof PowerMonitorBlockEntity second) || !second.fine().isEmpty()) {
            throw new GameTestAssertException("Zweiter Block misst selbst");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "power_monitor_net", timeoutTicks = 60)
    public static void readsRealPlantsFromTheNet(GameTestHelper h) {
        BlockPos mon = new BlockPos(2, 2, 2), panel = new BlockPos(5, 2, 2);
        PowerMonitorBlockEntity be = monitor(h, mon);
        h.setBlock(new BlockPos(3, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(panel, com.sqc.nuclearsq.registry.PowerRegistry.SOLAR_PANEL.get().defaultBlockState());
        h.runAfterDelay(3, () -> {
            be.scan();
            check(be.summary().plants() == 1, "Anlagen im Netz: " + be.summary().plants());
            check(be.summary().producers().size() == 1, "Sonnenkollektor fehlt in der Erzeugerliste");
            check(!be.fine().isEmpty(), "Keine Messung");
            h.succeed();
        });
    }
}
