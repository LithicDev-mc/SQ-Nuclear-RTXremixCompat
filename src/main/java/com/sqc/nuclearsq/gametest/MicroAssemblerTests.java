package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
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
public final class MicroAssemblerTests {
    private MicroAssemblerTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.MICRO_ASSEMBLER;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BlockPos min, BigMachine m) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        return be;
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(1000, false) > 0) { }
    }

    @GameTest(template = FLOOR, batch = "micro_assembler", timeoutTicks = 300)
    public static void microAssemblerBuildsChipOne(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, new BlockPos(2, 2, 2), M);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Mikro-Assembler wird nicht als aufgebaut erkannt (" + be.matched() + "/" + be.total() + ")");
            BigCrafter c = be.crafter();
            if (be.items() == null || be.items().getSlots() != 10) throw new GameTestAssertException("Kein 3x3-Raster mit Ergebnisfeld");
            be.items().insertItem(0, new ItemStack(item("silicon_wafer_sqc")), false);
            be.items().insertItem(1, new ItemStack(Items.COPPER_INGOT), false);
            be.items().insertItem(2, new ItemStack(Items.REDSTONE, 2), false);
            charge(be);
            h.runAfterDelay(5, () -> {
                if (c.partsRecipe() == null) throw new GameTestAssertException("Rezept fuer Chip I nicht erkannt");
                if (c.energyPerTick() != M.extra("fe_per_tick")) throw new GameTestAssertException("360 FE/t ohne Rechenstufe erwartet, ist " + c.energyPerTick());
                h.runAfterDelay(120, () -> {
                    if (!be.items().getStackInSlot(9).is(item("chip_1_sqc"))) throw new GameTestAssertException("Kein Chip I im Ergebnisfeld: " + be.items().getStackInSlot(9));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "micro_assembler", timeoutTicks = 100)
    public static void microAssemblerSkipsChipTwo(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, new BlockPos(2, 2, 2), M);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Mikro-Assembler wird nicht als aufgebaut erkannt");
            be.items().insertItem(0, new ItemStack(item("chip_1_sqc")), false);
            be.items().insertItem(1, new ItemStack(item("silicon_pure_sqc")), false);
            be.items().insertItem(2, new ItemStack(item("rubber_sqc")), false);
            be.items().insertItem(4, new ItemStack(item("photoresist_sqc"), 2), false);
            be.items().insertItem(3, new ItemStack(Items.GOLD_INGOT, 2), false);
            charge(be);
            h.runAfterDelay(5, () -> {
                if (be.crafter().partsRecipe() != null) throw new GameTestAssertException("Mikro-Assembler erkennt ein Rezept fuer Chip II");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "micro_assembler", timeoutTicks = 20)
    public static void microAssemblerKitComesFromSmallPartsAssembler(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        if (rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "small_parts_assembly/micro_assembler_kit_sqc")).isEmpty()) throw new GameTestAssertException("Kit-Rezept fehlt");
        var r = rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "small_parts_assembly/micro_assembler_kit_sqc")).get().value();
        if (!(r instanceof com.sqc.nuclearsq.assembler.SmallPartsRecipe)) throw new GameTestAssertException("Kit-Rezept ist kein Kleinteil-Rezept: " + r.getType());
        h.succeed();
    }
}
