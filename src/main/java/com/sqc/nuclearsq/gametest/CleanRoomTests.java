package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.robotics.RobotArmBlockEntity;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CleanRoomTests {
    private CleanRoomTests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static BlockPos controller(GameTestHelper h, BigMachine m) {
        return h.absolutePos(new BlockPos(2, 2, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m) {
        BlockPos ctrl = controller(h, m);
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        while (be.energy().receiveEnergy(1000, false) > 0) { }
        return be;
    }

    private static void chipThreeIngredients(BigMachineBlockEntity be) {
        be.items().insertItem(0, new ItemStack(item("chip_2_sqc")), false);
        be.items().insertItem(1, new ItemStack(item("silicon_pure_sqc"), 3), false);
        be.items().insertItem(2, new ItemStack(Items.ENDER_PEARL), false);
        be.items().insertItem(3, new ItemStack(Items.DIAMOND), false);
        be.items().insertItem(4, new ItemStack(Items.GOLD_INGOT, 2), false);
        be.items().insertItem(5, new ItemStack(item("hdpe_sheet_sqc")), false);
    }

    private static void dock(GameTestHelper h, BlockPos ctrl) {
        BlockPos armPos = ctrl.offset(4, 0, 0);
        h.getLevel().setBlockAndUpdate(armPos, RoboticsRegistry.ARM.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(armPos) instanceof RobotArmBlockEntity arm)) throw new GameTestAssertException("Greifarm hat keine BlockEntity");
        arm.setTarget(ctrl, Direction.UP);
    }

    @GameTest(template = FLOOR, batch = "clean_room", timeoutTicks = 200)
    public static void cleanRoomNeedsAnArmForChipThree(GameTestHelper h) {
        BigMachine m = BigMachine.CLEAN_ROOM;
        BigMachineBlockEntity be = build(h, m);
        BlockPos ctrl = controller(h, m);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Reinraum wird nicht als aufgebaut erkannt (" + be.matched() + "/" + be.total() + ")");
            if (be.items() == null || be.items().getSlots() != 10) throw new GameTestAssertException("Kein 3x3-Raster mit Ergebnisfeld");
            chipThreeIngredients(be);
            h.runAfterDelay(5, () -> {
                if (be.crafter().partsRecipe() != null) throw new GameTestAssertException("Chip III ohne Greifarm erkannt");
                if (be.crafter().status() != com.sqc.nuclearsq.machine.big.BigCrafter.NO_ARM) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt GREIFARM FEHLT");
                dock(h, ctrl);
                h.runAfterDelay(60, () -> {
                    if (be.crafter().partsRecipe() == null) throw new GameTestAssertException("Chip III mit angedocktem Greifarm nicht erkannt");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "clean_room", timeoutTicks = 200)
    public static void smallPartsAssemblerSkipsChipThree(GameTestHelper h) {
        BigMachine m = BigMachine.SMALL_PARTS_ASSEMBLER;
        BigMachineBlockEntity be = build(h, m);
        BlockPos ctrl = controller(h, m);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Kleinteil-Assembler nicht aufgebaut");
            chipThreeIngredients(be);
            dock(h, ctrl);
            h.runAfterDelay(60, () -> {
                if (be.crafter().partsRecipe() != null) throw new GameTestAssertException("Kleinteil-Assembler erkennt Chip III");
                h.succeed();
            });
        });
    }
}
