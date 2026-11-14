package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

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
public final class ExoStationTests {
    private ExoStationTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.EXO_STATION;

    @GameTest(template = FLOOR, batch = "exo_station", timeoutTicks = 100)
    public static void exoStationTakesPiece(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Exo Station wird nicht als aufgebaut erkannt (" + be.matched() + "/" + be.total() + ")");
            var exo = be.exo();
            if (exo == null) throw new GameTestAssertException("Keine Kammer in der Exo Station");
            if (exo.insert(new ItemStack(net.minecraft.world.item.Items.STICK))) throw new GameTestAssertException("Ein Stock darf nicht in die Kammer");
            ItemStack held = new ItemStack(ExoRegistry.ARMOR.get(0).get());
            if (!exo.insert(held) || !held.isEmpty()) throw new GameTestAssertException("Exo-Teil kommt nicht in die Kammer");
            if (exo.piece().getItem(0).isEmpty()) throw new GameTestAssertException("Kammer ist trotz Teil leer");
            if (exo.insert(new ItemStack(ExoRegistry.ARMOR.get(1).get()))) throw new GameTestAssertException("Zweites Teil darf nicht in die belegte Kammer");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "exo_station", timeoutTicks = 20)
    public static void exoStationKitComesFromEquipmentAssembler(GameTestHelper h) {
        if (h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "equipment_assembly/exo_station_kit_sqc")).isEmpty())
            throw new GameTestAssertException("Kit-Rezept fehlt");
        h.succeed();
    }
}
