package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.robotics.RodGripperBlockEntity;
import com.sqc.nuclearsq.robotics.RodGripperBlockEntity.Input;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RodGripperTests {
    private RodGripperTests() {}

    private static final String FLOOR = "floor";
    private static final int X = 5, Z = 5;

    private static RodGripperBlockEntity setup(GameTestHelper h, int gy, int headY, boolean powered) {
        for (int y = 4; y <= 16; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(new BlockPos(X + dx, y, Z + dz), Blocks.AIR);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.setBlock(new BlockPos(X, headY - i, Z), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        h.setBlock(new BlockPos(X, headY, Z), ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        h.setBlock(new BlockPos(X, gy, Z), RoboticsRegistry.ROD_GRIPPER.get());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(X, gy, Z))) instanceof RodGripperBlockEntity be)) throw new GameTestAssertException("Rod-Greifer hat keine BlockEntity");
        if (powered) for (int i = 0; i < 5; i++) be.energy().receiveEnergy(RodGripperBlockEntity.BUFFER, false);
        return be;
    }

    private static void signal(GameTestHelper h, int gy, Input in, boolean on) {
        h.setBlock(new BlockPos(X, gy, Z).relative(in.side(Direction.NORTH)), on ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
    }

    private static boolean rodAt(GameTestHelper h, int headY) {
        return RodGripperBlockEntity.rodAt(h.getLevel(), h.absolutePos(new BlockPos(X, headY, Z)));
    }

    private static boolean air(GameTestHelper h, int y) {
        return h.getLevel().getBlockState(h.absolutePos(new BlockPos(X, y, Z))).isAir();
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 120)
    public static void pullTakesRodUp(GameTestHelper h) {
        RodGripperBlockEntity be = setup(h, 13, 10, true);
        ((FuelRodBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(X, 6, Z)))).setRemaining(2000);
        signal(h, 13, Input.PULL, true);
        h.succeedWhen(() -> {
            if (be.depth() != 0 || !be.holding() || !rodAt(h, 12)) throw new GameTestAssertException("Stab noch nicht oben: Tiefe " + be.depth() + ", " + be.problem());
            if (!air(h, 7) || !air(h, 6)) throw new GameTestAssertException("Alter Platz des Stabs nicht frei");
            if (((FuelRodBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(X, 8, Z)))).getRemaining() != 2000) throw new GameTestAssertException("Ladung verloren");
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 160)
    public static void gripLiftLowerRelease(GameTestHelper h) {
        RodGripperBlockEntity be = setup(h, 11, 9, true);
        int[] phase = {0};
        signal(h, 11, Input.LOWER, true);
        h.onEachTick(() -> {
            switch (phase[0]) {
                case 0 -> {
                    if (be.depth() != 1) return;
                    signal(h, 11, Input.LOWER, false);
                    signal(h, 11, Input.GRIP, true);
                    phase[0] = 1;
                }
                case 1 -> {
                    if (!be.holding()) return;
                    signal(h, 11, Input.LIFT, true);
                    phase[0] = 2;
                }
                case 2 -> {
                    if (be.depth() != 0 || !rodAt(h, 10) || !air(h, 5)) return;
                    signal(h, 11, Input.LIFT, false);
                    signal(h, 11, Input.LOWER, true);
                    phase[0] = 3;
                }
                case 3 -> {
                    if (be.depth() != 1 || !rodAt(h, 9)) return;
                    signal(h, 11, Input.LOWER, false);
                    signal(h, 11, Input.GRIP, false);
                    phase[0] = 4;
                }
                case 4 -> {
                    if (be.holding()) return;
                    if (!rodAt(h, 9)) throw new GameTestAssertException("Stab nach dem Loslassen nicht mehr da");
                    h.succeed();
                }
                default -> {}
            }
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 60)
    public static void doesNothingWithoutPowerOrWhenOff(GameTestHelper h) {
        RodGripperBlockEntity be = setup(h, 13, 10, false);
        signal(h, 13, Input.LOWER, true);
        h.runAfterDelay(20, () -> {
            if (be.depth() != 0 || !be.problem().equals("power")) throw new GameTestAssertException("Ohne Strom bewegt: Tiefe " + be.depth() + ", " + be.problem());
            be.energy().receiveEnergy(RodGripperBlockEntity.BUFFER, false);
            be.setEnabled(false);
        });
        h.runAfterDelay(40, () -> {
            if (be.depth() != 0) throw new GameTestAssertException("Ausgeschaltet bewegt");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 80)
    public static void blockedWhenCellBelowIsTaken(GameTestHelper h) {
        RodGripperBlockEntity be = setup(h, 11, 9, true);
        h.setBlock(new BlockPos(X, 4, Z), Blocks.STONE);
        int[] phase = {0};
        signal(h, 11, Input.LOWER, true);
        h.onEachTick(() -> {
            switch (phase[0]) {
                case 0 -> {
                    if (be.depth() != 1) return;
                    signal(h, 11, Input.GRIP, true);
                    phase[0] = 1;
                }
                case 1 -> {
                    if (!be.holding()) return;
                    phase[0] = 2;
                }
                case 2 -> {
                    if (!be.problem().equals("blocked")) return;
                    if (be.depth() != 1 || !rodAt(h, 9)) throw new GameTestAssertException("Stab trotz belegter Zelle bewegt");
                    h.succeed();
                }
                default -> {}
            }
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 200)
    public static void clampedRodIsNotTouched(GameTestHelper h) {
        BigMachine m = BigMachine.SPENT_FUEL_RACK;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 1, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Gestell ohne Controller");
        BlockPos head = be.rodBase(be.shafts()[4]).above(10);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        h.succeedWhen(() -> {
            if (!be.shafts()[4].locked()) throw new GameTestAssertException("warte auf die Sperre");
            if (!BigMachineBlockEntity.rodClamped(h.getLevel(), head)) throw new GameTestAssertException("Gesperrter Stab gilt nicht als eingeklemmt");
            be.setRackReleased(4, true);
            if (BigMachineBlockEntity.rodClamped(h.getLevel(), head)) throw new GameTestAssertException("Freigegebener Stab gilt noch als eingeklemmt");
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 400)
    public static void driveClampedRodIsNotTouched(GameTestHelper h) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos ctrl = h.absolutePos(new BlockPos(4, 60, 4)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity drive)) throw new GameTestAssertException("Antrieb ohne Controller");
        BlockPos base = drive.rodBase(drive.shafts()[0]), head = base.above(8), gripper = base.above(12);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        h.getLevel().setBlockAndUpdate(gripper, RoboticsRegistry.ROD_GRIPPER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(gripper.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        if (!(h.getLevel().getBlockEntity(gripper) instanceof RodGripperBlockEntity be)) throw new GameTestAssertException("Rod-Greifer hat keine BlockEntity");
        for (int i = 0; i < 5; i++) be.energy().receiveEnergy(RodGripperBlockEntity.BUFFER, false);
        int[] phase = {0};
        h.onEachTick(() -> {
            if (phase[0] == 0) {
                if (drive.shafts()[0].headY() != 8) return;
                drive.setShaftLocked(0, true);
                phase[0] = 1;
            } else if (phase[0] == 1) {
                if (be.depth() != 3 || !be.problem().equals("clamped")) return;
                if (be.holding() || !RodGripperBlockEntity.rodAt(h.getLevel(), head)) throw new GameTestAssertException("Gesperrter Stab wurde angefasst");
                drive.setShaftLocked(0, false);
                phase[0] = 2;
            } else if (be.holding()) {
                h.succeed();
            }
        });
    }

    @GameTest(template = FLOOR, batch = "rod_gripper", timeoutTicks = 20)
    public static void dataPortShowsValues(GameTestHelper h) {
        RodGripperBlockEntity be = setup(h, 13, 10, true);
        BlockPos pos = h.absolutePos(new BlockPos(X, 13, Z));
        if (!DataSources.isPort(h.getLevel(), pos)) throw new GameTestAssertException("Kein Datenanschluss");
        DataSource s = DataSources.at(h.getLevel(), pos);
        if (s == null || s.value("hook") == null || s.value("grip") == null || s.value("energy") == null || !s.settable().contains("enable")) {
            throw new GameTestAssertException("Datenwerte fehlen: " + s);
        }
        s.set("enable", 0);
        if (be.enabled()) throw new GameTestAssertException("enable 0 schaltet nicht aus");
        h.succeed();
    }
}
