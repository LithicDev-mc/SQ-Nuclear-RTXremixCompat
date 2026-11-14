package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.ContainmentDoor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentDoorTests {
    private ContainmentDoorTests() {}

    private static final String FLOOR = "floor";

    private static MachineKits.Placement hull(GameTestHelper h, int y) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.CONTAINMENT.id), h.absolutePos(new BlockPos(34, y, 4)), Direction.SOUTH);
        for (BlockPos p : pl.footprint()) h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(blocked.size() + " Felder der Huelle belegt, z. B. " + blocked.get(0));
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    private static List<BlockPos> doorCells(MachineKits.Placement pl) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = ContainmentDoor.X0; x <= ContainmentDoor.X1; x++) {
            for (int y = 0; y < ContainmentDoor.HEIGHT; y++) {
                for (int z = 0; z < ContainmentDoor.DEPTH; z++) out.add(BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), x, y, z));
            }
        }
        return out;
    }

    private static BlockPos outside(MachineKits.Placement pl) {
        return BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), BigMachine.CONTAINMENT.ax, 0, -1);
    }

    private static BlockPos doorFront(MachineKits.Placement pl) {
        return BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), ContainmentDoor.X1, 0, -1);
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static void expectDoor(GameTestHelper h, List<BlockPos> cells, boolean solid, String what) {
        for (BlockPos p : cells) {
            BlockState st = h.getLevel().getBlockState(p);
            check(st.is(BigMachine.CONTAINMENT.shellBlock()), what + ": Tuerfeld " + p + " ist kein Hilfsblock, sondern " + st.getBlock());
            boolean hasCollision = !st.getCollisionShape(h.getLevel(), p).isEmpty();
            check(hasCollision == solid, what + ": Tuerfeld " + p + (solid ? " hat keine Kollision" : " hat Kollision"));
            check(!st.getShape(h.getLevel(), p).isEmpty(), what + ": Tuerfeld " + p + " ist nicht anklickbar");
        }
    }

    @GameTest(template = FLOOR, batch = "containment_door", timeoutTicks = 800)
    public static void bunkerDoorOpensAndCloses(GameTestHelper h) {
        MachineKits.Placement pl = hull(h, 140);
        int[] step = new int[1];
        Pig[] pig = new Pig[1];
        h.succeedWhen(() -> {
            for (BlockPos p : pl.footprint()) h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
            check(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be0 && be0.isFormed(), "Huelle nicht aufgebaut");
            BigMachineBlockEntity be = (BigMachineBlockEntity) h.getLevel().getBlockEntity(pl.anchor());
            List<BlockPos> cells = doorCells(pl);
            switch (step[0]) {
                case 0 -> {
                    expectDoor(h, cells, true, "zu");
                    check(!be.doorOpen(), "Tuer beim Aufbau schon offen");
                    for (BlockPos p : cells) check(ContainmentDoor.controllerOf(h.getLevel(), p) == be, "Controller der Tuer bei " + p + " nicht gefunden");
                    check(be.isDoorPos(cells.get(0)) && !be.isDoorPos(pl.anchor()), "isDoorPos stimmt nicht");
                    check(be.setDoor(true), "Tuer laesst sich nicht oeffnen");
                    step[0] = 1;
                    throw new GameTestAssertException("warte: Tuer geoeffnet");
                }
                case 1 -> {
                    expectDoor(h, cells, false, "offen");
                    check(be.doorOpen(), "doorOpen nach dem Oeffnen nicht gesetzt");
                    pig[0] = EntityType.PIG.spawn(h.getLevel(), cells.get(0), MobSpawnType.COMMAND);
                    check(pig[0] != null, "Testschwein nicht erzeugt");
                    step[0] = 2;
                    throw new GameTestAssertException("warte: Schwein im Durchgang");
                }
                case 2 -> {
                    check(!be.setDoor(false), "Tuer schliesst, obwohl jemand im Durchgang steht");
                    expectDoor(h, cells, false, "blockiert");
                    pig[0].discard();
                    step[0] = 3;
                    throw new GameTestAssertException("warte: Schwein entfernt");
                }
                case 3 -> {
                    check(be.setDoor(false), "Tuer laesst sich nicht schliessen");
                    expectDoor(h, cells, true, "wieder zu");
                    h.getLevel().setBlockAndUpdate(outside(pl), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    step[0] = 4;
                    throw new GameTestAssertException("warte: Redstone-Block gesetzt");
                }
                case 4 -> {
                    expectDoor(h, cells, false, "Redstone an");
                    h.getLevel().setBlockAndUpdate(outside(pl), Blocks.AIR.defaultBlockState());
                    step[0] = 5;
                    throw new GameTestAssertException("warte: Redstone-Block entfernt");
                }
                case 5 -> {
                    expectDoor(h, cells, true, "Redstone aus");
                    check(h.getLevel().getBlockState(cells.get(0)).canRedstoneConnectTo(h.getLevel(), cells.get(0), Direction.NORTH), "Redstone-Leitung verbindet sich nicht mit der Tuer");
                    h.getLevel().setBlockAndUpdate(doorFront(pl), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    step[0] = 6;
                    throw new GameTestAssertException("warte: Redstone-Block vor der Tuer gesetzt");
                }
                case 6 -> {
                    expectDoor(h, cells, false, "Redstone an der Tuer an");
                    h.getLevel().setBlockAndUpdate(doorFront(pl), Blocks.AIR.defaultBlockState());
                    step[0] = 7;
                    throw new GameTestAssertException("warte: Redstone-Block vor der Tuer entfernt");
                }
                case 7 -> expectDoor(h, cells, true, "Redstone an der Tuer aus");
                default -> { }
            }
        });
    }
}
