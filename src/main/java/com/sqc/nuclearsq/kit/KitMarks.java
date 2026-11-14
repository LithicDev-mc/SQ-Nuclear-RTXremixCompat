package com.sqc.nuclearsq.kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class KitMarks {
    private KitMarks() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    public static final class Data {
        final Long2LongOpenHashMap parts = new Long2LongOpenHashMap();
        final Long2IntOpenHashMap ctrls = new Long2IntOpenHashMap();

        boolean isEmpty() {
            return parts.isEmpty() && ctrls.isEmpty();
        }
    }

    private static final IAttachmentSerializer<CompoundTag, Data> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public Data read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            long[] p = tag.getLongArray("parts");
            for (int i = 0; i + 1 < p.length; i += 2) d.parts.put(p[i], p[i + 1]);
            long[] c = tag.getLongArray("ctrls");
            for (int i = 0; i + 1 < c.length; i += 2) d.ctrls.put(c[i], (int) c[i + 1]);
            return d;
        }

        @Nullable
        @Override
        public CompoundTag write(Data d, HolderLookup.Provider provider) {
            if (d.isEmpty()) return null;
            long[] p = new long[d.parts.size() * 2];
            int i = 0;
            for (var e : d.parts.long2LongEntrySet()) {
                p[i++] = e.getLongKey();
                p[i++] = e.getLongValue();
            }
            long[] c = new long[d.ctrls.size() * 2];
            i = 0;
            for (var e : d.ctrls.long2IntEntrySet()) {
                c[i++] = e.getLongKey();
                c[i++] = e.getIntValue();
            }
            CompoundTag tag = new CompoundTag();
            tag.put("parts", new LongArrayTag(p));
            tag.put("ctrls", new LongArrayTag(c));
            return tag;
        }
    };

    public static final Supplier<AttachmentType<Data>> MARKS = ATTACHMENTS.register("kit_marks",
            () -> AttachmentType.builder(Data::new).serialize(SERIALIZER).build());

    @Nullable
    private static Data data(Level level, BlockPos pos, boolean load) {
        if (!load && !level.isLoaded(pos)) return null;
        LevelChunk ch = level.getChunkAt(pos);
        return ch.hasData(MARKS) ? ch.getData(MARKS) : null;
    }

    public static boolean marked(Level level, BlockPos pos) {
        Data d = data(level, pos, false);
        return d != null && d.parts.containsKey(pos.asLong());
    }

    @Nullable
    public static MachineKits.Placement owner(Level level, BlockPos pos) {
        Data d = data(level, pos, false);
        if (d == null || !d.parts.containsKey(pos.asLong())) return null;
        return at(level, BlockPos.of(d.parts.get(pos.asLong())));
    }

    @Nullable
    static MachineKits.Placement at(Level level, BlockPos ctrl) {
        Data d = data(level, ctrl, true);
        if (d == null || !d.ctrls.containsKey(ctrl.asLong())) return null;
        int v = d.ctrls.get(ctrl.asLong());
        List<MachineKits.Def> defs = new ArrayList<>(MachineKits.all().values());
        int idx = v >> 2;
        return idx < defs.size() ? new MachineKits.Placement(defs.get(idx), ctrl, v & 3) : null;
    }

    static void mark(Level level, MachineKits.Placement pl) {
        long ctrl = pl.anchor().asLong();
        for (BlockPos p : pl.footprint()) edit(level, p).parts.put(p.asLong(), ctrl);
        int idx = new ArrayList<>(MachineKits.all().keySet()).indexOf(pl.def().id());
        edit(level, pl.anchor()).ctrls.put(ctrl, idx * 4 + (pl.rot() & 3));
    }

    static void unmark(Level level, MachineKits.Placement pl) {
        long ctrl = pl.anchor().asLong();
        for (BlockPos p : pl.footprint()) {
            Data d = data(level, p, true);
            if (d != null && d.parts.get(p.asLong()) == ctrl && d.parts.containsKey(p.asLong())) {
                d.parts.remove(p.asLong());
                changed(level, p, d);
            }
        }
        Data d = data(level, pl.anchor(), true);
        if (d != null && d.ctrls.containsKey(ctrl)) {
            d.ctrls.remove(ctrl);
            changed(level, pl.anchor(), d);
        }
    }

    private static Data edit(Level level, BlockPos pos) {
        LevelChunk ch = level.getChunkAt(pos);
        Data d = ch.getData(MARKS);
        ch.setUnsaved(true);
        return d;
    }

    private static void changed(Level level, BlockPos pos, Data d) {
        LevelChunk ch = level.getChunkAt(pos);
        if (d.isEmpty()) ch.removeData(MARKS);
        ch.setUnsaved(true);
    }

    private static final Map<ResourceKey<Level>, LongOpenHashSet> RECENT = new HashMap<>();
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();

    private record Pending(ServerLevel level, BlockPos ctrl, boolean kit) {}

    private static final List<Nuke> NUKES = new ArrayList<>();

    private record Nuke(ServerLevel level, BlockPos center, int r) {
        boolean hits(Level lv, BlockPos p) {
            return lv == level && com.sqc.nuclearsq.explosion.NuclearExplosion.inCrater(r, com.sqc.nuclearsq.explosion.NuclearExplosion.craterSeed(center),
                    p.getX(), p.getZ(), p.getX() - center.getX(), p.getY() - center.getY(), p.getZ() - center.getZ(), 0);
        }
    }

    public static List<ItemStack> drops(LootParams.Builder b, Supplier<List<ItemStack>> normal) {
        Vec3 o = b.getOptionalParameter(LootContextParams.ORIGIN);
        if (o == null) return normal.get();
        BlockPos pos = BlockPos.containing(o);
        ServerLevel level = b.getLevel();
        LongOpenHashSet recent = RECENT.get(level.dimension());
        if (marked(level, pos) || recent != null && recent.contains(pos.asLong())) return List.of();
        return normal.get();
    }

    public static void removed(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (state.is(newState.getBlock()) || !(level instanceof ServerLevel sl)) return;
        Data d = data(level, pos, false);
        if (d == null || !d.parts.containsKey(pos.asLong())) return;
        boolean nuked = false;
        for (Nuke n : NUKES) nuked |= n.hits(level, pos);
        PENDING.add(new Pending(sl, BlockPos.of(d.parts.get(pos.asLong())), !nuked));
    }

    static void remember(Level level, MachineKits.Placement pl) {
        LongOpenHashSet s = RECENT.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
        for (BlockPos p : pl.footprint()) s.add(p.asLong());
    }

    static void tick() {
        RECENT.clear();
        while (!PENDING.isEmpty()) {
            Pending p = PENDING.poll();
            MachineKits.Placement pl = at(p.level(), p.ctrl());
            if (pl != null) MachineKits.dismantle(p.level(), pl, p.kit());
        }
        if (!NUKES.isEmpty() && !com.sqc.nuclearsq.explosion.NuclearExplosion.changingBlocks()) NUKES.clear();
    }

    public static void nuke(ServerLevel level, BlockPos center, int r) {
        NUKES.add(new Nuke(level, center, r));
        int seed = com.sqc.nuclearsq.explosion.NuclearExplosion.craterSeed(center);
        int reach = (int) Math.ceil(r * com.sqc.nuclearsq.explosion.NuclearExplosion.RIM_WIDTH * 1.12) + 1;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet ctrls = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (int cx = (center.getX() - reach) >> 4; cx <= (center.getX() + reach) >> 4; cx++) {
            for (int cz = (center.getZ() - reach) >> 4; cz <= (center.getZ() + reach) >> 4; cz++) {
                if (!level.hasChunk(cx, cz) || com.sqc.nuclearsq.explosion.BlastShield.blocked(level, center, cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                if (!ch.hasData(MARKS)) continue;
                for (var e : ch.getData(MARKS).parts.long2LongEntrySet()) {
                    long k = e.getLongKey();
                    int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                    if (com.sqc.nuclearsq.explosion.NuclearExplosion.inCrater(r, seed, x, z, x - center.getX(), y - center.getY(), z - center.getZ(), 0)) {
                        ctrls.add(e.getLongValue());
                    }
                }
            }
        }
        for (long c : ctrls) {
            MachineKits.Placement pl = at(level, BlockPos.of(c));
            if (pl != null) MachineKits.dismantle(level, pl, false);
        }
    }

    static void clear() {
        PENDING.clear();
        RECENT.clear();
        NUKES.clear();
    }
}
