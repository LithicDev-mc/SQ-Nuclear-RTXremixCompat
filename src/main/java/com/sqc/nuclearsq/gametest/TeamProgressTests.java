package com.sqc.nuclearsq.gametest;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.TeamProgress;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class TeamProgressTests {
    private TeamProgressTests() {}

    @GameTest(template = "floor", batch = "team_progress", timeoutTicks = 40)
    public static void onlyOwnMachinesCount(GameTestHelper h) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("team_progress_a".getBytes()), "team_progress_a"));
        UUID other = UUID.nameUUIDFromBytes("team_progress_b".getBytes());
        if (!TeamProgress.counts(p, p.getUUID(), false)) throw new GameTestAssertException("Eigene Maschine zaehlt nicht");
        if (!TeamProgress.counts(p, null, false)) throw new GameTestAssertException("Maschine ohne Besitzer zaehlt nicht");
        if (TeamProgress.counts(p, other, false) || TeamProgress.counts(p, other, true))
            throw new GameTestAssertException("Fremde Maschine ohne gemeinsames Team zaehlt");
        if (TeamProgress.team(p) != null || TeamProgress.shared(p)) throw new GameTestAssertException("Spieler ohne FTB-Team gilt als Team");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "team_progress", timeoutTicks = 40)
    public static void sharedProgressReachesEveryMember(GameTestHelper h) {
        TeamProgress.Entry e = new TeamProgress.Entry();
        Set<String> aItems = new LinkedHashSet<>(Set.of("uranium_ingot_sqc")), aMs = new LinkedHashSet<>(Set.of("formed:reactor", "title:first_power"));
        Set<String> bItems = new LinkedHashSet<>(), bMs = new LinkedHashSet<>();
        boolean[] changed = {false};
        TeamProgress.merge(e, aItems, aMs, changed);
        if (!changed[0] || e.milestones.contains("title:first_power")) throw new GameTestAssertException("Teamstand falsch: " + e.milestones);
        if (!TeamProgress.merge(e, bItems, bMs, changed) || changed[0]) throw new GameTestAssertException("Zweites Mitglied bekommt nichts");
        if (!bItems.contains("uranium_ingot_sqc") || !bMs.contains("formed:reactor") || bMs.contains("title:first_power"))
            throw new GameTestAssertException("Zweites Mitglied hat falschen Stand: " + bItems + " " + bMs);
        if (TeamProgress.merge(e, bItems, bMs, changed)) throw new GameTestAssertException("Zweiter Abgleich aendert noch etwas");
        h.succeed();
    }
}
