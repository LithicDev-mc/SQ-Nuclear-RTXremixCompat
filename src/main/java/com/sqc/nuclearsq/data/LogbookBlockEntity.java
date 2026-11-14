package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LogbookBlockEntity extends BlockEntity {
    public static final int PERIOD = 20;
    public static final int MAX_ENTRIES = 500;
    public static final int COOLDOWN = 60;
    public static final int MAX_PER_SCAN = 24;
    private static final int NET_PERIODS = 10;

    private record Seen(int state, Component text, Component name, long loggedAt) {}

    private final List<LogEntry> entries = new ArrayList<>();
    private long nextSeq = 1;
    private final Map<BlockPos, Seen> seen = new HashMap<>();
    private boolean initialized;
    @Nullable private List<BlockPos> netPorts;
    private int netAge;

    public LogbookBlockEntity(BlockPos pos, BlockState state) {
        super(DataRegistry.LOGBOOK_BE.get(), pos, state);
    }

    public List<LogEntry> entries() {
        return entries;
    }

    public long nextSeq() {
        return nextSeq;
    }

    public List<LogEntry> since(long from) {
        List<LogEntry> out = new ArrayList<>();
        for (LogEntry e : entries) if (e.seq() >= from) out.add(e);
        return out;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        if (!LogbookBlock.layout(level, worldPosition).origin().equals(worldPosition)) return;
        scan();
    }

    public void scan() {
        if (level == null || level.isClientSide) return;
        List<DataSource> net = netPorts == null || ++netAge >= NET_PERIODS ? null : DataNet.read(level, netPorts);
        boolean full = net == null;
        boolean complete = true;
        if (full) {
            List<BlockPos> cells = LogbookBlock.layout(level, worldPosition).cells();
            DataNet.Result r = DataNet.scanChecked(level, cells);
            netPorts = DataNet.scanPorts(level, cells);
            netAge = 0;
            net = r.sources();
            complete = !r.incomplete();
        }
        long now = level.getGameTime();
        int added = 0;
        java.util.Set<BlockPos> present = new java.util.HashSet<>();
        for (DataSource s : net) present.add(s.pos());
        for (DataSource s : net) {
            if (added >= MAX_PER_SCAN) break;
            if (observe(s, now)) added++;
        }
        if (full && complete) {
            for (var it = seen.entrySet().iterator(); it.hasNext() && added < MAX_PER_SCAN; ) {
                var e = it.next();
                if (present.contains(e.getKey())) continue;
                it.remove();
                add(LogEntry.INFO, Component.translatable("logbook.nuclearsq.lost", e.getValue().name()));
                added++;
            }
        }
        if (!initialized) {
            initialized = true;
            add(LogEntry.INFO, Component.translatable("logbook.nuclearsq.start", net.size()));
            added++;
        }
        if (added > 0) setChanged();
    }

    private boolean observe(DataSource s, long now) {
        int state = s.state();
        Component text = s.stateText();
        Component name = s.name();
        Seen prev = seen.get(s.pos());
        if (prev == null) {
            seen.put(s.pos(), new Seen(state, text, name, now));
            if (state == DataSource.WARN || state == DataSource.ALARM) {
                add(severity(state), Component.translatable("logbook.nuclearsq.state", name, text));
                return true;
            }
            if (initialized) {
                add(LogEntry.INFO, Component.translatable("logbook.nuclearsq.linked", name));
                return true;
            }
            return false;
        }
        boolean raised = state == DataSource.WARN || state == DataSource.ALARM;
        boolean wasRaised = prev.state == DataSource.WARN || prev.state == DataSource.ALARM;
        boolean changed = raised ? state != prev.state || !text.equals(prev.text) : state != prev.state;
        if (!changed) {
            if (!name.equals(prev.name)) seen.put(s.pos(), new Seen(prev.state, prev.text, name, prev.loggedAt));
            return false;
        }
        if (now - prev.loggedAt < COOLDOWN) return false;
        seen.put(s.pos(), new Seen(state, text, name, now));
        if (raised) add(severity(state), Component.translatable("logbook.nuclearsq.state", name, text));
        else if (wasRaised) add(LogEntry.CLEAR, Component.translatable("logbook.nuclearsq.clear", name, text));
        else add(LogEntry.INFO, Component.translatable("logbook.nuclearsq.state", name, text));
        return true;
    }

    private static int severity(int state) {
        return state == DataSource.ALARM ? LogEntry.ALARM : LogEntry.WARN;
    }

    public void add(int severity, Component text) {
        if (level == null) return;
        entries.add(new LogEntry(nextSeq++, level.getDayTime(), System.currentTimeMillis(), severity, text));
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public void clear() {
        entries.clear();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public static final int SHOWN = 40;
    public static final int TEXT_SIZES = 5, DEFAULT_TEXT_SIZE = 1;
    private int textSize = DEFAULT_TEXT_SIZE;

    public int textSize() {
        return textSize;
    }

    public int nextTextSize() {
        textSize = (textSize + 1) % TEXT_SIZES;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        return textSize;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (LogEntry e : entries.subList(Math.max(0, entries.size() - SHOWN), entries.size())) list.add(e.save(registries));
        tag.put("entries", list);
        tag.putLong("next", nextSeq);
        tag.putInt("size", textSize);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (LogEntry e : entries) list.add(e.save(registries));
        tag.put("entries", list);
        tag.putLong("next", nextSeq);
        tag.putInt("size", textSize);
        tag.putBoolean("init", initialized);
        ListTag known = new ListTag();
        for (var e : seen.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey().asLong());
            t.putInt("state", e.getValue().state);
            t.putString("text", Component.Serializer.toJson(e.getValue().text, registries));
            t.putString("name", Component.Serializer.toJson(e.getValue().name, registries));
            known.add(t);
        }
        tag.put("seen", known);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entries.clear();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) entries.add(LogEntry.load((CompoundTag) t, registries));
        nextSeq = Math.max(tag.getLong("next"), entries.isEmpty() ? 1 : entries.get(entries.size() - 1).seq() + 1);
        initialized = tag.getBoolean("init");
        textSize = tag.contains("size") ? Math.max(0, Math.min(TEXT_SIZES - 1, tag.getInt("size"))) : DEFAULT_TEXT_SIZE;
        seen.clear();
        for (Tag t : tag.getList("seen", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            seen.put(BlockPos.of(c.getLong("pos")), new Seen(c.getInt("state"),
                    DataInstrumentBlockEntity.json(c.getString("text"), registries),
                    DataInstrumentBlockEntity.json(c.getString("name"), registries), Long.MIN_VALUE / 2));
        }
    }
}
