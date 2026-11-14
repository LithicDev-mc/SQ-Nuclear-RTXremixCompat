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
public final class HeavyWaterTests {
    private HeavyWaterTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("4c1d2e30-7b64-4a9f-8d1e-2f3a4b5c6d70"), "nsq_heavy");

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

    @GameTest(template = FLOOR, batch = "heavy_plant_fluid", timeoutTicks = 700)
    public static void plantMakesHeavyWater(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.HEAVY_WATER_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, "water_in", Fluids.WATER, 16000);
        var in = be.itemsAt("item_in");
        if (in.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty()) throw new GameTestAssertException("Anlage nimmt Erde an");
        in.insertItem(0, new ItemStack(item("sulfur_dust_sqc")), false);
        h.runAfterDelay(520, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Schwerwasseranlage nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            var t = be.tank("heavy_out");
            if (t.getFluidAmount() != 100 || !t.getFluid().is(ModFluids.HEAVY_WATER.source.get())) throw new GameTestAssertException("Schweres Wasser: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("water_in").getFluidAmount() != 8000) throw new GameTestAssertException("Wasserverbrauch falsch: " + be.tank("water_in").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "heavy_plant_cell", timeoutTicks = 900)
    public static void plantMakesTheCell(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.HEAVY_WATER_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, "water_in", Fluids.WATER, 16000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("glass_seal_sqc"), 2), false);
        h.runAfterDelay(720, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Schwerwasseranlage nicht aufgebaut");
            int n = out(be, item("heavy_water_cell_sqc"));
            if (n != 1) throw new GameTestAssertException("Erwartet 1 Zelle, ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("water_in").getFluidAmount() != 0) throw new GameTestAssertException("Wasser nicht verbraucht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "heavy_separator", timeoutTicks = 160)
    public static void separatorUsesHeavyWater(GameTestHelper h) {
        BigMachineBlockEntity sep = build(h, BigMachine.DEUTERIUM_SEPARATOR, new BlockPos(2, 4, 2));
        h.onEachTick(() -> sep.energy().receiveEnergy(sep.energy().getMaxEnergyStored(), false));
        fill(sep, "water_in", ModFluids.HEAVY_WATER.source.get(), 6400);
        h.runAfterDelay(100, () -> {
            int d = sep.tank("deuterium_out").getFluidAmount();
            int used = 6400 - sep.tank("water_in").getFluidAmount();
            if (d < 150) throw new GameTestAssertException("Zu wenig Deuterium aus Schwerwasser: " + d + " mB");
            if (used > d * 3) throw new GameTestAssertException("Schwerwasser-Verbrauch " + used + " mB fuer " + d + " mB Deuterium");
            if (!sep.tank("deuterium_out").getFluid().is(ModFluids.DEUTERIUM.source.get())) throw new GameTestAssertException("falsche Fluessigkeit");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "heavy_moderator", timeoutTicks = 20)
    public static void heavyWaterModeratorIsStageFive(GameTestHelper h) {
        if (Math.abs(com.sqc.nuclearsq.reactor.PartStages.moderatorPower(5) - 4.0f) > 0.001f) throw new GameTestAssertException("Leistung Stufe 5");
        if (Math.abs(com.sqc.nuclearsq.reactor.PartStages.moderatorHeat(5) - 4.2f) > 0.001f) throw new GameTestAssertException("Hitze Stufe 5");
        if (com.sqc.nuclearsq.reactor.PartStages.moderatorStage(new ItemStack(item("moderator_heavy_water_sqc"))) != 5) throw new GameTestAssertException("Item nicht erkannt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "heavy_data", timeoutTicks = 20)
    public static void heavyWaterIsInTheDataPack(GameTestHelper h) {
        boolean fluid = false, cell = false;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r) || r.kind() != ReprocessingRecipe.Kind.HEAVY_WATER) continue;
            fluid |= r.fluidResults().stream().anyMatch(o -> o.port().equals("heavy_out"));
            cell |= r.results().size() == 1 && r.results().get(0).stack().is(item("heavy_water_cell_sqc"));
        }
        if (!fluid) throw new GameTestAssertException("Schwerwasser-Rezept fehlt");
        if (!cell) throw new GameTestAssertException("Zellen-Rezept fehlt");
        for (String id : new String[] {"heavy_water_plant_kit_sqc", "heavy_water_plant_controller_sqc", "heavy_water_cell_sqc", "moderator_heavy_water_sqc"}) item(id);
        h.succeed();
    }
}
