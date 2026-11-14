package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.door.AirlockBlockEntity;
import com.sqc.nuclearsq.door.DoorBlockEntity;
import com.sqc.nuclearsq.door.DoorPanelBlock;
import com.sqc.nuclearsq.door.DoorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AirlockTests {
    private AirlockTests() {}

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static DoorBlockEntity door(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, DoorRegistry.CONTROLLER.get());
        h.setBlock(rel.above(), DoorRegistry.HANGAR_PANEL.get().defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        if (!(h.getBlockEntity(rel) instanceof DoorBlockEntity be)) throw new GameTestAssertException("keine Torsteuerung");
        return be;
    }

    @GameTest(template = "floor", batch = "airlock", timeoutTicks = 120)
    public static void airlockInterlocksAndCycles(GameTestHelper h) {
        BlockPos mid = new BlockPos(4, 2, 4);
        DoorBlockEntity one = door(h, mid.west(2)), two = door(h, mid.east(3));
        h.setBlock(mid, DoorRegistry.AIRLOCK.get());
        if (!(h.getBlockEntity(mid) instanceof AirlockBlockEntity lock)) throw new GameTestAssertException("keine Schleusensteuerung");
        lock.link();
        check(lock.door(0) == one && lock.door(1) == two, "Tore nicht gekoppelt (naechstes zuerst)");
        check(one.setOpen(true) == null, "Tor 1 oeffnet nicht");
        check(two.setOpen(true) != null && two.closed() && two.locked(), "Tor 2 ist nicht verriegelt");
        DataSource s = DataSources.at(h.getLevel(), h.absolutePos(mid));
        check(s != null && s.type().equals("airlock_control") && s.settable().contains("cycle"), "Datenanschluss fehlt: " + s);
        h.runAfterDelay(10, () -> {
            check(lock.openDoor() == 1 && s.value("door").value() == 1, "Tor 1 steht nicht offen");
            check(s.set("cycle", 1) == null && lock.busy(), "Durchschleusen startet nicht");
            h.runAfterDelay(12, () -> {
                check(one.closed() && two.closed() && lock.busy(), "Spuelzeit fehlt: Tor 2 schon in Fahrt");
                h.runAfterDelay(AirlockBlockEntity.PURGE + 12, () -> {
                    check(one.closed() && two.open() && !two.moving() && !lock.busy(), "Tor 2 steht nicht offen");
                    check(one.setOpen(true) != null, "Tor 1 ist nicht verriegelt");
                    lock.request(-1);
                    check(!two.open(), "Schliessen wirkt nicht");
                    h.destroyBlock(mid);
                    check(!one.locked() && !two.locked(), "Tore nach Abbau der Schleuse noch verriegelt");
                    h.succeed();
                });
            });
        });
    }
}
