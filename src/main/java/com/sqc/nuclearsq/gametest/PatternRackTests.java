package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.storage.StorageTerminalMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PatternRackTests {
    private PatternRackTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": Controller ohne BlockEntity");
        return be;
    }

    private static void cable(GameTestHelper h, BlockPos a, BlockPos b) {
        BlockPos p = a;
        while (true) {
            h.getLevel().setBlockAndUpdate(p, DataRegistry.CABLE.get().defaultBlockState());
            if (p.equals(b)) return;
            p = p.getX() != b.getX() ? p.offset(Integer.signum(b.getX() - p.getX()), 0, 0)
                    : p.getZ() != b.getZ() ? p.offset(0, 0, Integer.signum(b.getZ() - p.getZ()))
                    : p.offset(0, Integer.signum(b.getY() - p.getY()), 0);
        }
    }

    private static void charge(BigMachineBlockEntity be) {
        be.energy().add(be.energy().getMaxEnergyStored());
    }

    private static ItemStack writeCard(GameTestHelper h, ComponentAssembler a, ItemStack in) {
        for (int i = 0; i < ComponentAssembler.GRID; i++) a.template().setStackInSlot(i, ItemStack.EMPTY);
        a.template().setStackInSlot(0, in);
        a.encode().setStackInSlot(0, new ItemStack(StorageRegistry.PATTERN_CARD.get()));
        a.encode().setStackInSlot(1, ItemStack.EMPTY);
        a.updatePreview(h.getLevel());
        if (!a.writeCard(h.getLevel())) throw new GameTestAssertException("Karte nicht beschrieben");
        return a.encode().extractItem(1, 1, false);
    }

    @GameTest(template = FLOOR, batch = "pattern_rack_slots", timeoutTicks = 60)
    public static void patternRackTakesOnlyWrittenCards(GameTestHelper h) {
        BigMachineBlockEntity pr = build(h, BigMachine.PATTERN_RACK, new BlockPos(2, 2, 2));
        BigMachineBlockEntity asm = build(h, BigMachine.COMPONENT_ASSEMBLER, new BlockPos(8, 2, 8));
        if (pr.rack() == null || !pr.rack().isPatternRack() || pr.rack().slots() != 40) throw new GameTestAssertException("Muster-Rack ohne 40 Schaechte");
        ItemStack card = writeCard(h, asm.assembler(), new ItemStack(Items.OAK_LOG));
        var drives = pr.rack().drives();
        if (drives.insertItem(0, new ItemStack(StorageRegistry.PATTERN_CARD.get()), false).isEmpty()) throw new GameTestAssertException("leere Karte steckt im Rack");
        if (drives.insertItem(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()), false).isEmpty()) throw new GameTestAssertException("Festplatte steckt im Muster-Rack");
        if (!drives.insertItem(0, card, false).isEmpty()) throw new GameTestAssertException("beschriebene Karte passt nicht");
        if (pr.rack().patterns().size() != 1) throw new GameTestAssertException("Muster nicht gezaehlt");
        BigMachineBlockEntity storage = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(14, 2, 2));
        if (storage.rack().drives().insertItem(0, card.copy(), false).getCount() != 1) throw new GameTestAssertException("Karte steckt im Speicher-Rack");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "pattern_rack_net", timeoutTicks = 500)
    public static void componentAssemblerUsesPatternRack(GameTestHelper h) {
        BigMachineBlockEntity storage = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BigMachineBlockEntity be = build(h, BigMachine.COMPONENT_ASSEMBLER, new BlockPos(7, 2, 7));
        BlockPos rc = storage.getBlockPos(), rp = storage.machine().structure.world(rc, 0, 0, 0, 1);
        BlockPos fromRack = rp.offset(rp.subtract(rc));
        BlockPos front = BigMachine.COMPONENT_ASSEMBLER.structure.world(be.getBlockPos(), 0, BigMachine.COMPONENT_ASSEMBLER.ax,
                BigMachine.COMPONENT_ASSEMBLER.ay, BigMachine.COMPONENT_ASSEMBLER.az - 1);
        cable(h, fromRack, front);
        BlockPos terminal = fromRack.above();
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        BigMachineBlockEntity pr = build(h, BigMachine.PATTERN_RACK, new BlockPos(2, 2, 12));
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 12));
        BlockPos corner = new BlockPos(start.getX(), start.getY(), fromRack.getZ());
        cable(h, start, corner);
        cable(h, corner, fromRack);
        storage.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        charge(storage);
        charge(pr);
        ComponentAssembler a = be.assembler();
        ItemStack planks = writeCard(h, a, new ItemStack(Items.OAK_LOG));
        if (!pr.rack().drives().insertItem(5, planks, false).isEmpty()) throw new GameTestAssertException("Karte steckt nicht im Muster-Rack");
        h.runAfterDelay(45, () -> {
            if (!pr.isFormed() || !be.isFormed() || !storage.isFormed()) throw new GameTestAssertException("nicht aufgebaut: Muster-Rack " + pr.isFormed() + " " + h.relativePos(pr.getBlockPos()) + ", Kabelstart " + h.relativePos(start) + ", Speicher-Anschluss " + h.relativePos(fromRack) + ", Assembler " + be.isFormed() + ", Speicher " + storage.isFormed());
            if (!pr.rack().online()) throw new GameTestAssertException("Muster-Rack nicht online");
            if (!a.patterns().isEmpty()) throw new GameTestAssertException("Assembler hat eigene Karten");
            if (StorageNet.racks(h.getLevel(), terminal).size() != 1) throw new GameTestAssertException("Muster-Rack zaehlt als Speicher-Rack");
            if (!a.hasPattern(ItemKey.of(new ItemStack(Items.OAK_PLANKS)))) throw new GameTestAssertException("Muster aus dem Rack nicht sichtbar");
            StorageAccess s = StorageNet.access(h.getLevel(), terminal);
            if (s.insert(ItemKey.of(new ItemStack(Items.OAK_LOG)), 1, false) != 1) throw new GameTestAssertException("Stamm nicht eingelagert");
            Player p = FakePlayerFactory.getMinecraft(h.getLevel());
            StorageTerminalMenu menu = new StorageTerminalMenu(0, p.getInventory(), terminal);
            if (!menu.order(new ItemStack(Items.OAK_PLANKS), 4)) throw new GameTestAssertException("Bestellung abgelehnt: " + menu.lastMessage().getString());
            charge(be);
            h.runAfterDelay(2 * a.cycleTicks() + 60, () -> {
                long n = StorageNet.access(h.getLevel(), terminal).count(ItemKey.of(new ItemStack(Items.OAK_PLANKS)));
                if (n != 4) throw new GameTestAssertException("Bretter im Speicher: " + n + ", Status " + a.status());
                pr.energy().drain(pr.energy().getEnergyStored());
                h.runAfterDelay(60, () -> {
                    if (pr.rack().online()) throw new GameTestAssertException("Muster-Rack ohne Strom noch online");
                    if (a.hasPattern(ItemKey.of(new ItemStack(Items.OAK_PLANKS)))) throw new GameTestAssertException("Muster ohne Strom noch bestellbar");
                    h.succeed();
                });
            });
        });
    }
}
