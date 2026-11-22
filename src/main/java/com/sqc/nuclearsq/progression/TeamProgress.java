package com.sqc.nuclearsq.progression;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.compat.ftbteams.FtbTeamsCompat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class TeamProgress extends SavedData {
    private static final String KEY = "nuclearsq_team_progress";
    private static final SavedData.Factory<TeamProgress> FACTORY = new SavedData.Factory<>(TeamProgress::new, TeamProgress::load, null);

    public static final class Entry {
        public boolean single;
        public final Set<String> items = new LinkedHashSet<>();
        public final Set<String> milestones = new LinkedHashSet<>();
    }

    private final Map<UUID, Entry> teams = new HashMap<>();

    public static TeamProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    public Entry entry(UUID team) {
        return teams.computeIfAbsent(team, k -> new Entry());
    }

    @Nullable
    public static UUID team(ServerPlayer p) {
        FtbTeamsCompat.Party party = FtbTeamsCompat.party(p.getUUID());
        return party == null ? null : party.id();
    }

    public static boolean shared(ServerPlayer p) {
        UUID t = team(p);
        return t != null && !get(p.server).entry(t).single;
    }

    public static boolean counts(ServerPlayer p, @Nullable UUID owner, boolean shared) {
        return owner == null || owner.equals(p.getUUID()) || shared && FtbTeamsCompat.sameTeam(owner, p.getUUID());
    }

    public static boolean merge(Entry e, Set<String> items, Set<String> milestones, boolean[] teamChanged) {
        int ti = e.items.size(), tm = e.milestones.size();
        e.items.addAll(items);
        for (String k : milestones) if (!k.startsWith("title:")) e.milestones.add(k);
        teamChanged[0] = e.items.size() != ti || e.milestones.size() != tm;
        int pi = items.size(), pm = milestones.size();
        items.addAll(e.items);
        milestones.addAll(e.milestones);
        return items.size() != pi || milestones.size() != pm;
    }

    public static void share(ServerPlayer p) {
        UUID t = team(p);
        if (t == null) return;
        TeamProgress d = get(p.server);
        Entry e = d.entry(t);
        if (e.single) return;
        Set<String> items = p.getData(Discovered.ITEMS.get()), ms = p.getData(GuideProgress.MILESTONES.get());
        int pi = items.size();
        boolean[] teamChanged = {false};
        if (merge(e, items, ms, teamChanged)) {
            p.setData(Discovered.ITEMS.get(), items);
            p.setData(GuideProgress.MILESTONES.get(), ms);
            if (items.size() != pi) Discovered.sync(p);
            GuideProgress.sync(p);
        }
        if (teamChanged[0]) d.setDirty();
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 40 == 17) share(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) share(sp);
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nsqteam")
                .then(Commands.literal("erfolge")
                        .executes(c -> mode(c, null))
                        .then(Commands.literal("geteilt").executes(c -> mode(c, false)))
                        .then(Commands.literal("einzeln").executes(c -> mode(c, true))))
                .then(Commands.literal("achievements")
                        .executes(c -> mode(c, null))
                        .then(Commands.literal("shared").executes(c -> mode(c, false)))
                        .then(Commands.literal("individual").executes(c -> mode(c, true)))));
    }

    private static Component msg(String key) {
        return Component.translatable("commands.nuclearsq.team.progress." + key);
    }

    private static int mode(CommandContext<CommandSourceStack> c, @Nullable Boolean single) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        FtbTeamsCompat.Party party = FtbTeamsCompat.party(sp.getUUID());
        if (party == null) {
            c.getSource().sendFailure(msg("noteam"));
            return 0;
        }
        TeamProgress d = get(sp.server);
        Entry e = d.entry(party.id());
        if (single != null) {
            if (!party.owner().equals(sp.getUUID()) && !sp.hasPermissions(2)) {
                c.getSource().sendFailure(msg("notowner"));
                return 0;
            }
            e.single = single;
            d.setDirty();
            if (!single) share(sp);
        }
        c.getSource().sendSuccess(() -> msg(e.single ? "single" : "shared"), false);
        return 1;
    }

    private static ListTag list(Set<String> s) {
        ListTag l = new ListTag();
        for (String k : s) l.add(StringTag.valueOf(k));
        return l;
    }

    public static TeamProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        TeamProgress d = new TeamProgress();
        ListTag list = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Entry e = d.entry(t.getUUID("id"));
            e.single = t.getBoolean("single");
            for (Tag s : t.getList("items", Tag.TAG_STRING)) e.items.add(s.getAsString());
            for (Tag s : t.getList("milestones", Tag.TAG_STRING)) e.milestones.add(s.getAsString());
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        teams.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putBoolean("single", e.single);
            t.put("items", list(e.items));
            t.put("milestones", list(e.milestones));
            list.add(t);
        });
        tag.put("teams", list);
        return tag;
    }
}
