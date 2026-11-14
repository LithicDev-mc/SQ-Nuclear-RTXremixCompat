package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.door.DoorBlockEntity;
import com.sqc.nuclearsq.door.DoorPanelBlock;
import com.sqc.nuclearsq.door.DoorPanelBlock.Phase;
import com.sqc.nuclearsq.door.DoorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DoorTests {
    private DoorTests() {}

    private static final String FLOOR = "floor";
    private static final BlockPos CTRL = new BlockPos(2, 2, 3);

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static DoorBlockEntity door(GameTestHelper h, Block panel, int w, int ht) {
        h.setBlock(CTRL, DoorRegistry.CONTROLLER.get());
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < ht; y++) h.setBlock(CTRL.offset(1 + x, y, 0), panel.defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        }
        if (!(h.getBlockEntity(CTRL) instanceof DoorBlockEntity be)) throw new GameTestAssertException("keine Torsteuerung");
        return be;
    }

    private static void expect(GameTestHelper h, int w, int ht, Phase phase, String what) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < ht; y++) {
                BlockPos p = h.absolutePos(CTRL.offset(1 + x, y, 0));
                BlockState st = h.getLevel().getBlockState(p);
                check(st.getBlock() instanceof DoorPanelBlock && st.getValue(DoorPanelBlock.PHASE) == phase, what + ": Segment " + p + " ist " + st);
                check(st.getCollisionShape(h.getLevel(), p).isEmpty() == (phase == Phase.OPEN), what + ": Kollision bei " + p + " falsch");
            }
        }
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 100)
    public static void airlockDoorOpensAndCloses(GameTestHelper h) {
        DoorBlockEntity be = door(h, DoorRegistry.AIRLOCK_PANEL.get(), 3, 2);
        check(be.setOpen(true) == null, "Tor oeffnet nicht");
        check(be.panels().size() == 6 && be.width() == 3 && be.height() == 2 && be.kind() == DoorPanelBlock.Kind.AIRLOCK && be.way() == 3, "Tor falsch eingelesen: " + be.panels().size());
        expect(h, 3, 2, Phase.MOVING, "Fahrt auf");
        h.runAfterDelay(25, () -> {
            expect(h, 3, 2, Phase.OPEN, "offen");
            check(!be.moving() && be.open(), "Tor steht nicht offen");
            var stand = h.spawn(EntityType.ARMOR_STAND, CTRL.offset(2, 0, 0));
            check(be.setOpen(false) != null && be.open(), "Tor schliesst trotz Hindernis");
            stand.discard();
            check(be.setOpen(false) == null, "Tor schliesst nicht");
            expect(h, 3, 2, Phase.MOVING, "Fahrt zu");
            h.runAfterDelay(25, () -> {
                expect(h, 3, 2, Phase.CLOSED, "zu");
                check(be.closed(), "Tor steht nicht geschlossen");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 60)
    public static void hangarDoorByDataAndRelease(GameTestHelper h) {
        DoorBlockEntity be = door(h, DoorRegistry.HANGAR_PANEL.get(), 2, 2);
        h.setBlock(CTRL.offset(3, 0, 0), DoorRegistry.AIRLOCK_PANEL.get().defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        DataSource s = DataSources.at(h.getLevel(), h.absolutePos(CTRL));
        check(s != null && s.type().equals("door_control") && s.settable().contains("open"), "Datenanschluss fehlt: " + s);
        check(s.set("open", 1) == null && be.panels().size() == 4 && be.kind() == DoorPanelBlock.Kind.HANGAR && be.way() == 2, "Datennetz oeffnet nicht");
        h.runAfterDelay(15, () -> {
            expect(h, 2, 2, Phase.OPEN, "offen");
            check(s.value("open").value() == 1, "Wert open falsch");
            check(h.getBlockState(CTRL.offset(3, 0, 0)).getValue(DoorPanelBlock.PHASE) == Phase.RAW, "fremdes Segment faehrt mit");
            h.destroyBlock(CTRL);
            expect(h, 2, 2, Phase.RAW, "nach Abbau");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 20)
    public static void noPanelsAndRedstone(GameTestHelper h) {
        h.setBlock(CTRL, DoorRegistry.CONTROLLER.get());
        if (!(h.getBlockEntity(CTRL) instanceof DoorBlockEntity be)) throw new GameTestAssertException("keine Torsteuerung");
        check(be.setOpen(true) != null && be.closed(), "Tor ohne Segmente oeffnet");
        door(h, DoorRegistry.HANGAR_PANEL.get(), 1, 1);
        if (!(h.getBlockEntity(CTRL) instanceof DoorBlockEntity be2)) throw new GameTestAssertException("keine Torsteuerung");
        h.setBlock(CTRL.west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        check(be2.open() && be2.moving(), "Redstone oeffnet nicht");
        h.setBlock(CTRL.west(), net.minecraft.world.level.block.Blocks.AIR);
        check(!be2.open(), "Redstone schliesst nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 60)
    public static void rectangleIsAdopted(GameTestHelper h) {
        DoorBlockEntity be = door(h, DoorRegistry.AIRLOCK_PANEL.get(), 2, 3);
        h.runAfterDelay(25, () -> {
            expect(h, 2, 3, Phase.CLOSED, "uebernommen");
            check(be.panels().size() == 6 && be.kind() == DoorPanelBlock.Kind.AIRLOCK && be.way() == 2, "Schleusentuer falsch eingelesen");
            h.setBlock(CTRL.offset(2, 2, 0), net.minecraft.world.level.block.Blocks.AIR);
            check(be.setOpen(true) != null && be.closed() && be.panels().isEmpty(), "Tor mit Luecke oeffnet");
            check(h.getBlockState(CTRL.offset(1, 0, 0)).getValue(DoorPanelBlock.PHASE) == Phase.RAW, "Segmente nach Luecke nicht wieder roh");
            h.succeed();
        });
    }

    private static void redstoneDrives(GameTestHelper h, Block panel) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) h.setBlock(CTRL.offset(1 + x, y, 0), panel.defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        }
        BlockPos low = h.absolutePos(CTRL.offset(1, 0, 0)), side = CTRL.offset(3, 0, 0);
        check(h.getLevel().getBlockState(low).canRedstoneConnectTo(h.getLevel(), low, Direction.EAST), "Redstone-Leitung verbindet sich nicht mit dem Segment");
        h.setBlock(side, Blocks.REDSTONE_BLOCK);
        DoorBlockEntity be = DoorBlockEntity.controllerOf(h.getLevel(), low);
        check(be != null && be.open(), "Redstone-Signal oeffnet nicht");
        h.runAfterDelay(60, () -> {
            expect(h, 2, 2, Phase.OPEN, "offen per Redstone");
            h.setBlock(side, Blocks.AIR);
            check(!be.open(), "ohne Redstone-Signal schliesst es nicht");
            h.runAfterDelay(60, () -> {
                expect(h, 2, 2, Phase.CLOSED, "zu ohne Redstone");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneHangar(GameTestHelper h) { redstoneDrives(h, DoorRegistry.HANGAR_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneSectional(GameTestHelper h) { redstoneDrives(h, DoorRegistry.SECTIONAL_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneGrille(GameTestHelper h) { redstoneDrives(h, DoorRegistry.GRILLE_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneFolding(GameTestHelper h) { redstoneDrives(h, DoorRegistry.FOLDING_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneAirlock(GameTestHelper h) { redstoneDrives(h, DoorRegistry.AIRLOCK_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 200)
    public static void redstoneVault(GameTestHelper h) { redstoneDrives(h, DoorRegistry.VAULT_PANEL.get()); }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 40)
    public static void doorBuiltWithSneakLeftClick(GameTestHelper h) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) h.setBlock(CTRL.offset(1 + x, y, 0), DoorRegistry.HANGAR_PANEL.get().defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        }
        BlockPos low = h.absolutePos(CTRL.offset(1, 0, 0));
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(low), Direction.NORTH, low, false);
        h.getLevel().getBlockState(low).useWithoutItem(h.getLevel(), p, hit);
        check(DoorBlockEntity.controllerOf(h.getLevel(), low) == null, "Rechtsklick baut das Tor auf");
        expect(h, 2, 2, Phase.RAW, "nach Rechtsklick");
        p.setShiftKeyDown(true);
        PlayerInteractEvent.LeftClickBlock e = new PlayerInteractEvent.LeftClickBlock(p, low, Direction.NORTH, PlayerInteractEvent.LeftClickBlock.Action.START);
        DoorPanelBlock.leftClick(e);
        check(e.isCanceled(), "Umschalt + Linksklick baut das Segment ab");
        expect(h, 2, 2, Phase.CLOSED, "nach Umschalt + Linksklick");
        p.setShiftKeyDown(false);
        check(h.getLevel().getBlockState(low).useWithoutItem(h.getLevel(), p, hit) != InteractionResult.PASS, "Rechtsklick nicht angenommen");
        DoorBlockEntity be = DoorBlockEntity.controllerOf(h.getLevel(), low);
        check(be != null && be.open() && be.panels().size() == 4, "Rechtsklick faehrt das aufgebaute Tor nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 140)
    public static void doorWithoutController(GameTestHelper h) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) h.setBlock(CTRL.offset(1 + x, y, 0), DoorRegistry.HANGAR_PANEL.get().defaultBlockState().setValue(DoorPanelBlock.AXIS, Direction.Axis.Z));
        }
        BlockPos low = h.absolutePos(CTRL.offset(1, 0, 0)), top = low.above(), side = CTRL.offset(3, 0, 0);
        check(DoorBlockEntity.controllerOf(h.getLevel(), low) == null, "Tor hat schon eine Steuerung");
        DoorBlockEntity be = DoorBlockEntity.of(h.getLevel(), low);
        check(be != null && be.master() && be.toggle() == null && be.panels().size() == 4, "Segment uebernimmt die Steuerung nicht");
        check(DoorBlockEntity.of(h.getLevel(), top) == be, "zweites Segment findet das Leitsegment nicht");
        expect(h, 2, 2, Phase.MOVING, "Fahrt auf");
        h.runAfterDelay(15, () -> {
            expect(h, 2, 2, Phase.OPEN, "offen");
            check(!h.getLevel().getBlockState(top).getShape(h.getLevel(), top).isEmpty() && h.getLevel().getBlockState(low).getShape(h.getLevel(), low).isEmpty(),
                    "offen ist nicht genau der Rollkasten anklickbar");
            check(be.toggle() == null, "Tor schliesst nicht");
            h.runAfterDelay(15, () -> {
                expect(h, 2, 2, Phase.CLOSED, "zu");
                h.setBlock(side, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
                check(be.open() && be.moving(), "Redstone am Segment oeffnet nicht");
                h.setBlock(side, net.minecraft.world.level.block.Blocks.AIR);
                check(!be.open(), "Redstone am Segment schliesst nicht");
                h.runAfterDelay(15, () -> {
                    h.setBlock(CTRL, DoorRegistry.CONTROLLER.get());
                    h.runAfterDelay(45, () -> {
                        check(h.getBlockEntity(CTRL) instanceof DoorBlockEntity c && !c.master() && c.panels().size() == 4, "Steuerblock uebernimmt das Tor nicht");
                        check(!h.getLevel().getBlockState(low).getValue(DoorPanelBlock.MASTER) && h.getLevel().getBlockEntity(low) == null, "Leitsegment bleibt neben dem Steuerblock");
                        expect(h, 2, 2, Phase.CLOSED, "vom Steuerblock uebernommen");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "doors", timeoutTicks = 20)
    public static void variantsKnowTheirWay(GameTestHelper h) {
        Block[] panels = {DoorRegistry.SECTIONAL_PANEL.get(), DoorRegistry.GRILLE_PANEL.get(), DoorRegistry.FOLDING_PANEL.get()};
        int[] way = {2, 2, 3};
        for (int k = 0; k < panels.length; k++) {
            DoorBlockEntity be = door(h, panels[k], 3, 2);
            check(be.setOpen(true) == null && be.panels().size() == 6 && be.way() == way[k] && be.kind().up() == (k < 2), "Bauart " + k + " falsch eingelesen");
            h.destroyBlock(CTRL);
        }
        h.succeed();
    }
}
