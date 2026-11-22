package com.sqc.nuclearsq.compat.ftbteams;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.fml.ModList;

public final class FtbTeamsCompat {
    private FtbTeamsCompat() {}

    private static Method api, loaded, manager, same;
    private static boolean failed;
    private static Method forPlayer, teamId, teamOwner, party;
    private static boolean teamFailed;

    public record Party(UUID id, UUID owner) {}

    @Nullable
    public static Party party(UUID player) {
        if (teamFailed || !ModList.get().isLoaded("ftbteams")) return null;
        try {
            if (party == null) {
                Class<?> c = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
                Class<?> t = Class.forName("dev.ftb.mods.ftbteams.api.Team");
                api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api");
                loaded = c.getMethod("isManagerLoaded");
                manager = c.getMethod("getManager");
                forPlayer = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager").getMethod("getTeamForPlayerID", UUID.class);
                teamId = t.getMethod("getId");
                teamOwner = t.getMethod("getOwner");
                party = t.getMethod("isPartyTeam");
            }
            Object o = api.invoke(null);
            if (!(boolean) loaded.invoke(o)) return null;
            Optional<?> team = (Optional<?>) forPlayer.invoke(manager.invoke(o), player);
            if (team.isEmpty() || !(boolean) party.invoke(team.get())) return null;
            return new Party((UUID) teamId.invoke(team.get()), (UUID) teamOwner.invoke(team.get()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            teamFailed = true;
            return null;
        }
    }

    public static boolean sameTeam(UUID a, UUID b) {
        if (failed || !ModList.get().isLoaded("ftbteams")) return false;
        try {
            if (same == null) {
                Class<?> c = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
                api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api");
                loaded = c.getMethod("isManagerLoaded");
                manager = c.getMethod("getManager");
                same = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager").getMethod("arePlayersInSameTeam", UUID.class, UUID.class);
            }
            Object o = api.invoke(null);
            return (boolean) loaded.invoke(o) && (boolean) same.invoke(manager.invoke(o), a, b);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            return false;
        }
    }
}
