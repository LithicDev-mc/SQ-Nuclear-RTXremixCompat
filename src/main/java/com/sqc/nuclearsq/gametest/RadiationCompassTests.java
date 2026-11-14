package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.GeigerPayload;
import com.sqc.nuclearsq.radiation.GeigerCounterItem;
import com.sqc.nuclearsq.radiation.RadiationCompass;
import com.sqc.nuclearsq.radiation.RadiationManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationCompassTests {
    private RadiationCompassTests() {}

    private static final String FLOOR = "floor";

    private static void near(String what, double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-4) throw new GameTestAssertException(what + ": " + actual + " statt " + expected);
    }

    @GameTest(template = FLOOR, batch = "compass_bearing", timeoutTicks = 20)
    public static void bearingTurnsFollowTheView(GameTestHelper h) {
        near("Sueden bei Blick nach Sueden", RadiationCompass.bearingTurns(0, 0, 5), 0.0);
        near("Westen ist rechts bei Blick nach Sueden", RadiationCompass.bearingTurns(0, -5, 0), 0.25);
        near("Norden ist hinten bei Blick nach Sueden", RadiationCompass.bearingTurns(0, 0, -5), 0.5);
        near("Osten ist links bei Blick nach Sueden", RadiationCompass.bearingTurns(0, 5, 0), 0.75);
        near("Blick nach Westen, Quelle im Westen", RadiationCompass.bearingTurns(90, -5, 0), 0.0);
        near("Blick nach Osten, Quelle im Sueden", RadiationCompass.bearingTurns(-90, 0, 5), 0.25);
        near("Blick nach Norden, Quelle im Norden", RadiationCompass.bearingTurns(180, 0, -5), 0.0);
        near("Blick nach Norden, Quelle im Osten", RadiationCompass.bearingTurns(180, 5, 0), 0.25);
        for (int yaw = -540; yaw <= 540; yaw += 37) {
            float t = RadiationCompass.bearingTurns(yaw, 3, -4);
            if (t < 0 || t >= 1) throw new GameTestAssertException("Drehung ausserhalb [0,1): " + t + " bei yaw " + yaw);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compass_best", timeoutTicks = 20)
    public static void strongestSourceWinsAndRangeIsLimited(GameTestHelper h) {
        Vec3 from = new Vec3(0, 0, 0);
        var b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(10, 0, 0, 1.0);
        b.offer(0, 0, 5, 1.0);
        var t = b.result();
        if (t == null || t.pos().z != 5) throw new GameTestAssertException("Naehere Quelle nicht gewaehlt: " + t);
        b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(2, 0, 0, 1e-4);
        b.offer(0, 0, 40, 10.0);
        t = b.result();
        if (t == null || t.pos().z != 40) throw new GameTestAssertException("Starke Quelle nicht gewaehlt: " + t);
        near("Staerke am Spieler", t.apparentSvH(), 10.0 / 1600.0);
        near("Abstand", t.distance(), 40.0);
        b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(RadiationCompass.RANGE + 1, 0, 0, 1e9);
        if (b.result() != null) throw new GameTestAssertException("Quelle ausserhalb der Reichweite gefunden");
        b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(0, 0, 30, RadiationCompass.MIN_SV_H * 900 * 0.9);
        if (b.result() != null) throw new GameTestAssertException("Zu schwache Quelle gefunden");
        b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(0, 0, 30, RadiationCompass.MIN_SV_H * 900 * 1.1);
        if (b.result() == null) throw new GameTestAssertException("Quelle ueber der Schwelle nicht gefunden");
        b = new RadiationCompass.Best(from, RadiationCompass.RANGE);
        b.offer(0, 0, 0, 2.0);
        t = b.result();
        if (t == null || !(t.apparentSvH() <= 2.0) || !t.here(from)) throw new GameTestAssertException("Quelle am Spieler falsch: " + t);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compass_level", timeoutTicks = 20)
    public static void levelFollowsGeigerThresholds(GameTestHelper h) {
        if (RadiationCompass.level(1e-7) != 0) throw new GameTestAssertException("1e-7 sollte gelb sein");
        if (RadiationCompass.level(GeigerCounterItem.ELEVATED_SV_H) != 1) throw new GameTestAssertException("erhoeht sollte orange sein");
        if (RadiationCompass.level(GeigerCounterItem.DANGER_SV_H) != 2) throw new GameTestAssertException("gefaehrlich sollte rot sein");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compass_codec", timeoutTicks = 20)
    public static void payloadSurvivesTheWire(GameTestHelper h) {
        GeigerPayload p = new GeigerPayload(1.5e-3f, true, 12.5f, 64f, -7.25f, 0.02f, true);
        ByteBuf buf = Unpooled.buffer();
        GeigerPayload.STREAM_CODEC.encode(buf, p);
        GeigerPayload q = GeigerPayload.STREAM_CODEC.decode(buf);
        if (!p.equals(q) || buf.isReadable()) throw new GameTestAssertException("Paket veraendert: " + q);
        GeigerPayload r = GeigerPayload.reading(0.5f);
        if (r.hasTarget() || r.here()) throw new GameTestAssertException("Messwert-Paket hat ein Ziel");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compass_scan", timeoutTicks = 40)
    public static void scanFindsEmitterAndPayloadNeedsHand(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos srcRel = new BlockPos(6, 2, 2);
        BlockPos src = h.absolutePos(srcRel);
        Vec3 from = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
        RadiationManager.emit(lv, src, 1000.0);
        RadiationCompass.Target t = RadiationCompass.scan(lv, from);
        if (t == null) throw new GameTestAssertException("Quelle nicht gefunden");
        if (t.pos().distanceTo(Vec3.atCenterOf(src)) > 0.01) throw new GameTestAssertException("Falsche Quelle: " + t.pos());
        near("Abstand", t.distance(), 4.0);
        if (t.level() != 2) throw new GameTestAssertException("Starke Quelle nicht rot: " + t.level());
        if (t.here(from)) throw new GameTestAssertException("Quelle faelschlich 'hier'");

        ServerPlayer sp = FakePlayerFactory.get(lv, new GameProfile(UUID.fromString("7a1c2e3f-4b5d-4e6f-8a90-1b2c3d4e5f71"), "nsq_compass_a"));
        sp.setPos(from.x, from.y - sp.getBbHeight() * 0.5, from.z);
        GeigerPayload held = GeigerCounterItem.payload(lv, sp, 1e-3, true);
        if (!held.hasTarget()) throw new GameTestAssertException("Kein Ziel mit dem Geraet in der Hand");
        if (Math.abs(held.x() - (src.getX() + 0.5)) > 1.0 || Math.abs(held.z() - (src.getZ() + 0.5)) > 1.0) {
            throw new GameTestAssertException("Ziel im Paket falsch: " + held);
        }
        GeigerPayload hotbar = GeigerCounterItem.payload(lv, sp, 1e-3, false);
        if (hotbar.hasTarget()) throw new GameTestAssertException("Kompass auch in der Hotbar");
        sp.setPos(src.getX() + 0.5, src.getY() + 0.5 - sp.getBbHeight() * 0.5, src.getZ() + 0.5);
        if (!GeigerCounterItem.payload(lv, sp, 1e-3, true).here()) throw new GameTestAssertException("Auf der Quelle nicht 'hier'");
        h.succeed();
    }
}
