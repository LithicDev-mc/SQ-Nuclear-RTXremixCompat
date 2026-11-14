package com.sqc.nuclearsq.machine;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class TeamData extends SavedData {
    private static final String KEY = "nuclearsq_teams";
    private static final SavedData.Factory<TeamData> FACTORY = new SavedData.Factory<>(TeamData::new, TeamData::load, null);

    public static final class Team {
        public UUID owner;
        public final Set<UUID> members = new HashSet<>();
    }

    private final Map<String, Team> teams = new TreeMap<>();
    private final Map<String, UUID> reserved = new TreeMap<>();

    public static TeamData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    @Nullable
    public Team team(String name) {
        return teams.get(name);
    }

    public Map<String, Team> all() {
        return teams;
    }

    public boolean create(String name, UUID owner) {
        if (teams.containsKey(name) || reserved.containsKey(name) && !reserved.get(name).equals(owner)) return false;
        reserved.remove(name);
        Team t = new Team();
        t.owner = owner;
        teams.put(name, t);
        setDirty();
        return true;
    }

    public void delete(String name) {
        Team t = teams.remove(name);
        if (t != null) reserved.put(name, t.owner);
        setDirty();
    }

    public boolean inTeam(String name, UUID player) {
        Team t = teams.get(name);
        return t != null && (t.owner.equals(player) || t.members.contains(player));
    }

    public static TeamData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeamData d = new TeamData();
        ListTag list = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Team team = new Team();
            team.owner = UUID.fromString(t.getString("owner"));
            for (Tag s : t.getList("members", Tag.TAG_STRING)) team.members.add(UUID.fromString(s.getAsString()));
            d.teams.put(t.getString("name"), team);
        }
        CompoundTag res = tag.getCompound("reserved");
        for (String k : res.getAllKeys()) d.reserved.put(k, res.getUUID(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        teams.forEach((name, team) -> {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("owner", team.owner.toString());
            ListTag members = new ListTag();
            for (UUID m : team.members) members.add(StringTag.valueOf(m.toString()));
            t.put("members", members);
            list.add(t);
        });
        tag.put("teams", list);
        CompoundTag res = new CompoundTag();
        reserved.forEach(res::putUUID);
        tag.put("reserved", res);
        return tag;
    }
}
