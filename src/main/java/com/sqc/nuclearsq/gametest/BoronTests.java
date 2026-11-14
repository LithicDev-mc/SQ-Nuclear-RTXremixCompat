package com.sqc.nuclearsq.gametest;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BoronTests {
    private BoronTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("9b3c4d10-6a52-4f8e-b0c7-1d2e3f405162"), "nsq_boron");

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
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

    @GameTest(template = FLOOR, batch = "boron_core", timeoutTicks = 40)
    public static void boronLowersThePowerUntilItIsExchanged(GameTestHelper h) {
        CoreChemistry c = new CoreChemistry();
        if (c.powerFactor() != 1f || c.boron() != 0) throw new GameTestAssertException("Frischer Kern hat Bor");
        for (int i = 0; i < CoreChemistry.BORON_MAX / CoreChemistry.BORON_PER_ITEM; i++) {
            if (!c.dose()) throw new GameTestAssertException("Dosierung " + i + " abgelehnt");
        }
        if (c.dose() || c.boron() != CoreChemistry.BORON_MAX) throw new GameTestAssertException("Hoechstwert nicht gehalten: " + c.boron());
        if (Math.abs(c.powerFactor() - 0.5f) > 0.001f) throw new GameTestAssertException("Faktor bei 1000 ppm: " + c.powerFactor());
        c.exchange();
        if (c.boron() != CoreChemistry.BORON_MAX - CoreChemistry.EXCHANGE_BORON) throw new GameTestAssertException("Tauscher nimmt Bor falsch: " + c.boron());
        c.setWater(500, 0f);
        c.set(0f, 65f);
        float both = c.powerFactor();
        c.set(0f, 0f);
        if (both >= c.powerFactor() * 0.99f || both <= 0f) throw new GameTestAssertException("Xenon und Bor wirken nicht zusammen: " + both);
        c.set(0f, 100f);
        if (c.powerFactor() != 0f) throw new GameTestAssertException("Xenon-Stopp wirkt mit Bor nicht mehr");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "boron_crud", timeoutTicks = 60)
    public static void crudGrowsWithPowerAndWeakensCooling(GameTestHelper h) {
        CoreChemistry c = new CoreChemistry();
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 250, 2)));
        for (int i = 1; i <= 200; i++) c.tick(h.getLevel(), at, i, 0f, 1f);
        if (c.crud() != 0f) throw new GameTestAssertException("Ablagerungen ohne Leistung: " + c.crud());
        for (int i = 201; i <= 400; i++) c.tick(h.getLevel(), at, i, 1f, 1f);
        float expect = 200 * CoreChemistry.CRUD_RISE;
        if (Math.abs(c.crud() - expect) > 0.01f) throw new GameTestAssertException("Ablagerungen " + c.crud() + " statt " + expect);
        if (c.crudCoolingFactor() >= 1f) throw new GameTestAssertException("Schon ein bisschen Belag aendert nichts an der Kuehlung");
        c.setWater(0, 100f);
        if (Math.abs(c.crudCoolingFactor() - 0.85f) > 0.001f) throw new GameTestAssertException("Kuehlfaktor bei 100 %: " + c.crudCoolingFactor());
        c.exchange();
        if (Math.abs(c.crud() - 65f) > 0.01f) throw new GameTestAssertException("Tauscher: " + c.crud() + " statt 65");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "boron_dose", timeoutTicks = 40)
    public static void boricAcidDosesBoronByRightClick(GameTestHelper h) {
        BigMachineBlockEntity drive = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("boric_acid_sqc"), 3));
        if (!drive.installPart(p, InteractionHand.MAIN_HAND) || drive.core().chem().boron() != CoreChemistry.BORON_PER_ITEM || p.getMainHandItem().getCount() != 2) {
            throw new GameTestAssertException("Borsaeure nicht dosiert: " + drive.core().chem().boron() + " ppm, " + p.getMainHandItem().getCount() + " Stueck");
        }
        drive.core().chem().setWater(CoreChemistry.BORON_MAX, 0f);
        if (!drive.installPart(p, InteractionHand.MAIN_HAND) || p.getMainHandItem().getCount() != 2) throw new GameTestAssertException("Bei vollem Bor wurde Borsaeure verbraucht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "boric_plant", timeoutTicks = 400)
    public static void boricAcidPlantDissolvesBorax(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.BORIC_ACID_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 1000);
        fill(be, "water_in", Fluids.WATER, 1000);
        var in = be.itemsAt("item_in");
        if (in.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt Erde an");
        in.insertItem(0, new ItemStack(item("borax_sqc")), false);
        h.runAfterDelay(260, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Borsaeure-Anlage nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            int acid = out(be, item("boric_acid_sqc"));
            if (acid != 4) throw new GameTestAssertException("Erwartet 4 Borsaeure, ist " + acid + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("acid_in").getFluidAmount() != 750 || be.tank("water_in").getFluidAmount() != 800) throw new GameTestAssertException("Fluessigkeiten falsch verbraucht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "boron_exchanger", timeoutTicks = 700)
    public static void ionExchangerCleansTheNextReactor(GameTestHelper h) {
        BigMachineBlockEntity drive = build(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        BigMachineBlockEntity ex = build(h, BigMachine.ION_EXCHANGER, new BlockPos(2, 4, 10));
        h.onEachTick(() -> ex.energy().receiveEnergy(ex.energy().getMaxEnergyStored(), false));
        drive.core().chem().setWater(500, 80f);
        fill(ex, "primary_in", ModFluids.PRIMARY_WATER.source.get(), 2000);
        ex.itemsAt("item_in").insertItem(0, new ItemStack(item("ion_resin_sqc")), false);
        h.runAfterDelay(520, () -> {
            if (!ex.isFormed() || !drive.isFormed()) throw new GameTestAssertException("Anlage nicht aufgebaut");
            CoreChemistry c = drive.core().chem();
            if (c.boron() != 500 - CoreChemistry.EXCHANGE_BORON) throw new GameTestAssertException("Bor " + c.boron() + " ppm (Zustand " + ex.crafter().status() + ")");
            if (Math.abs(c.crud() - (80f - CoreChemistry.EXCHANGE_CRUD)) > 1f) throw new GameTestAssertException("Ablagerungen " + c.crud());
            if (out(ex, item("spent_resin_sqc")) != 1) throw new GameTestAssertException("Kein verbrauchtes Harz im Ausgang");
            if (ex.tank("primary_out").getFluidAmount() != 1000 || !ex.tank("primary_out").getFluid().is(ModFluids.PRIMARY_WATER.source.get())) {
                throw new GameTestAssertException("Gereinigtes Primaerwasser fehlt: " + ex.tank("primary_out").getFluid());
            }
            if (ex.tank("primary_in").getFluidAmount() != 1000) throw new GameTestAssertException("Primaerwasser-Zulauf falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "boron_data", timeoutTicks = 20)
    public static void waterChemistryIsInTheDataPack(GameTestHelper h) {
        boolean boric = false, exchange = false;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            if (r.kind() == ReprocessingRecipe.Kind.BORIC_ACID) {
                boric |= r.fluids().size() == 2 && r.results().size() == 1 && r.results().get(0).stack().is(item("boric_acid_sqc"));
            } else if (r.kind() == ReprocessingRecipe.Kind.ION_EXCHANGE) {
                exchange |= r.fluids().stream().anyMatch(c -> c.port().equals("primary_in")) && r.fluidResults().stream().anyMatch(o -> o.port().equals("primary_out"));
            }
        }
        if (!com.sqc.nuclearsq.spent.SpentFuel.isWaste(new ItemStack(item("spent_resin_sqc")))) throw new GameTestAssertException("Verbrauchtes Harz darf nicht ins Endlager");
        if (!boric) throw new GameTestAssertException("Borsaeure-Rezept fehlt");
        if (!exchange) throw new GameTestAssertException("Ionentauscher-Rezept fehlt");
        for (String id : new String[] {"boric_acid_plant_kit_sqc", "boric_acid_plant_controller_sqc", "ion_exchanger_kit_sqc", "ion_exchanger_controller_sqc",
                "borax_sqc", "borax_ore_sqc", "deepslate_borax_ore_sqc", "boric_acid_sqc", "ion_resin_sqc", "spent_resin_sqc"}) item(id);
        h.succeed();
    }
}
