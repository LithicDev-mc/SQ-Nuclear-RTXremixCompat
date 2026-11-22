package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.RodShaft;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.PartStages;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PartChainTests {
    private PartChainTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("7c2a1d44-0b5e-4f19-8f0d-3a1c9b6e7d02"), "nsq_part_chain");

    private static BigMachineBlockEntity buildBig(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) {
            throw new GameTestAssertException(m.id + ": Controller hat kein BlockEntity");
        }
        return be;
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(1000, false) > 0) { }
    }

    private static BigMachineBlockEntity fourRodDrive(GameTestHelper h) {
        return fourRodDrive(h, true);
    }

    private static BigMachineBlockEntity fourRodDrive(GameTestHelper h, boolean flow) {
        BigMachineBlockEntity be = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        charge(be);
        be.tank("hydraulic_in").fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        for (int k = 0; k < 9; k++) {
            BlockPos head = be.rodBase(be.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        h.runAfterDelay(25, () -> {
            for (int k = 0; k < 9; k++) be.setShaftLocked(k, true);
            be.setRodTarget(15);
        });
        h.onEachTick(() -> {
            charge(be);
            if (!flow) return;
            be.addPrimaryFlow(4000);
            FluidTank in = be.tank("primary_cold_in");
            in.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), in.getSpace()), IFluidHandler.FluidAction.EXECUTE);
            FluidTank out = be.tank("primary_hot_out");
            out.drain(out.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
        });
        return be;
    }

    private static int rodFuel(GameTestHelper h, BigMachineBlockEntity be) {
        int sum = 0;
        for (int k = 0; k < 9; k++) {
            BlockPos head = be.rodBase(be.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                if (h.getLevel().getBlockEntity(head.below(i)) instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity f) sum += f.getRemaining();
            }
        }
        return sum;
    }

    private static void near(String what, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > Math.abs(expected) * tolerance) {
            throw new GameTestAssertException(what + ": " + actual + ", erwartet " + expected + " (+-" + Math.round(tolerance * 100) + " %)");
        }
    }

    @GameTest(template = FLOOR, batch = "part_chain_table", timeoutTicks = 20)
    public static void stageFactorsFollowTheDataFile(GameTestHelper h) {
        float[] power = {1.25f, 1.5f, 2f, 3f}, heat = {1.2f, 1.4f, 2.2f, 3.6f}, cool = {0.3f, 0.6f, 1.0f};
        int[] fe = {100, 110, 125, 150}, flow = {100, 150, 200, 300};
        for (int s = 1; s <= 4; s++) {
            near("Moderator-Leistung " + s, PartStages.moderatorPower(s), power[s - 1], 0.001);
            near("Moderator-Hitze " + s, PartStages.moderatorHeat(s), heat[s - 1], 0.001);
            near("Translator-FE " + s, PartStages.translatorFePct(s), fe[s - 1], 0.001);
            near("Translator-Durchsatz " + s, PartStages.translatorFlowPct(s), flow[s - 1], 0.001);
            if (PartStages.moderatorStage(PartStages.stack(PartStages.MODERATORS, s)) != s) throw new GameTestAssertException("Moderator-Item Stufe " + s);
            if (PartStages.translatorStage(PartStages.stack(PartStages.TRANSLATORS, s)) != s) throw new GameTestAssertException("Translator-Item Stufe " + s);
        }
        for (int s = 1; s <= 3; s++) {
            near("Kuehler " + s, PartStages.coolerCooling(s), cool[s - 1], 0.001);
            if (PartStages.coolerStage(PartStages.stack(PartStages.COOLERS, s)) != s) throw new GameTestAssertException("Kuehler-Item Stufe " + s);
        }
        if (PartStages.moderatorPower(0) != 1f || PartStages.moderatorHeat(0) != 1f || PartStages.coolerCooling(0) != 0f
                || PartStages.translatorFePct(0) != 100 || PartStages.translatorFlowPct(0) != 100) {
            throw new GameTestAssertException("Stufe 0 ist nicht neutral");
        }
        if (PartStages.moderatorStage(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL)) != 0) {
            throw new GameTestAssertException("Kohle gilt als Moderator");
        }
        h.succeed();
    }

    private record Phase(int ticks, Runnable start, Runnable end) {}

    private static boolean rodsDown(BigMachineBlockEntity drive) {
        for (RodShaft s : drive.shafts()) {
            int travel = s.headY() - RodShaft.FULL_HEAD_Y;
            if (s.headY() < 0 || travel <= 0 || s.shown() < travel - 0.01f) return false;
        }
        return true;
    }

    private static void runPhases(GameTestHelper h, BigMachineBlockEntity drive, int giveUpAt, List<Phase> phases) {
        int[] idx = {-1, 0};
        h.onEachTick(() -> {
            if (idx[0] == Integer.MAX_VALUE) return;
            if (idx[0] < 0) {
                if (!rodsDown(drive)) return;
                idx[0] = 0;
                idx[1] = 0;
                phases.get(0).start().run();
                return;
            }
            if (++idx[1] < phases.get(idx[0]).ticks()) return;
            phases.get(idx[0]).end().run();
            idx[1] = 0;
            if (++idx[0] < phases.size()) {
                phases.get(idx[0]).start().run();
                return;
            }
            idx[0] = Integer.MAX_VALUE;
            drive.setParts(0, 0);
            drive.core().setHeat(0f);
            drive.scram();
            h.succeed();
        });
        h.runAfterDelay(giveUpAt, () -> {
            if (idx[0] < 0) {
                StringBuilder sb = new StringBuilder();
                for (RodShaft s : drive.shafts()) sb.append(" [Kopf ").append(s.headY()).append(", gefahren ").append(s.shown()).append(']');
                throw new GameTestAssertException("Staebe nicht ganz eingezogen:" + sb + ", Stabstellung " + drive.rod() + ", gesperrt/Strom pruefen");
            }
        });
    }

    @GameTest(template = FLOOR, batch = "part_chain_burn", timeoutTicks = 900)
    public static void moderatorScalesPowerAndBurnTogether(GameTestHelper h) {
        BigMachineBlockEntity drive = fourRodDrive(h);
        long[] heated = new long[2];
        int[] on = {0};
        int[] fuel = new int[4];
        h.onEachTick(() -> {
            if (on[0] > 0) heated[on[0] - 1] += drive.vesselMb();
        });
        runPhases(h, drive, 800, List.of(
                new Phase(100, () -> {
                    fuel[0] = rodFuel(h, drive);
                    on[0] = 1;
                }, () -> {
                    on[0] = 0;
                    fuel[1] = rodFuel(h, drive);
                    if (drive.vesselDry()) throw new GameTestAssertException("Behaelter trocken ohne Moderator");
                    near("Primaerwasser ohne Moderator", drive.vesselMb(), 640, 0.03);
                }),
                new Phase(8, () -> drive.setParts(4, 3), () -> { }),
                new Phase(100, () -> {
                    fuel[2] = rodFuel(h, drive);
                    on[0] = 2;
                }, () -> {
                    on[0] = 0;
                    fuel[3] = rodFuel(h, drive);
                    near("Primaerwasser mit Fluorit-Moderator", drive.vesselMb(), 1920, 0.03);
                    int burnA = fuel[0] - fuel[1], burnB = fuel[2] - fuel[3];
                    if (burnA <= 0) throw new GameTestAssertException("Ohne Moderator kein Abbrand");
                    near("Abbrand mit Moderator (Faktor 3)", burnB, burnA * 3.0, 0.06);
                    near("Primaerwasser je Ladung", heated[1] / (double) burnB, heated[0] / (double) burnA, 0.06);
                })));
    }

    private static Phase heatPhase(BigMachineBlockEntity drive, int moderator, int cooler, boolean shouldRise) {
        float[] start = new float[1];
        return new Phase(100, () -> {
            drive.setParts(moderator, cooler);
            drive.core().setHeat(300f);
            start[0] = drive.core().heat();
        }, () -> {
            float diff = drive.core().heat() - start[0];
            String what = "Moderator " + moderator + " + Kuehler " + cooler + ": Hitze " + start[0] + " -> " + drive.core().heat()
                    + " (Durchfluss " + drive.vesselMb() + " mB/t, Kuehlung " + drive.core().cooling() + ")";
            if (drive.vesselDry()) throw new GameTestAssertException(what + ", Behaelter trocken");
            if (shouldRise && diff < 4f) throw new GameTestAssertException(what + ", sollte steigen");
            if (!shouldRise && diff > 1f) throw new GameTestAssertException(what + ", sollte nicht steigen");
        });
    }

    @GameTest(template = FLOOR, batch = "part_chain_heat", timeoutTicks = 1000)
    public static void coolerMustMatchTheModerator(GameTestHelper h) {
        BigMachineBlockEntity drive = fourRodDrive(h);
        runPhases(h, drive, 800, List.of(heatPhase(drive, 3, 0, true), heatPhase(drive, 3, 1, false),
                heatPhase(drive, 4, 1, true), heatPhase(drive, 4, 2, false)));
    }

    @GameTest(template = FLOOR, batch = "part_chain_basic", timeoutTicks = 900)
    public static void lowModeratorsNeedNoCooler(GameTestHelper h) {
        BigMachineBlockEntity drive = fourRodDrive(h);
        runPhases(h, drive, 800, List.of(heatPhase(drive, 0, 0, false), heatPhase(drive, 1, 0, false), heatPhase(drive, 2, 0, false)));
    }

    private static Phase dryRisePhase(BigMachineBlockEntity drive, int moderator, double expectedPerTick, String what) {
        float[] start = new float[1];
        return new Phase(40, () -> {
            drive.setParts(moderator, 0);
            drive.core().setHeat(100f);
            start[0] = drive.core().heat();
        }, () -> near(what, (drive.core().heat() - start[0]) / 40.0, expectedPerTick, 0.05));
    }

    @GameTest(template = FLOOR, batch = "part_chain_dry", timeoutTicks = 900)
    public static void dryVesselHeatIsDividedByTheModerator(GameTestHelper h) {
        BigMachineBlockEntity drive = fourRodDrive(h, false);
        runPhases(h, drive, 800, List.of(
                dryRisePhase(drive, 0, 1.84, "Aufheizen ohne Moderator je Tick"),
                dryRisePhase(drive, 4, 3.296, "Aufheizen mit Fluorit-Moderator je Tick")));
    }

    @GameTest(template = FLOOR, batch = "part_chain_countdown", timeoutTicks = 900)
    public static void meltdownCountdownIsTwentyFivePercentLonger(GameTestHelper h) {
        BigMachineBlockEntity drive = fourRodDrive(h, false);
        if (BigMachine.CONTROL_ROD_DRIVE.extra("meltdown_ticks") != 250) throw new GameTestAssertException("meltdown_ticks nicht 250");
        runPhases(h, drive, 800, List.of(
                new Phase(10, () -> drive.core().setHeat(1000f), () -> {
                    int left = drive.core().meltdownLeft();
                    if (left <= 200 || left > 250) throw new GameTestAssertException("Countdown " + left + " Ticks, erwartet knapp unter 250");
                    if (!drive.core().overheated()) throw new GameTestAssertException("Kern auf 100 % nicht ueberhitzt");
                })));
    }

    private static TurbineControllerBlockEntity buildTurbine(GameTestHelper h, List<BlockPos> placed) {
        MultiblockStructure t = MultiblockStructure.TURBINE;
        BlockPos ctrl = h.absolutePos(new BlockPos(4, 4, 0)).subtract(t.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : t.cells(ctrl, 0)) {
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            placed.add(c.pos());
        }
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof TurbineControllerBlockEntity be)) {
            throw new GameTestAssertException("Turbinen-Controller hat kein BlockEntity");
        }
        be.steam().fill(new FluidStack(ModFluids.STEAM.source.get(), 200000), IFluidHandler.FluidAction.EXECUTE);
        return be;
    }

    @GameTest(template = FLOOR, batch = "part_chain_turbine", timeoutTicks = 240)
    public static void translatorStagesRaiseThroughputAndYield(GameTestHelper h) {
        List<BlockPos> placed = new ArrayList<>();
        TurbineControllerBlockEntity tb = buildTurbine(h, placed);
        int[] mb = {400, 400, 600, 800, 1200};
        int[] fe = {102400, 102400, 168960, 256000, 460800};
        for (int s = 0; s <= 4; s++) {
            int stage = s;
            h.runAfterDelay(40 + 30 * s, () -> tb.setTranslator(stage));
            h.runAfterDelay(50 + 30 * s, () -> {
                try {
                    if (!tb.isFormed()) throw new GameTestAssertException("Turbine nicht aufgebaut: " + tb.matched() + "/" + tb.total());
                    if (tb.lastMb() != mb[stage] || tb.lastFe() != fe[stage]) {
                        throw new GameTestAssertException("Stufe " + stage + ": " + tb.lastMb() + " mB / " + tb.lastFe() + " FE je Tick, erwartet "
                                + mb[stage] + " / " + fe[stage]);
                    }
                } catch (GameTestAssertException e) {
                    clear(h, placed);
                    throw e;
                }
            });
        }
        h.runAfterDelay(200, () -> {
            clear(h, placed);
            h.succeed();
        });
    }

    private static void clear(GameTestHelper h, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) h.getLevel().setBlock(placed.get(i), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    @GameTest(template = FLOOR, batch = "part_chain_install", timeoutTicks = 40)
    public static void installSwapAndRemoveGiveTheItemsBack(GameTestHelper h) {
        BigMachineBlockEntity drive = buildBig(h, BigMachine.CONTROL_ROD_DRIVE, new BlockPos(2, 4, 2));
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, PartStages.stack(PartStages.MODERATORS, 3));
        if (!drive.installPart(p, InteractionHand.MAIN_HAND) || drive.moderator() != 3 || !p.getMainHandItem().isEmpty()) {
            throw new GameTestAssertException("Obsidian-Moderator nicht eingebaut (Stufe " + drive.moderator() + ")");
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, PartStages.stack(PartStages.MODERATORS, 1));
        drive.installPart(p, InteractionHand.MAIN_HAND);
        if (drive.moderator() != 1 || p.getInventory().countItem(PartStages.stack(PartStages.MODERATORS, 3).getItem()) != 1) {
            throw new GameTestAssertException("Tausch: Stufe " + drive.moderator() + ", alter Moderator nicht zurueck");
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, PartStages.stack(PartStages.COOLERS, 2));
        drive.installPart(p, InteractionHand.MAIN_HAND);
        if (drive.cooler() != 2) throw new GameTestAssertException("Kuehler nicht eingebaut");
        p.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
        if (drive.installPart(p, InteractionHand.MAIN_HAND)) throw new GameTestAssertException("Kohle wurde eingebaut");
        if (!drive.removePart(p) || drive.moderator() != 0 || drive.cooler() != 2) throw new GameTestAssertException("Ausbau: erst der Moderator");
        if (!drive.removePart(p) || drive.cooler() != 0) throw new GameTestAssertException("Ausbau: dann der Kuehler");
        if (drive.removePart(p)) throw new GameTestAssertException("Ausbau ohne Teile meldet Erfolg");
        h.succeed();
    }
}
