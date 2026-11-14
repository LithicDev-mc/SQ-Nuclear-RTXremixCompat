package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.CraftPlanner;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.Pattern;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.storage.StorageTerminalMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ComponentAssemblerSafetyTests {
    private ComponentAssemblerSafetyTests() {}

    static final String FLOOR = "floor";
    static final BigMachine M = BigMachine.COMPONENT_ASSEMBLER;
    static final ItemKey LOG = ItemKey.of(new ItemStack(Items.OAK_LOG)), PLANKS = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

    static final class Rig {
        BigMachineBlockEntity rack, a1, a2;
        BlockPos terminal;
    }

    static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": Controller ohne BlockEntity");
        return be;
    }

    static void cable(GameTestHelper h, BlockPos a, BlockPos b) {
        BlockPos p = a;
        while (true) {
            h.getLevel().setBlockAndUpdate(p, DataRegistry.CABLE.get().defaultBlockState());
            if (p.equals(b)) return;
            p = p.getX() != b.getX() ? p.offset(Integer.signum(b.getX() - p.getX()), 0, 0)
                    : p.getZ() != b.getZ() ? p.offset(0, 0, Integer.signum(b.getZ() - p.getZ()))
                    : p.offset(0, Integer.signum(b.getY() - p.getY()), 0);
        }
    }

    static void charge(BigMachineBlockEntity be) {
        be.energy().add(be.energy().getMaxEnergyStored());
    }

    static BlockPos front(BigMachineBlockEntity be) {
        return M.structure.world(be.getBlockPos(), 0, M.ax, M.ay, M.az - 1);
    }

    static Rig rig(GameTestHelper h, boolean twin, Item drive) {
        Rig r = new Rig();
        r.rack = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        r.a1 = build(h, M, new BlockPos(7, 2, 7));
        BlockPos rc = r.rack.getBlockPos(), rp = r.rack.machine().structure.world(rc, 0, 0, 0, 1);
        BlockPos fromRack = rp.offset(rp.subtract(rc));
        cable(h, fromRack, front(r.a1));
        if (twin) {
            r.a2 = build(h, M, new BlockPos(14, 2, 7));
            cable(h, front(r.a1), front(r.a2));
        }
        r.terminal = fromRack.above();
        h.getLevel().setBlockAndUpdate(r.terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        r.rack.rack().drives().setStackInSlot(0, new ItemStack(drive));
        charge(r.rack);
        charge(r.a1);
        if (twin) charge(r.a2);
        ComponentAssembler a = r.a1.assembler();
        a.cards().setStackInSlot(0, planksCard(h, a));
        return r;
    }

    static ItemStack planksCard(GameTestHelper h, ComponentAssembler a) {
        for (int i = 0; i < ComponentAssembler.GRID; i++) a.template().setStackInSlot(i, ItemStack.EMPTY);
        a.template().setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        a.encode().setStackInSlot(0, new ItemStack(StorageRegistry.PATTERN_CARD.get()));
        a.encode().setStackInSlot(1, ItemStack.EMPTY);
        a.updatePreview(h.getLevel());
        if (!a.writeCard(h.getLevel())) throw new GameTestAssertException("Karte nicht beschrieben");
        return a.encode().extractItem(1, 1, false);
    }

    static StorageAccess storage(GameTestHelper h, Rig r) {
        return StorageNet.access(h.getLevel(), r.terminal);
    }

    static long count(GameTestHelper h, Rig r, ItemKey k) {
        return storage(h, r).count(k);
    }

    static long bufferCount(BigMachineBlockEntity be, Item item) {
        long n = 0;
        for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(item)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    static long dropped(GameTestHelper h, BlockPos around, Item item) {
        long n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(12), e -> e.getItem().is(item))) n += e.getItem().getCount();
        return n;
    }

    static StorageTerminalMenu terminal(GameTestHelper h, Rig r) {
        Player p = FakePlayerFactory.getMinecraft(h.getLevel());
        return new StorageTerminalMenu(0, p.getInventory(), r.terminal);
    }

    static void reload(GameTestHelper h, BigMachineBlockEntity be) {
        var reg = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithoutMetadata(reg);
        be.assembler().unbind(h.getLevel());
        be.loadWithComponents(tag, reg);
        be.assembler().bindNow(h.getLevel(), be);
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 60)
    public static void assemblerTempoModulesSpeedUpAndCostMore(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, M, new BlockPos(5, 2, 5));
        ComponentAssembler a = be.assembler();
        check(a.tempoLevel() == 0 && a.cycleTicks() == 40 && a.powerUse() == 1440, "ohne Modul: " + a.cycleTicks() + " Ticks, " + a.powerUse() + " FE/t");
        a.upgrades().setStackInSlot(0, new ItemStack(StorageRegistry.ASSEMBLER_TEMPO.get(), 3));
        check(a.tempoLevel() == 3 && a.cycleTicks() == 16 && a.powerUse() == 1440 * 625 / 100, "Stufe 3: " + a.cycleTicks() + " Ticks, " + a.powerUse() + " FE/t");
        a.upgrades().setStackInSlot(0, new ItemStack(StorageRegistry.ASSEMBLER_TEMPO.get(), 8));
        check(a.tempoLevel() == 8 && a.cycleTicks() == 8 && a.powerUse() == 36000, "Stufe 8: " + a.cycleTicks() + " Ticks, " + a.powerUse() + " FE/t");
        check(a.upgrades().getSlotLimit(0) == 8, "Schacht fasst nicht 8");
        check(!a.upgrades().isItemValid(0, new ItemStack(Items.REDSTONE)), "fremdes Item im Modulschacht");
        check(a.upgrades().insertItem(0, new ItemStack(StorageRegistry.ASSEMBLER_TEMPO.get(), 4), false).getCount() == 4, "9. Modul passt in den Schacht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 200)
    public static void assemblerTempoFinishesFaster(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        a.upgrades().setStackInSlot(0, new ItemStack(StorageRegistry.ASSEMBLER_TEMPO.get(), 8));
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 8, false) == 8, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 32), "Bestellung abgelehnt");
            h.runAfterDelay(8 * 2 + 50, () -> {
                check(a.jobs().isEmpty(), "Auftrag nach " + (8 * 2 + 50) + " Ticks noch offen, Status " + a.status());
                check(count(h, r, PLANKS) == 32 && count(h, r, LOG) == 0, "Bestand: " + count(h, r, PLANKS) + " Bretter, " + count(h, r, LOG) + " Staemme");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 400)
    public static void assemblerCancelKeepsEveryItem(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() * 3 / 2, () -> {
                a.cancel(true);
                h.runAfterDelay(a.cycleTicks() * 2, () -> {
                    long planks = count(h, r, PLANKS), logs = count(h, r, LOG);
                    check(planks > 0 && planks < 40, "Es sollte teilweise gebaut sein: " + planks + " Bretter");
                    check(logs * 4 + planks == 64, "Itemwert nicht erhalten: " + logs + " Staemme, " + planks + " Bretter");
                    check(a.jobs().isEmpty() && a.busyLanes() == 0, "Auftrag oder Spur nach Abbruch noch aktiv");
                    check(dropped(h, r.a1.getBlockPos(), Items.OAK_PLANKS) == 0 && bufferCount(r.a1, Items.OAK_PLANKS) == 0, "Bretter ausserhalb des Speichers");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 500)
    public static void assemblerJobSurvivesSaveAndLoad(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() + a.cycleTicks() / 2, () -> {
                int before = a.jobs().get(0).leftInStep();
                check(before > 0 && before < 10, "Auftrag sollte angefangen sein, offen " + before);
                ComponentAssembler b = r.a1.assembler();
                reload(h, r.a1);
                check(b.jobs().size() == 1 && b.jobs().get(0).leftInStep() == before, "Auftrag nach dem Laden: " + b.jobs().size() + " / offen " + (b.jobs().isEmpty() ? -1 : b.jobs().get(0).leftInStep()));
                reload(h, r.a1);
                check(b.jobs().size() == 1, "Auftrag nach zweitem Laden doppelt: " + b.jobs().size());
                h.runAfterDelay(10 * b.cycleTicks(), () -> {
                    check(b.jobs().isEmpty(), "Auftrag nach dem Laden nicht fertig, Status " + b.status());
                    check(count(h, r, PLANKS) == 40 && count(h, r, LOG) == 6, "Bestand: " + count(h, r, PLANKS) + " Bretter, " + count(h, r, LOG) + " Staemme");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 400)
    public static void assemblerFullRackKeepsResultInBuffer(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            StorageAccess s = storage(h, r);
            check(s.insert(LOG, 1, false) == 1 && s.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 4095, false) == 4095, "Platte nicht gefuellt");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 4), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() + 15, () -> {
                long inRack = count(h, r, PLANKS), inBuffer = bufferCount(r.a1, Items.OAK_PLANKS);
                check(count(h, r, LOG) == 0 && a.jobs().isEmpty(), "Auftrag nicht gelaufen: " + count(h, r, LOG) + " Staemme");
                check(inRack + inBuffer == 4, "Bretter im Rack " + inRack + " + Puffer " + inBuffer + " ergibt nicht 4");
                check(inBuffer > 0, "Rack war voll, aber nichts im Puffer");
                check(dropped(h, r.a1.getBlockPos(), Items.OAK_PLANKS) == 0, "Bretter liegen auf dem Boden");
                check(storage(h, r).extract(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 100, false) == 100, "Platz nicht geschaffen");
                h.runAfterDelay(50, () -> {
                    check(bufferCount(r.a1, Items.OAK_PLANKS) == 0 && count(h, r, PLANKS) == 4, "Puffer nicht geleert: Rack " + count(h, r, PLANKS) + ", Puffer " + bufferCount(r.a1, Items.OAK_PLANKS));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 500)
    public static void assemblerBlockedOutputConsumesNothing(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            StorageAccess s = storage(h, r);
            check(s.insert(LOG, 1, false) == 1 && s.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 4095, false) == 4095, "Platte nicht gefuellt");
            for (int i = 0; i < r.a1.items().getSlots(); i++) r.a1.items().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 4), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() * 2 + 20, () -> {
                check(a.status() == ComponentAssembler.BLOCKED, "Status BLOCKED erwartet, ist " + a.status());
                check(count(h, r, LOG) == 1 && count(h, r, PLANKS) == 0 && a.jobs().size() == 1, "Es wurde etwas verbraucht: " + count(h, r, LOG) + " Staemme");
                for (int i = 0; i < r.a1.items().getSlots(); i++) r.a1.items().setStackInSlot(i, ItemStack.EMPTY);
                h.runAfterDelay(40, () -> {
                    check(a.jobs().isEmpty() && count(h, r, LOG) == 0, "Auftrag nach Freigabe nicht fertig, Status " + a.status());
                    check(count(h, r, PLANKS) + bufferCount(r.a1, Items.OAK_PLANKS) == 4, "Bretter: " + count(h, r, PLANKS) + " + Puffer " + bufferCount(r.a1, Items.OAK_PLANKS));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 400)
    public static void assemblerBreakReturnsBufferOnce(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        a.upgrades().setStackInSlot(0, new ItemStack(StorageRegistry.ASSEMBLER_TEMPO.get(), 2));
        h.runAfterDelay(25, () -> {
            r.a1.items().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 40));
            r.a1.items().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 1));
            BlockPos ctrl = r.a1.getBlockPos();
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(ctrl);
            check(!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity), "Anlage steht noch");
            check(count(h, r, PLANKS) == 40 && count(h, r, ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 1, "Puffer nicht in die Racks zurueck: " + count(h, r, PLANKS) + " Bretter");
            check(dropped(h, ctrl, Items.OAK_PLANKS) == 0 && dropped(h, ctrl, Items.IRON_INGOT) == 0, "Puffer lag zusaetzlich auf dem Boden");
            check(dropped(h, ctrl, StorageRegistry.ASSEMBLER_TEMPO.get()) == 2, "Tempo-Module nicht herausgefallen");
            check(dropped(h, ctrl, StorageRegistry.PATTERN_CARD.get()) == 1, "Musterkarte nicht herausgefallen");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 400)
    public static void assemblerBreakWithFullRackDropsRest(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 4096, false) == 4096, "Platte nicht gefuellt");
            for (int i = 0; i < r.a1.items().getSlots(); i++) r.a1.items().setStackInSlot(i, new ItemStack(Items.OAK_PLANKS, 10));
            long total = 10L * r.a1.items().getSlots();
            BlockPos ctrl = r.a1.getBlockPos();
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(ctrl);
            long got = count(h, r, PLANKS) + dropped(h, ctrl, Items.OAK_PLANKS);
            check(got == total, "Bretter nach Abbau: " + got + " statt " + total);
            h.succeed();
        });
    }

    static void plug(Rig r, Item module, int count) {
        for (int i = 0; i < count; i++) {
            int slot = -1;
            for (int k = 0; k < r.rack.rack().slots() && slot < 0; k++) if (r.rack.rack().drives().getStackInSlot(k).isEmpty()) slot = k;
            check(slot >= 0, "kein freier Rack-Schacht");
            r.rack.rack().drives().setStackInSlot(slot, new ItemStack(module));
        }
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 200)
    public static void assemblerCoresAndSizeComeFromComputeModules(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            a.refreshNow(h.getLevel(), r.a1);
            check(a.laneCap() == 1 && a.fits(64) && !a.fits(65), "ohne Module: " + a.laneCap() + " Kern, Groesse " + a.sizeCap());
            check(storage(h, r).insert(LOG, 1100, false) == 1100, "Staemme nicht eingelagert");
            StorageTerminalMenu menu = terminal(h, r);
            check(!menu.order(new ItemStack(Items.OAK_PLANKS), 3000), "zu grosse Bestellung angenommen");
            check(menu.lastMessage().getString().contains("too big"), "Meldung zu gross erwartet: " + menu.lastMessage().getString());
            plug(r, StorageRegistry.CPU_MODULE.get(), 1);
            plug(r, StorageRegistry.RAM_MODULE.get(), 1);
            h.runAfterDelay(5, () -> {
                a.refreshNow(h.getLevel(), r.a1);
                check(a.laneCap() == 4, "1 CPU-Modul (4 Kerne, 1 fuer das Terminal) ergibt 4 Spuren, ist " + a.laneCap());
                check(a.sizeCap() == 64 + 12 * 64 && a.fits(832), "1 RAM-Modul (16 GB, 4 fuer das Terminal): Groesse " + a.sizeCap());
                check(menu.order(new ItemStack(Items.OAK_PLANKS), 3000), "Bestellung mit RAM abgelehnt: " + menu.lastMessage().getString());
                plug(r, StorageRegistry.CPU_MODULE.get(), 1);
                h.runAfterDelay(5, () -> {
                    a.refreshNow(h.getLevel(), r.a1);
                    check(a.laneCap() == 8, "Spuren sind auf 8 begrenzt, ist " + a.laneCap());
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 400)
    public static void assemblerLanesWorkInParallel(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        plug(r, StorageRegistry.CPU_MODULE.get(), 1);
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 40, false) == 40, "Staemme nicht eingelagert");
            a.refreshNow(h.getLevel(), r.a1);
            check(a.laneCap() == 5, "Spuren (Terminal laeuft ohne RAM nicht, braucht keinen Kern): " + a.laneCap());
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 160), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() + 10, () -> {
                check(a.busyLanes() > 1 || a.jobs().isEmpty() || a.jobs().get(0).leftInStep() <= 20, "Es arbeitet nur eine Spur: " + a.busyLanes());
                h.runAfterDelay(a.cycleTicks() + 30, () -> {
                    check(a.jobs().isEmpty(), "40 Durchgaenge nach 2 Zyklen nicht fertig, offen " + (a.jobs().isEmpty() ? 0 : a.jobs().get(0).leftInStep()));
                    check(count(h, r, PLANKS) == 160 && count(h, r, LOG) == 0, "Bestand: " + count(h, r, PLANKS) + " Bretter, " + count(h, r, LOG) + " Staemme");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 500)
    public static void assemblerClusterSharesOneJob(GameTestHelper h) {
        Rig r = rig(h, true, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler(), b = r.a2.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(StorageNet.assemblers(h.getLevel(), r.terminal).size() == 2, "Terminal findet nicht beide Assembler");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 64), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() * 2 + 30, () -> {
                check(a.jobs().isEmpty() && b.jobs().isEmpty(), "Auftrag mit zwei Assemblern nach 2 Zyklen nicht fertig, Status " + a.status() + "/" + b.status());
                check(count(h, r, PLANKS) == 64 && count(h, r, LOG) == 0, "Bestand: " + count(h, r, PLANKS) + " Bretter, " + count(h, r, LOG) + " Staemme");
                check(a.peers() == 2, "Verbund erkennt " + a.peers() + " Assembler");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 600)
    public static void assemblerClusterHelperSurvivesReload(GameTestHelper h) {
        Rig r = rig(h, true, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler(), b = r.a2.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 64), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() / 2, () -> {
                reload(h, r.a2);
                reload(h, r.a1);
                h.runAfterDelay(a.cycleTicks() * 6, () -> {
                    check(a.jobs().isEmpty(), "Auftrag nach Neustart nicht fertig, offen " + (a.jobs().isEmpty() ? 0 : a.jobs().get(0).leftInStep()));
                    long planks = count(h, r, PLANKS), logs = count(h, r, LOG);
                    check(planks == 64 && logs == 0, "Bestand: " + planks + " Bretter, " + logs + " Staemme");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 800)
    public static void assemblerClusterHelperRemovedOwnerFinishes(GameTestHelper h) {
        Rig r = rig(h, true, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 64), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() / 2, () -> {
                FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(r.a2.getBlockPos());
                h.runAfterDelay(a.cycleTicks() * 8, () -> {
                    check(a.jobs().isEmpty(), "Besitzer wurde nach dem Abbau des Helfers nicht fertig, offen " + (a.jobs().isEmpty() ? 0 : a.jobs().get(0).leftInStep()));
                    check(count(h, r, PLANKS) == 64 && count(h, r, LOG) == 0, "Bestand: " + count(h, r, PLANKS) + " Bretter, " + count(h, r, LOG) + " Staemme");
                    h.succeed();
                });
            });
        });
    }

    private static Pattern pattern(Item out, Item... in) {
        List<Pattern.Part> parts = new ArrayList<>();
        for (Item i : in) parts.add(new Pattern.Part(new ItemStack(i), 1));
        return new Pattern(parts, new Pattern.Part(new ItemStack(out), 1), List.of());
    }

    @GameTest(template = FLOOR, batch = "component_assembler_safety", timeoutTicks = 100)
    public static void plannerSurvivesLoopsAndHugeTrees(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        h.runAfterDelay(25, () -> {
            StorageAccess s = storage(h, r);
            Map<ItemKey, Pattern> book = new LinkedHashMap<>();
            Pattern ingot = pattern(Items.IRON_INGOT, Items.IRON_BLOCK), block = pattern(Items.IRON_BLOCK, Items.IRON_INGOT);
            book.put(ItemKey.of(new ItemStack(Items.IRON_INGOT)), ingot);
            book.put(ItemKey.of(new ItemStack(Items.IRON_BLOCK)), block);
            CraftPlanner.Plan loop = CraftPlanner.plan(s, book, ItemKey.of(new ItemStack(Items.IRON_INGOT)), 3);
            check(!loop.ok() && !loop.missing().isEmpty() && !loop.tooComplex(), "Schleife: erwartet fehlende Zutat, ist " + loop);
            List<Item> items = new ArrayList<>();
            for (Item it : BuiltInRegistries.ITEM) if (it != Items.AIR && items.size() < 1 + 16 + 16 + 16 * 16 + 16 * 16 * 16) items.add(it);
            check(items.size() > 600, "zu wenige Items fuer den Test");
            Map<ItemKey, Pattern> wide = new LinkedHashMap<>();
            Item top = items.get(0);
            Item[] l1 = items.subList(1, 17).toArray(new Item[0]);
            wide.put(ItemKey.of(new ItemStack(top)), pattern(top, l1));
            for (int i = 0; i < 16; i++) {
                Item[] l2 = items.subList(17 + i * 16, 33 + i * 16).toArray(new Item[0]);
                wide.put(ItemKey.of(new ItemStack(l1[i])), pattern(l1[i], l2));
                for (int j = 0; j < 16; j++) {
                    Item mid = l2[j];
                    int from = 273 + (i * 16 + j) * 16 % (items.size() - 300);
                    Item[] l3 = items.subList(from, from + 16).toArray(new Item[0]);
                    wide.put(ItemKey.of(new ItemStack(mid)), pattern(mid, l3));
                }
            }
            CraftPlanner.Plan huge = CraftPlanner.plan(s, wide, ItemKey.of(new ItemStack(top)), 1);
            check(huge.tooComplex() && !huge.ok(), "Breiter Baum nicht als zu komplex erkannt, Knoten ueber " + CraftPlanner.MAX_NODES);
            h.succeed();
        });
    }
}
