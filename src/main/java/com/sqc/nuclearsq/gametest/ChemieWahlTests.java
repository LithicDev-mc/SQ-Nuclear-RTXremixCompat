package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ChemieWahlTests {
    private ChemieWahlTests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static int out(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    private static int insert(IItemHandler h, ItemStack stack) {
        for (int i = 0; i < h.getSlots(); i++) if (h.insertItem(i, stack.copy(), false).isEmpty()) return i;
        return -1;
    }

    @GameTest(template = FLOOR, batch = "chemie_wahl_vitrify", timeoutTicks = 900)
    public static void vitrificationPrefersNitricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.VITRIFICATION_PLANT);
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 4000);
        fill(be, "nitric_in", ModFluids.NITRIC_ACID.source.get(), 4000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("filter_residue_sqc"), 8), false);
        h.runAfterDelay(800, () -> {
            if (be.tank("nitric_in").getFluidAmount() != 3800 || be.tank("acid_in").getFluidAmount() != 4000 || be.tank("hlw_out").getFluidAmount() != 800)
                throw new GameTestAssertException("Salpetersaeure " + be.tank("nitric_in").getFluidAmount() + ", Schwefelsaeure " + be.tank("acid_in").getFluidAmount()
                        + ", HLW " + be.tank("hlw_out").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_wahl_polymer", timeoutTicks = 1200)
    public static void polymerAlternatesSulfurRecipes(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.POLYMER_PLANT);
        fill(be, "tar_in", ModFluids.TAR.source.get(), 4000);
        fill(be, "naphtha_in", ModFluids.NAPHTHA.source.get(), 4000);
        be.items().setStackInSlot(0, new ItemStack(item("sulfur_dust_sqc"), 3));
        h.runAfterDelay(1100, () -> {
            if (out(be, item("rubber_sqc")) != 6 || out(be, item("photoresist_sqc")) != 4)
                throw new GameTestAssertException("Gummi " + out(be, item("rubber_sqc")) + ", Photolack " + out(be, item("photoresist_sqc")));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_wahl_port", timeoutTicks = 900)
    public static void polymerPortPutsSulfurIntoAuxSlot(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.POLYMER_PLANT);
        fill(be, "water_in", Fluids.WATER, 4000);
        IItemHandler port = be.itemsAt("item_in");
        int s = insert(port, new ItemStack(item("sulfur_dust_sqc"), 1));
        int k = insert(port, new ItemStack(Items.DRIED_KELP, 8));
        if (s != ReprocessingRecipe.AUX_SLOT || k != 0) throw new GameTestAssertException("Schwefel in Platz " + s + ", Seetang in Platz " + k);
        h.runAfterDelay(800, () -> {
            if (out(be, item("rubber_sqc")) != 2) throw new GameTestAssertException("Gummi " + out(be, item("rubber_sqc")) + " statt 2");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemie_wahl_aux_main", timeoutTicks = 900)
    public static void polymerUsesAuxSulfurWithTar(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.POLYMER_PLANT);
        fill(be, "tar_in", ModFluids.TAR.source.get(), 4000);
        insert(be.itemsAt("item_in"), new ItemStack(item("sulfur_dust_sqc"), 2));
        h.runAfterDelay(800, () -> {
            if (out(be, item("rubber_sqc")) != 6 || be.tank("tar_in").getFluidAmount() != 3600)
                throw new GameTestAssertException("Gummi " + out(be, item("rubber_sqc")) + ", Teer " + be.tank("tar_in").getFluidAmount());
            h.succeed();
        });
    }
}
