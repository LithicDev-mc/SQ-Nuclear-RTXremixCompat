package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.GeigerCounterItem;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class MeterTests {
    private MeterTests() {}

    private static final String FLOOR = "floor";

    private static ServerPlayer player(GameTestHelper h, String id, String name) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.fromString(id), name));
        sp.getInventory().clearContent();
        if (DosimeterItem.isPinned(sp)) DosimeterItem.togglePin(sp);
        return sp;
    }

    @GameTest(template = FLOOR, batch = "meter_geiger", timeoutTicks = 20)
    public static void geigerClicksRiseWithRate(GameTestHelper h) {
        double[] rates = {RadiationManager.AMBIENT_SV_H, 1e-5, 1e-3, 1.0};
        double prev = -1;
        for (double r : rates) {
            double cps = GeigerCounterItem.clicksPerSecond(r);
            if (cps <= prev) throw new GameTestAssertException("Knackrate steigt nicht bei " + r + " Sv/h: " + cps + " <= " + prev);
            if (cps >= 45) throw new GameTestAssertException("Knackrate ueber der Totzeit-Grenze bei " + r + " Sv/h: " + cps);
            prev = cps;
        }
        double base = GeigerCounterItem.clicksPerSecond(RadiationManager.AMBIENT_SV_H);
        if (base < 0.3 || base > 1) throw new GameTestAssertException("Knackrate bei Grundstrahlung: " + base);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "meter_pin", timeoutTicks = 20)
    public static void dosimeterPinToggle(GameTestHelper h) {
        ServerPlayer sp = player(h, "7a1c2e3f-4b5d-4e6f-8a90-1b2c3d4e5f61", "nsq_meter_a");
        sp.getInventory().setItem(0, new ItemStack(ProtectionRegistry.DOSIMETER.get()));
        if (!DosimeterItem.togglePin(sp) || !DosimeterItem.isPinned(sp)) throw new GameTestAssertException("Anzeige nicht fixiert");
        if (DosimeterItem.togglePin(sp) || DosimeterItem.isPinned(sp)) throw new GameTestAssertException("Fixierung nicht geloest");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "meter_pin_end", timeoutTicks = 20)
    public static void dosimeterPinEndsWhenRemoved(GameTestHelper h) {
        ServerPlayer sp = player(h, "7a1c2e3f-4b5d-4e6f-8a90-1b2c3d4e5f62", "nsq_meter_b");
        var inv = sp.getInventory();
        inv.setItem(0, new ItemStack(ProtectionRegistry.DOSIMETER.get()));
        DosimeterItem.togglePin(sp);
        inv.setItem(5, inv.removeItemNoUpdate(0));
        DosimeterItem.checkPin(sp);
        if (!DosimeterItem.isPinned(sp)) throw new GameTestAssertException("Fixierung geloest, obwohl das Dosimeter in der Hotbar liegt");
        inv.setItem(20, inv.removeItemNoUpdate(5));
        DosimeterItem.checkPin(sp);
        if (DosimeterItem.isPinned(sp)) throw new GameTestAssertException("Fixierung bleibt ohne Dosimeter in Hotbar/Zweithand");
        h.succeed();
    }
}
