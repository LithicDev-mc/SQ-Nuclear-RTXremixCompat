package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.EquipmentRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class EquipmentAssemblerTests {
    private EquipmentAssemblerTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.EQUIPMENT_ASSEMBLER;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, path);
    }

    @GameTest(template = FLOOR, batch = "equipment_assembler", timeoutTicks = 100)
    public static void equipmentAssemblerFindsRecipe(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(id("equipment_assembly/hazmat_1_helmet_sqc"));
        if (holder.isEmpty() || !(holder.get().value() instanceof EquipmentRecipe r)) throw new GameTestAssertException("Rezept equipment_assembly/hazmat_1_helmet_sqc fehlt");
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Ausruestungs-Assembler wird nicht als aufgebaut erkannt (" + be.matched() + "/" + be.total() + ")");
            if (be.items() == null || be.items().getSlots() != 10) throw new GameTestAssertException("Kein 3x3-Raster mit Ergebnisfeld");
            int slot = 0;
            for (var n : r.base().ingredients()) {
                ItemStack s = n.ingredient().getItems()[0];
                be.items().insertItem(slot++, s.copyWithCount(n.count()), false);
            }
            h.runAfterDelay(5, () -> {
                if (be.crafter().partsRecipe() != r.base()) throw new GameTestAssertException("Rezept nicht erkannt: " + be.crafter().partsRecipe());
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "equipment_assembler", timeoutTicks = 100)
    public static void exoLeggingsNotBoots(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(id("equipment_assembly/exo_leggings_sqc"));
        if (holder.isEmpty() || !(holder.get().value() instanceof EquipmentRecipe r)) throw new GameTestAssertException("Rezept equipment_assembly/exo_leggings_sqc fehlt");
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Ausruestungs-Assembler wird nicht als aufgebaut erkannt");
            int slot = 0;
            for (var n : r.base().ingredients()) be.items().insertItem(slot++, n.ingredient().getItems()[0].copyWithCount(n.count()), false);
            h.runAfterDelay(5, () -> {
                if (be.crafter().partsRecipe() != r.base()) throw new GameTestAssertException("Exo-Hose wird nicht erkannt: " + be.crafter().partsRecipe());
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "equipment_assembler", timeoutTicks = 20)
    public static void equipmentAssemblerKitComesFromReactorAssembler(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        if (rm.byKey(id("assembly/equipment_assembler_kit_sqc")).isEmpty()) throw new GameTestAssertException("Kit-Rezept fehlt");
        if (rm.getAllRecipesFor(EquipmentRegistry.TYPE.get()).isEmpty()) throw new GameTestAssertException("Keine Rezepte nuclearsq:equipment_assembly geladen");
        h.succeed();
    }
}
