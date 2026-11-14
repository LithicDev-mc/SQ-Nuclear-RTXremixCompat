package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSearch;
import com.sqc.nuclearsq.data.DataTree;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataTerminalTests {
    private DataTerminalTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "data_terminal", timeoutTicks = 100)
    public static void dataTerminalTreeShowsRackFolders(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2));
        BigMachine m = BigMachine.SERVER_RACK_SMALL;
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity rack) || rack.rack() == null) {
            throw new GameTestAssertException("kein Rack-Controller");
        }
        BlockPos port = m.structure.world(ctrl, 0, 0, 0, 1), cable = port.offset(port.subtract(ctrl)), terminal = cable.offset(port.subtract(ctrl));
        h.getLevel().setBlockAndUpdate(cable, DataRegistry.CABLE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(terminal, DataRegistry.TERMINAL.get().defaultBlockState());
        rack.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        rack.rack().drives().setStackInSlot(1, new ItemStack(StorageRegistry.CPU_MODULE.get()));
        rack.energy().add(rack.energy().getMaxEnergyStored());
        h.runAfterDelay(25, () -> {
            if (!rack.rack().online()) throw new GameTestAssertException("Rack mit Strom nicht online");
            var access = StorageNet.access(h.getLevel(), terminal);
            access.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 300, false);
            access.insert(ItemKey.of(new ItemStack(Items.DIAMOND)), 7, false);

            List<DataEntry> tree = DataTree.scan(h.getLevel(), terminal);
            DataEntry e = tree.stream().filter(x -> x.pos().equals(ctrl)).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("Rack fehlt im Terminal: " + tree.size() + " Anlagen"));
            if (!e.rack() || e.slots().size() != rack.rack().slots()) throw new GameTestAssertException("Schaechte: " + e.slots().size() + " statt " + rack.rack().slots());
            DataEntry.Slot drive = e.slots().get(0), module = e.slots().get(1), empty = e.slots().get(2);
            if (drive.kind() != DataEntry.DRIVE || module.kind() != DataEntry.MODULE || empty.kind() != DataEntry.EMPTY) {
                throw new GameTestAssertException("Schacht-Arten " + drive.kind() + "/" + module.kind() + "/" + empty.kind() + " statt Platte/Modul/leer");
            }
            if (drive.items().size() != 2 || !drive.items().get(0).item().is(Items.COBBLESTONE) || drive.items().get(0).count() != 300
                    || drive.items().get(1).count() != 7) {
                throw new GameTestAssertException("Platten-Ordner zeigt " + drive.items() + " statt 300 Bruchstein, 7 Diamanten");
            }
            if (e.line("storage_used") == null || e.line("storage_used").value() != 307) throw new GameTestAssertException("Speicher belegt fehlt oder falsch");
            DataEntry.Line prio = e.line("priority"), enable = e.line("enable");
            if (prio == null || prio.kind() != DataEntry.STEP || enable == null || enable.kind() != DataEntry.SWITCH) {
                throw new GameTestAssertException("Steuerung: Prioritaet als Stufe und Ein/Aus als Schalter erwartet, hat " + e.controls());
            }
            if (e.values().stream().anyMatch(l -> l.key().equals("enable") || l.key().equals("priority"))) {
                throw new GameTestAssertException("Stellgroessen stehen doppelt unter Messwerten");
            }

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(),
                    net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
            DataEntry.LIST_CODEC.encode(buf, tree);
            List<DataEntry> back = DataEntry.LIST_CODEC.decode(buf);
            DataEntry eb = back.stream().filter(x -> x.pos().equals(ctrl)).findFirst().orElseThrow();
            if (eb.slots().get(0).items().get(1).count() != 7 || !eb.slots().get(0).items().get(1).item().is(Items.DIAMOND)
                    || eb.controls().size() != e.controls().size() || buf.readableBytes() != 0) {
                throw new GameTestAssertException("Liste kommt nicht heil ueber das Netz");
            }

            if (DataTree.control(h.getLevel(), terminal, ctrl, "priority", 7) != null || rack.rack().priority() != 7) {
                throw new GameTestAssertException("Prioritaet laesst sich nicht stellen: " + rack.rack().priority());
            }
            if (DataTree.control(h.getLevel(), terminal, ctrl, "priority", 12) == null || rack.rack().priority() != 7) {
                throw new GameTestAssertException("Prioritaet 12 ausserhalb 0-9 angenommen");
            }
            if (DataTree.control(h.getLevel(), terminal, ctrl, "enable", 0) != null || rack.enabled()) {
                throw new GameTestAssertException("Ausschalten aus dem Ordner geht nicht");
            }
            if (DataTree.control(h.getLevel(), terminal, ctrl, "scram", 1) == null || DataTree.control(h.getLevel(), terminal, ctrl, "dump.power", 1) == null) {
                throw new GameTestAssertException("Terminal darf nur Schalter und Stufen stellen");
            }
            if (DataTree.control(h.getLevel(), terminal, ctrl.above(20), "enable", 1) == null) {
                throw new GameTestAssertException("Anlage ausserhalb des Netzes angenommen");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "data_terminal")
    public static void dataSearchFindsByNameIdAndUmlaut(GameTestHelper h) {
        String name = "Speisewasservorwärmer";
        String id = "feed_water_preheater";
        check(DataSearch.hasAll(name + " " + id, DataSearch.terms("speisewasser")), "deutscher Teilname");
        check(DataSearch.hasAll(name + " " + id, DataSearch.terms("Vorwaermer")), "Umschreibung ae");
        check(DataSearch.hasAll(name + " " + id, DataSearch.terms("feedwater")), "englisch ohne Leerzeichen");
        check(DataSearch.hasAll(name + " " + id, DataSearch.terms("feed preheater")), "zwei Woerter");
        check(!DataSearch.hasAll(name + " " + id, DataSearch.terms("turbine")), "kein Treffer bei anderem Namen");
        check(DataSearch.terms("  ").isEmpty(), "leere Suche ohne Woerter");
        h.succeed();
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new GameTestAssertException("Suche: " + what);
    }
}
