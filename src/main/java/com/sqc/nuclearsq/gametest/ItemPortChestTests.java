package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.ItemPortBlock;
import com.sqc.nuclearsq.storage.ItemPortBlockEntity;
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
public final class ItemPortChestTests {
    private ItemPortChestTests() {}

    private static final String FLOOR = "floor";

    private record Setup(BlockPos port, BlockPos chest, ItemPortBlockEntity be) {}

    private static Setup build(GameTestHelper h, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min);
        BigMachine m = BigMachine.SERVER_RACK_SMALL;
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || be.rack() == null) {
            throw new GameTestAssertException("kein Rack-Controller");
        }
        be.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        be.energy().add(be.energy().getMaxEnergyStored());
        BlockPos at = m.structure.world(ctrl, 0, 0, 0, 1);
        BlockPos port = at.offset(at.subtract(ctrl));
        for (BlockPos p : BlockPos.betweenClosed(port.offset(-2, -1, 0), port.offset(2, 2, 3))) {
            h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        Direction facing = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = port.relative(d);
            if (h.getLevel().getBlockState(n).isAir()) {
                facing = d;
                break;
            }
        }
        if (facing == null) throw new GameTestAssertException("keine freie Seite fuer die Kiste bei " + port + " (Controller " + ctrl + ", Rack-Port " + at + ")");
        BlockPos chest = port.relative(facing);
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        h.getLevel().setBlockAndUpdate(port, StorageRegistry.ITEM_PORT.get().defaultBlockState().setValue(ItemPortBlock.FACING, facing));
        if (!(h.getLevel().getBlockEntity(port) instanceof ItemPortBlockEntity pbe)) throw new GameTestAssertException("kein Item-Port");
        return new Setup(port, chest, pbe);
    }

    private static IItemHandler chest(GameTestHelper h, Setup s) {
        IItemHandler inv = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.chest(), null);
        if (inv == null) throw new GameTestAssertException("Kiste ohne Item-Handler");
        return inv;
    }

    private static long net(GameTestHelper h, Setup s, ItemStack st) {
        return StorageNet.access(h.getLevel(), s.port()).count(ItemKey.of(st));
    }

    private static int inChest(IItemHandler inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    @GameTest(template = FLOOR, batch = "item_port_a", timeoutTicks = 120)
    public static void itemPortImportsEverything(GameTestHelper h) {
        Setup s = build(h, new BlockPos(2, 2, 2));
        IItemHandler inv = chest(h, s);
        inv.insertItem(0, new ItemStack(Items.COBBLESTONE, 40), false);
        inv.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
        h.runAfterDelay(70, () -> {
            if (inChest(inv, Items.COBBLESTONE) != 0 || inChest(inv, Items.DIAMOND) != 0) throw new GameTestAssertException("Kiste nicht leer: " + s.be().problem());
            if (net(h, s, new ItemStack(Items.COBBLESTONE)) != 40 || net(h, s, new ItemStack(Items.DIAMOND)) != 5) throw new GameTestAssertException("Netz hat nicht alles");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_port_a", timeoutTicks = 120)
    public static void itemPortImportRespectsFilter(GameTestHelper h) {
        Setup s = build(h, new BlockPos(2, 2, 2));
        IItemHandler inv = chest(h, s);
        inv.insertItem(0, new ItemStack(Items.COBBLESTONE, 40), false);
        inv.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
        if (!s.be().toggleFilter(new ItemStack(Items.DIAMOND))) throw new GameTestAssertException("Filter nicht gesetzt");
        h.runAfterDelay(70, () -> {
            if (inChest(inv, Items.DIAMOND) != 0 || net(h, s, new ItemStack(Items.DIAMOND)) != 5) throw new GameTestAssertException("Diamanten nicht eingelagert");
            if (inChest(inv, Items.COBBLESTONE) != 40 || net(h, s, new ItemStack(Items.COBBLESTONE)) != 0) throw new GameTestAssertException("Bruchstein trotz Filter bewegt");
            if (s.be().toggleFilter(new ItemStack(Items.DIAMOND)) || s.be().filterCount() != 0) throw new GameTestAssertException("Filtereintrag nicht entfernt");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_port_b", timeoutTicks = 160)
    public static void itemPortExportsFilterItems(GameTestHelper h) {
        Setup s = build(h, new BlockPos(2, 2, 2));
        IItemHandler inv = chest(h, s);
        h.runAfterDelay(10, () -> {
            var access = StorageNet.access(h.getLevel(), s.port());
            access.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 100, false);
            access.insert(ItemKey.of(new ItemStack(Items.DIAMOND)), 7, false);
            s.be().setMode(ItemPortBlockEntity.EXPORT);
        });
        h.runAfterDelay(50, () -> {
            if (inChest(inv, Items.COBBLESTONE) != 0 || !"no_filter".equals(s.be().problem())) throw new GameTestAssertException("ohne Filter ausgelagert: " + s.be().problem());
            s.be().toggleFilter(new ItemStack(Items.COBBLESTONE));
        });
        h.runAfterDelay(120, () -> {
            int c = inChest(inv, Items.COBBLESTONE);
            if (c < 64) throw new GameTestAssertException("zu wenig ausgelagert: " + c);
            if (c + net(h, s, new ItemStack(Items.COBBLESTONE)) != 100) throw new GameTestAssertException("Bruchstein verloren oder verdoppelt");
            if (inChest(inv, Items.DIAMOND) != 0) throw new GameTestAssertException("Diamanten ohne Filter ausgelagert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_port_c", timeoutTicks = 120)
    public static void itemPortActsAsItemHandler(GameTestHelper h) {
        Setup s = build(h, new BlockPos(2, 2, 2));
        h.runAfterDelay(10, () -> {
            IItemHandler port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.port(), null);
            if (port == null) throw new GameTestAssertException("Port ohne Item-Handler");
            ItemStack rest = port.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            if (!rest.isEmpty() || net(h, s, new ItemStack(Items.IRON_INGOT)) != 10) throw new GameTestAssertException("Einstecken lagert nicht ein");
            if (!port.extractItem(1, 4, false).isEmpty()) throw new GameTestAssertException("Herausziehen ohne Filter");
            s.be().toggleFilter(new ItemStack(Items.IRON_INGOT));
            ItemStack got = port.extractItem(1, 4, false);
            if (got.getCount() != 4 || net(h, s, new ItemStack(Items.IRON_INGOT)) != 6) throw new GameTestAssertException("Herausziehen: " + got);
            s.be().setEnabled(false);
            if (port.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty()) throw new GameTestAssertException("ausgeschalteter Port nimmt an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_port_c", timeoutTicks = 60)
    public static void itemPortHasDataPortWithMode(GameTestHelper h) {
        Setup s = build(h, new BlockPos(2, 2, 2));
        h.runAfterDelay(5, () -> {
            if (!DataSources.isPort(h.getLevel(), s.port())) throw new GameTestAssertException("Item-Port ist kein Datenanschluss");
            DataSource d = DataSources.at(h.getLevel(), s.port());
            if (d == null || d.value("mode") == null || d.value("moved") == null || d.value("enable") == null) throw new GameTestAssertException("Werte fehlen");
            if (d.set("mode", 1) != null || !s.be().exporting()) throw new GameTestAssertException("mode nicht gesetzt");
            if (d.set("mode", 2) == null) throw new GameTestAssertException("mode 2 wird angenommen");
            if (d.set("enable", 0) != null || s.be().enabled()) throw new GameTestAssertException("enable nicht gesetzt");
            h.succeed();
        });
    }
}
