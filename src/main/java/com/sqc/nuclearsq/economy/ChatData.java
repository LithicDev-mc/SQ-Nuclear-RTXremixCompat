package com.sqc.nuclearsq.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChatData extends SavedData {
    private static final String KEY = "nuclearsq_chat";
    private static final SavedData.Factory<ChatData> FACTORY = new SavedData.Factory<>(ChatData::new, ChatData::load, null);

    public static final int MAX_TEXT = 200, MAX_PER_CHAT = 100, FETCH = 200;
    public static final long KEEP = 7 * EconomyData.DAY;

    public record Msg(int id, UUID from, @Nullable UUID to, String text, long time) {}

    private final List<Msg> msgs = new ArrayList<>();
    private int nextId = 1;

    public static ChatData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    private static boolean sameChat(Msg a, UUID from, @Nullable UUID to) {
        if (to == null || a.to == null) return to == null && a.to == null;
        return a.from.equals(from) && a.to.equals(to) || a.from.equals(to) && a.to.equals(from);
    }

    public int send(UUID from, @Nullable UUID to, String text, long now) {
        text = EconomyData.clean(text, MAX_TEXT);
        if (text.isEmpty()) throw new BankException("message is empty");
        msgs.removeIf(m -> now - m.time() > KEEP || now < m.time());
        int count = 1;
        for (Msg m : msgs) if (sameChat(m, from, to)) count++;
        for (int i = 0; count > MAX_PER_CHAT && i < msgs.size(); ) {
            if (sameChat(msgs.get(i), from, to)) {
                msgs.remove(i);
                count--;
            } else i++;
        }
        Msg m = new Msg(nextId++, from, to, text, now);
        msgs.add(m);
        setDirty();
        return m.id();
    }

    public List<Msg> since(UUID me, int after) {
        List<Msg> out = new ArrayList<>();
        for (Msg m : msgs) if (m.id() > after && (m.to() == null || m.from().equals(me) || me.equals(m.to()))) out.add(m);
        return out.size() > FETCH ? out.subList(out.size() - FETCH, out.size()) : out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextId);
        ListTag list = new ListTag();
        for (Msg m : msgs) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", m.id());
            t.putUUID("from", m.from());
            if (m.to() != null) t.putUUID("to", m.to());
            t.putString("text", m.text());
            t.putLong("time", m.time());
            list.add(t);
        }
        tag.put("msgs", list);
        return tag;
    }

    public static ChatData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChatData d = new ChatData();
        d.nextId = Math.max(1, tag.getInt("next"));
        for (Tag e : tag.getList("msgs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            d.msgs.add(new Msg(t.getInt("id"), t.getUUID("from"), t.hasUUID("to") ? t.getUUID("to") : null, t.getString("text"), t.getLong("time")));
        }
        return d;
    }
}
