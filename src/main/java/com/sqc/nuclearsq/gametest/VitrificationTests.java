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
public final class VitrificationTests {
    private VitrificationTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("6e3f4052-9d86-4cb0-8f30-4b5c6d7e8f92"), "nsq_vitrify");

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

    private static BigMachineBlockEntity plant(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.VITRIFICATION_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        return be;
    }

    @GameTest(template = FLOOR, batch = "vitrify_residue", timeoutTicks = 500)
    public static void residueDissolvesToHlw(GameTestHelper h) {
        BigMachineBlockEntity be = plant(h);
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("filter_residue_sqc"), 8), false);
        h.runAfterDelay(380, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Verglasungsanlage nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            var t = be.tank("hlw_out");
            if (t.getFluidAmount() != 500 || !t.getFluid().is(ModFluids.HLW.source.get())) throw new GameTestAssertException("HLW: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("acid_in").getFluidAmount() != 1600) throw new GameTestAssertException("Saeureverbrauch falsch: " + be.tank("acid_in").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "vitrify_resin", timeoutTicks = 500)
    public static void spentResinDissolvesToHlw(GameTestHelper h) {
        BigMachineBlockEntity be = plant(h);
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("spent_resin_sqc"), 4), false);
        h.runAfterDelay(300, () -> {
            var t = be.tank("hlw_out");
            if (t.getFluidAmount() != 200 || !t.getFluid().is(ModFluids.HLW.source.get())) throw new GameTestAssertException("HLW: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "vitrify_canister", timeoutTicks = 900)
    public static void hlwAndSandMakeACanister(GameTestHelper h) {
        BigMachineBlockEntity be = plant(h);
        fill(be, "hlw_out", ModFluids.HLW.source.get(), 1000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(Items.SAND, 4), false);
        h.runAfterDelay(700, () -> {
            int n = out(be, item("glass_canister_sqc"));
            if (n != 1) throw new GameTestAssertException("Erwartet 1 Glaskokille, ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("hlw_out").getFluidAmount() != 500) throw new GameTestAssertException("HLW-Verbrauch falsch: " + be.tank("hlw_out").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "vitrify_radiation", timeoutTicks = 40)
    public static void canisterIsWasteAndRadiates(GameTestHelper h) {
        ItemStack c = new ItemStack(item("glass_canister_sqc"), 2);
        if (!com.sqc.nuclearsq.spent.SpentFuel.isWaste(c)) throw new GameTestAssertException("Kokille darf nicht ins Endlager");
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
        p.getInventory().clearContent();
        double without = com.sqc.nuclearsq.spent.SpentFuel.carried(p);
        p.getInventory().add(c);
        double with = com.sqc.nuclearsq.spent.SpentFuel.carried(p);
        if (Math.abs((with - without) - 2 * com.sqc.nuclearsq.radiation.RadiationValues.CANISTER_CARRY_SV_H) > 1e-9) throw new GameTestAssertException("Strahlung der Kokillen: " + (with - without));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "vitrify_data", timeoutTicks = 20)
    public static void vitrificationIsInTheDataPack(GameTestHelper h) {
        int n = 0;
        boolean canister = false, hlwOut = false;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r) || r.kind() != ReprocessingRecipe.Kind.VITRIFICATION) continue;
            n++;
            canister |= r.results().size() == 1 && r.results().get(0).stack().is(item("glass_canister_sqc"));
            hlwOut |= r.fluidResults().stream().anyMatch(o -> o.port().equals("hlw_out"));
        }
        if (n != 5) throw new GameTestAssertException("Verglasungs-Rezepte: " + n + " statt 5");
        if (!canister) throw new GameTestAssertException("Kokillen-Rezept fehlt");
        if (!hlwOut) throw new GameTestAssertException("Loese-Rezept ohne HLW");
        for (String id : new String[] {"vitrification_plant_kit_sqc", "vitrification_plant_controller_sqc", "glass_canister_sqc"}) item(id);
        h.succeed();
    }
}
