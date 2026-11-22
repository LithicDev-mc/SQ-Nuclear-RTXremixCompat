package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.DriveContents;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.ItemPortBlock;
import com.sqc.nuclearsq.storage.ItemPortBlockEntity;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StoragePriorityExportTests {
    private StoragePriorityExportTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min);
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || be.rack() == null) {
            throw new GameTestAssertException(m.id + ": kein Rack-Controller");
        }
        be.energy().add(be.energy().getMaxEnergyStored());
        return be;
    }

    private static BlockPos behind(BigMachineBlockEntity be) {
        BlockPos c = be.getBlockPos(), port = be.machine().structure.world(c, 0, 0, 0, 1);
        return port.offset(port.subtract(c));
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

    @GameTest(template = FLOOR, batch = "storage_prio_a", timeoutTicks = 120)
    public static void storagePriorityBeatsExistingStack(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BigMachineBlockEntity b = build(h, BigMachine.SERVER_RACK, new BlockPos(6, 2, 2));
        BlockPos ba = behind(a), bb = behind(b);
        cable(h, ba, bb);
        a.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_2.get()));
        b.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_2.get()));
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        h.runAfterDelay(25, () -> {
            if (!a.rack().online() || !b.rack().online()) throw new GameTestAssertException("Racks nicht online");
            StorageAccess s = StorageNet.access(h.getLevel(), ba);
            if (s.drives() != 2) throw new GameTestAssertException("Netz sieht " + s.drives() + " Platten statt 2");
            if (s.insert(cobble, 100, false) != 100) throw new GameTestAssertException("Einlagern fehlgeschlagen");
            DriveContents ca = a.rack().contents(h.getLevel().getServer(), 0), cb = b.rack().contents(h.getLevel().getServer(), 0);
            if (ca == null || cb == null) throw new GameTestAssertException("Platteninhalt fehlt");
            BigMachineBlockEntity low = ca.count(cobble) > 0 ? a : b, high = low == a ? b : a;
            DriveContents cl = low == a ? ca : cb, ch = low == a ? cb : ca;
            if (cl.count(cobble) != 100) throw new GameTestAssertException("gleiche Prioritaet verteilt auf beide Racks");
            high.rack().setPriority(5);
            StorageAccess s2 = StorageNet.access(h.getLevel(), ba);
            if (s2.insert(cobble, 50, false) != 50) throw new GameTestAssertException("zweites Einlagern fehlgeschlagen");
            if (ch.count(cobble) != 50 || cl.count(cobble) != 100) {
                throw new GameTestAssertException("Prioritaet ignoriert: Rack mit 5 hat " + ch.count(cobble) + ", Rack mit 0 hat " + cl.count(cobble));
            }
            if (s2.extract(cobble, 30, false) != 30 || cl.count(cobble) != 70 || ch.count(cobble) != 50) {
                throw new GameTestAssertException("Auslagern nicht zuerst aus der niedrigsten Prioritaet");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_prio_b", timeoutTicks = 120)
    public static void itemPortOffersFilterItemsWithoutInventory(GameTestHelper h) {
        BigMachineBlockEntity rack = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        rack.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        BlockPos ctrl = rack.getBlockPos();
        BlockPos at = rack.machine().structure.world(ctrl, 0, 0, 0, 1);
        BlockPos port = at.offset(at.subtract(ctrl));
        for (BlockPos p : BlockPos.betweenClosed(port.offset(-2, -1, 0), port.offset(2, 2, 3))) {
            h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        Direction facing = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (h.getLevel().getBlockState(port.relative(d)).isAir()) {
                facing = d;
                break;
            }
        }
        if (facing == null) throw new GameTestAssertException("keine freie Seite am Port");
        h.getLevel().setBlockAndUpdate(port, StorageRegistry.ITEM_PORT.get().defaultBlockState().setValue(ItemPortBlock.FACING, facing));
        if (!(h.getLevel().getBlockEntity(port) instanceof ItemPortBlockEntity be)) throw new GameTestAssertException("kein Item-Port");
        h.runAfterDelay(10, () -> {
            StorageNet.access(h.getLevel(), port).insert(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 20, false);
            be.toggleFilter(new ItemStack(Items.IRON_INGOT));
        });
        h.runAfterDelay(60, () -> {
            IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, port, null);
            if (cap == null) throw new GameTestAssertException("Port ohne Item-Handler");
            ItemStack shown = cap.getStackInSlot(1);
            if (!shown.is(Items.IRON_INGOT) || shown.getCount() != 20) {
                throw new GameTestAssertException("Port zeigt ohne Inventar davor nichts an: " + shown + " (" + be.problem() + ")");
            }
            h.succeed();
        });
    }
}
