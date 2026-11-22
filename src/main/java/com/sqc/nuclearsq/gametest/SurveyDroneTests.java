package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.robotics.drone.DroneRegistry;
import com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity;
import com.sqc.nuclearsq.robotics.drone.SurveyMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SurveyDroneTests {
    private SurveyDroneTests() {}

    @GameTest(template = "floor", batch = "survey_drone", timeoutTicks = 600)
    public static void surveysZoneAndLandsAtStart(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        SurveyDroneEntity d = DroneRegistry.DRONE.get().create(lv);
        if (d == null) throw new GameTestAssertException("Drohne nicht erzeugbar");
        Vec3 home = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 4, 2)));
        d.moveTo(home.x, home.y, home.z, 0f, 0f);
        lv.addFreshEntity(d);
        Contamination.add(lv, h.absolutePos(new BlockPos(9, 4, 9)), 0.01f);
        String r = d.start(java.util.List.of(h.absolutePos(new BlockPos(5, 4, 5)), h.absolutePos(new BlockPos(10, 4, 10))));
        if (!r.equals("started") || !d.busy()) throw new GameTestAssertException("Flug startete nicht: " + r);
        h.runAfterDelay(20, () -> h.succeedWhen(() -> {
            if (d.busy()) throw new GameTestAssertException("Drohne fliegt noch");
            if (d.position().distanceTo(home) > 0.1) throw new GameTestAssertException("Nicht am Startplatz: " + d.position().distanceTo(home));
            SurveyMap m = d.map();
            if (m == null || m.w() != 3 || m.h() != 3) throw new GameTestAssertException("Karte falsch");
            for (byte b : m.cells()) if (b == 0) throw new GameTestAssertException("Zelle nicht gemessen");
            if (m.at(2, 2) <= m.at(0, 0)) throw new GameTestAssertException("Quelle nicht erkannt: " + m.at(2, 2) + " / " + m.at(0, 0));
            d.discard();
        }));
    }

    @GameTest(template = "floor", batch = "survey_drone", timeoutTicks = 20)
    public static void shiftClickWithTabletStartsEmptyHandsPickUp(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        SurveyDroneEntity d = DroneRegistry.DRONE.get().create(lv);
        if (d == null) throw new GameTestAssertException("Drohne nicht erzeugbar");
        Vec3 home = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 4, 2)));
        d.moveTo(home.x, home.y, home.z, 0f, 0f);
        lv.addFreshEntity(d);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setShiftKeyDown(true);
        ItemStack tab = new ItemStack(DroneRegistry.TABLET.get());
        tab.set(DroneRegistry.ZONE.get(), java.util.List.of(h.absolutePos(new BlockPos(3, 4, 3)), h.absolutePos(new BlockPos(5, 4, 5))));
        p.setItemInHand(InteractionHand.OFF_HAND, tab);
        p.interactOn(d, InteractionHand.MAIN_HAND);
        if (d.isRemoved()) throw new GameTestAssertException("Drohne aufgehoben, obwohl das Tablet in der Nebenhand ist");
        p.interactOn(d, InteractionHand.OFF_HAND);
        if (!d.busy()) throw new GameTestAssertException("Shift + Rechtsklick mit Tablet auf die Drohne startet sie nicht");
        d.recall();
        d.discard();
        SurveyDroneEntity e = DroneRegistry.DRONE.get().create(lv);
        e.moveTo(home.x, home.y, home.z, 0f, 0f);
        lv.addFreshEntity(e);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        p.interactOn(e, InteractionHand.MAIN_HAND);
        if (!e.isRemoved()) throw new GameTestAssertException("Beide Haende leer: Drohne nicht aufgehoben");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "survey_drone", timeoutTicks = 400)
    public static void manualFlightMeasuresAndFillsTablet(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        SurveyDroneEntity d = DroneRegistry.DRONE.get().create(lv);
        if (d == null) throw new GameTestAssertException("Drohne nicht erzeugbar");
        Vec3 home = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 4, 2)));
        d.moveTo(home.x, home.y, home.z, 0f, 0f);
        lv.addFreshEntity(d);
        Contamination.add(lv, h.absolutePos(new BlockPos(2, 4, 4)), 0.01f);
        var sp = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(lv);
        sp.setPos(home.x, home.y, home.z + 3);
        sp.getInventory().clearContent();
        ItemStack tab = new ItemStack(DroneRegistry.TABLET.get());
        tab.set(DroneRegistry.BOUND.get(), d.getUUID());
        sp.setItemInHand(InteractionHand.MAIN_HAND, tab);
        d.startControl(sp, tab);
        if (!d.controlled() || !d.busy() || !d.flying()) throw new GameTestAssertException("Steuerung startete nicht");
        SurveyMap m = d.map();
        if (m == null || m.w() != SurveyDroneEntity.ZONE_MAX / SurveyMap.CELL) throw new GameTestAssertException("Karte ohne Zone nicht 64 x 64");
        d.input(0, 0, 1, 0f, 0f);
        for (int i = 0; i < 8; i++) d.step(lv, false, true);
        if (d.getY() <= home.y + 1) throw new GameTestAssertException("Drohne steigt nicht: " + (d.getY() - home.y));
        d.input(1, 0, 0, 0f, 0f);
        for (int i = 0; i < 6; i++) d.step(lv, true, true);
        if (d.getZ() <= home.z + 1.5) throw new GameTestAssertException("Drohne fliegt nicht vorwaerts: " + (d.getZ() - home.z));
        int measured = 0, top = 0;
        for (byte b : m.cells()) if (b != 0) {
            measured++;
            top = Math.max(top, b & 255);
        }
        if (measured == 0) throw new GameTestAssertException("Nichts gemessen");
        if (top <= SurveyMap.encode(com.sqc.nuclearsq.radiation.RadiationManager.AMBIENT_SV_H)) throw new GameTestAssertException("Quelle nicht in der Karte: " + top);
        d.stopControl("");
        if (d.controlled()) throw new GameTestAssertException("Steuerung nicht beendet");
        SurveyMap got = sp.getMainHandItem().get(DroneRegistry.MAP.get());
        if (got == null || !java.util.Arrays.equals(got.cells(), m.cells())) throw new GameTestAssertException("Karte nicht im Tablet");
        h.runAfterDelay(5, () -> h.succeedWhen(() -> {
            if (d.busy()) throw new GameTestAssertException("Drohne fliegt nach der Steuerung nicht heim");
            if (d.position().distanceTo(home) > 0.1) throw new GameTestAssertException("Nicht am Startplatz");
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            d.discard();
        }));
    }

    @GameTest(template = "floor", batch = "survey_drone", timeoutTicks = 20)
    public static void manualFlightNeedsCharge(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        SurveyDroneEntity d = DroneRegistry.DRONE.get().create(lv);
        Vec3 home = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 4, 2)));
        d.moveTo(home.x, home.y, home.z, 0f, 0f);
        lv.addFreshEntity(d);
        d.setEnergy(SurveyDroneEntity.RESERVE);
        var sp = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(lv);
        sp.setPos(home.x, home.y, home.z + 3);
        d.startControl(sp, new ItemStack(DroneRegistry.TABLET.get()));
        if (d.controlled() || d.busy()) throw new GameTestAssertException("Steuerung trotz leerem Akku");
        d.discard();
        h.succeed();
    }

    @GameTest(template = "floor", batch = "survey_drone", timeoutTicks = 20)
    public static void droneAndTabletHaveRecipes(GameTestHelper h) {
        for (String id : new String[] {"survey_drone_sqc", "survey_tablet_sqc"}) {
            if (h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "equipment_assembly/" + id)).isEmpty())
                throw new GameTestAssertException("Rezept fehlt: " + id);
        }
        h.succeed();
    }
}
