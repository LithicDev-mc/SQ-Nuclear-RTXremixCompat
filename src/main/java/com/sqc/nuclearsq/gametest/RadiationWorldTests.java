package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.RadiationAttachments;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationWorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationWorldTests {
    private RadiationWorldTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "rad_world_save", timeoutTicks = 20)
    public static void coreSurvivesRestart(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        RadiationWorldData d = new RadiationWorldData();
        long now = lv.getGameTime();
        Vec3 core = new Vec3(10.5, 70, -4.5);
        d.addCore(new RadiationWorldData.Core(core, 30.0, now - 400, now + 20_000));
        long f = new BlockPos(3, 65, 9).asLong();
        d.addFallout(f);
        CompoundTag tag = d.save(new CompoundTag(), lv.registryAccess());
        RadiationWorldData back = RadiationWorldData.load(tag, lv.registryAccess());
        if (back.cores().size() != 1) throw new GameTestAssertException("Kern nach dem Laden: " + back.cores().size() + " statt 1");
        RadiationWorldData.Core c = back.cores().get(0);
        if (!c.pos().equals(core) || c.start() != now - 400 || c.end() != now + 20_000 || c.strength() != 30.0) {
            throw new GameTestAssertException("Kern veraendert: " + c);
        }
        if (Math.abs(c.rateAt(now) - d.cores().get(0).rateAt(now)) > 1e-12) throw new GameTestAssertException("Kern strahlt nach dem Laden anders");
        if (!back.hasFallout(f) || back.falloutCount() != 1) throw new GameTestAssertException("Niederschlag nicht gespeichert");
        RadiationManager.addCore(lv, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 3, 1))), 1e-6);
        var cores = RadiationWorldData.get(lv).cores();
        if (cores.isEmpty() || cores.get(cores.size() - 1).start() != lv.getGameTime()) {
            throw new GameTestAssertException("Kernbeginn ist nicht die Spielzeit der Welt");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_world_save", timeoutTicks = 20)
    public static void contaminationIsSavedWithWorldData(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        BlockPos spot = h.absolutePos(new BlockPos(2, 2, 2));
        try {
            Contamination.add(lv, spot, 0.01f);
            RadiationWorldData d = RadiationWorldData.get(lv);
            RadiationWorldData back = RadiationWorldData.load(d.save(new CompoundTag(), lv.registryAccess()), lv.registryAccess());
            float before = Contamination.zones(lv).get(spot);
            float after = back.zones().get(spot);
            if (before <= 0 || Math.abs(before - after) > 1e-9) throw new GameTestAssertException("Kontamination " + before + " -> " + after);
        } finally {
            Contamination.clean(lv, spot, 0, 1f);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_world_nether", timeoutTicks = 40)
    public static void netherRadiatesOnItsOwn(GameTestHelper h) {
        ServerLevel over = h.getLevel();
        ServerLevel nether = over.getServer().getLevel(Level.NETHER);
        if (nether == null) throw new GameTestAssertException("Kein Nether");
        Vec3 at = new Vec3(0.5, 140.5, 0.5);
        BlockPos fall = BlockPos.containing(at).offset(2, 0, 0);
        double overBefore = RadiationManager.rateAt(over, at);
        double netherBefore = RadiationManager.rateAt(nether, at);
        long now = nether.getGameTime();
        RadiationWorldData nd = RadiationWorldData.get(nether);
        try {
            nd.addFallout(fall.asLong());
            nd.addCore(new RadiationWorldData.Core(at.add(0, 0, 3), 1.0, now, now + 1));
            double netherNow = RadiationManager.rateAt(nether, at);
            if (netherNow <= netherBefore + 1e-6) throw new GameTestAssertException("Nether strahlt nicht: " + netherBefore + " -> " + netherNow);
            double overNow = RadiationManager.rateAt(over, at);
            if (Math.abs(overNow - overBefore) > 1e-12) throw new GameTestAssertException("Nether-Quellen wirken in der Oberwelt: " + overBefore + " -> " + overNow);
        } finally {
            nd.removeFallout(fall.asLong());
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_world_death", timeoutTicks = 20)
    public static void onlyDosimeterTotalSurvivesDeath(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer old = FakePlayerFactory.get(lv, new GameProfile(UUID.fromString("4e5c3a10-0000-4000-8000-00000000d001"), "[NuclearSQ-Tod-A]"));
        ServerPlayer died = FakePlayerFactory.get(lv, new GameProfile(UUID.fromString("4e5c3a10-0000-4000-8000-00000000d002"), "[NuclearSQ-Tod-B]"));
        ServerPlayer moved = FakePlayerFactory.get(lv, new GameProfile(UUID.fromString("4e5c3a10-0000-4000-8000-00000000d003"), "[NuclearSQ-Tod-C]"));
        try {
            RadiationManager.setDose(old, 2.0);
            Contamination.setBody(old, 0.01);
            old.setData(RadiationAttachments.DOSIMETER_TOTAL, 0.5);
            old.setData(RadiationAttachments.DOSIMETER, RadiationAttachments.Dosimeter.EMPTY.withDay(0, 0.2, 1).withPinned(true));
            old.setData(RadiationAttachments.IODINE_UNTIL, lv.getGameTime() + 1000);
            old.setData(RadiationAttachments.MEDICINE_UNTIL, lv.getGameTime() + 1000);

            EventHooks.onPlayerClone(died, old, true);
            if (Math.abs(DosimeterItem.total(died) - 0.5) > 1e-12) throw new GameTestAssertException("Gesamtdosis nach dem Tod " + DosimeterItem.total(died) + " statt 0,5");
            if (RadiationManager.doseOf(died) != 0) throw new GameTestAssertException("Dosis ueberlebt den Tod");
            if (Contamination.body(died) != 0) throw new GameTestAssertException("Koerper-Kontamination ueberlebt den Tod");
            if (died.hasData(RadiationAttachments.DOSIMETER) || DosimeterItem.isPinned(died)) throw new GameTestAssertException("Tagesdosis/Fixierung ueberlebt den Tod");
            if (RadiationManager.iodineTicksLeft(died) != 0 || RadiationManager.medicineTicksLeft(died) != 0) throw new GameTestAssertException("Iod/Medizin ueberleben den Tod");

            EventHooks.onPlayerClone(moved, old, false);
            if (Math.abs(RadiationManager.doseOf(moved) - 2.0) > 1e-12 || Contamination.body(moved) <= 0 || !DosimeterItem.isPinned(moved)
                    || RadiationManager.iodineTicksLeft(moved) <= 0) {
                throw new GameTestAssertException("Ohne Tod gehen Werte verloren");
            }
        } finally {
            for (ServerPlayer p : new ServerPlayer[] {old, died, moved}) clear(p);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_world_mob", timeoutTicks = 20)
    public static void mobDoseIsSavedWithMob(GameTestHelper h) {
        Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        RadiationManager.setDose(z, 1.25);
        CompoundTag tag = z.saveWithoutId(new CompoundTag());
        Zombie copy = EntityType.ZOMBIE.create(h.getLevel());
        if (copy == null) throw new GameTestAssertException("Kein Zombie");
        copy.load(tag);
        if (Math.abs(RadiationManager.doseOf(copy) - 1.25) > 1e-12) throw new GameTestAssertException("Mob-Dosis nach dem Laden " + RadiationManager.doseOf(copy));
        z.discard();
        h.succeed();
    }

    private static void clear(ServerPlayer p) {
        RadiationManager.setDose(p, 0);
        Contamination.setBody(p, 0);
        p.removeData(RadiationAttachments.DOSIMETER);
        p.removeData(RadiationAttachments.DOSIMETER_TOTAL);
        p.removeData(RadiationAttachments.IODINE_UNTIL);
        p.removeData(RadiationAttachments.MEDICINE_UNTIL);
    }
}
