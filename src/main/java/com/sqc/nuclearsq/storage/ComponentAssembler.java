package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.sqc.nuclearsq.data.ChunkGuard;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ComponentAssembler {
    private static final Logger LOG = LogUtils.getLogger();

    public static final int CARDS = 8, GRID = 9;
    public static final int IDLE = 0, WORKING = 1, WAITING = 2, NO_POWER = 3, OFF = 4, NO_STORAGE = 5, BLOCKED = 6, CHUNK = 7;
    private static final int REFRESH_TICKS = 20;
    private static final int STALE_CLAIM_TICKS = 100;

    public static final class Job {
        public final ItemStack target;
        public final int amount;
        final long id;
        final int size;
        final List<Pattern> patterns;
        final int[] left;
        int step;
        int inflight;
        long lastProgress = -1;
        long beat;

        Job(long id, ItemStack target, int amount, int size, List<Pattern> patterns, int[] left, int step) {
            this.id = id;
            this.target = target.copyWithCount(1);
            this.amount = amount;
            this.size = size;
            this.patterns = patterns;
            this.left = left;
            this.step = step;
        }

        static int sum(int[] left) {
            long n = 0;
            for (int v : left) n += Math.max(0, v);
            return (int) Math.min(Integer.MAX_VALUE, n);
        }

        int available() {
            return step < left.length ? Math.max(0, left[step] - inflight) : 0;
        }

        public int size() { return size; }
        public long id() { return id; }

        public int steps() { return patterns.size(); }
        public int step() { return step; }
        Pattern current() { return patterns.get(step); }
        public int leftInStep() { return step < left.length ? left[step] : 0; }

        boolean done(int k) {
            left[step] -= k;
            int before = step;
            while (step < left.length && left[step] <= 0) step++;
            if (step != before) inflight = 0;
            return step >= left.length;
        }

        CompoundTag save(HolderLookup.Provider reg) {
            CompoundTag t = new CompoundTag();
            t.put("target", target.save(reg));
            t.putInt("amount", amount);
            t.putLong("id", id);
            t.putInt("size", size);
            t.putInt("step", step);
            t.putIntArray("left", left);
            ListTag ps = new ListTag();
            var ops = reg.createSerializationContext(NbtOps.INSTANCE);
            for (Pattern p : patterns) Pattern.CODEC.encodeStart(ops, p).result().ifPresent(ps::add);
            t.put("patterns", ps);
            return t;
        }

        static Job load(CompoundTag t, HolderLookup.Provider reg) {
            var ops = reg.createSerializationContext(NbtOps.INSTANCE);
            List<Pattern> ps = new ArrayList<>();
            for (Tag e : t.getList("patterns", Tag.TAG_COMPOUND)) Pattern.CODEC.parse(ops, e).result().ifPresent(ps::add);
            int[] left = t.getIntArray("left");
            if (ps.isEmpty() || left.length != ps.size()) return null;
            for (int i = 0; i < left.length; i++) left[i] = Math.max(0, left[i]);
            ItemStack target = ItemStack.parseOptional(reg, t.getCompound("target"));
            if (target.isEmpty()) return null;
            long id = t.contains("id") ? t.getLong("id") : newId();
            int size = t.contains("size") ? Math.max(1, t.getInt("size")) : Math.max(1, sum(left));
            return new Job(id, target, t.getInt("amount"), size, ps, left, Math.min(Math.max(0, t.getInt("step")), left.length));
        }
    }

    public static volatile int testFault;

    private static final class Booking {
        long seq, jobId;
        int step, k, phase;
        BlockPos owner;
        final Map<ItemKey, Long> taken = new java.util.LinkedHashMap<>();
        final Map<ItemKey, Long> outs = new java.util.LinkedHashMap<>();

        static ListTag saveMap(Map<ItemKey, Long> m, HolderLookup.Provider reg) {
            ListTag l = new ListTag();
            m.forEach((key, n) -> {
                CompoundTag t = new CompoundTag();
                t.put("item", key.proto().save(reg));
                t.putLong("n", n);
                l.add(t);
            });
            return l;
        }

        static void loadMap(ListTag l, Map<ItemKey, Long> m, HolderLookup.Provider reg) {
            for (Tag e : l) {
                ItemKey key = ItemKey.of(ItemStack.parseOptional(reg, ((CompoundTag) e).getCompound("item")));
                long n = ((CompoundTag) e).getLong("n");
                if (key != null && n > 0) m.merge(key, n, Long::sum);
            }
        }

        CompoundTag save(HolderLookup.Provider reg) {
            CompoundTag t = new CompoundTag();
            t.putLong("seq", seq);
            t.putLong("job", jobId);
            t.putInt("step", step);
            t.putInt("k", k);
            t.putInt("phase", phase);
            t.putLong("owner", owner.asLong());
            t.put("taken", saveMap(taken, reg));
            t.put("outs", saveMap(outs, reg));
            return t;
        }

        static Booking load(CompoundTag t, HolderLookup.Provider reg) {
            Booking b = new Booking();
            b.seq = t.getLong("seq");
            b.jobId = t.getLong("job");
            b.step = t.getInt("step");
            b.k = t.getInt("k");
            b.phase = t.getInt("phase");
            b.owner = BlockPos.of(t.getLong("owner"));
            loadMap(t.getList("taken", Tag.TAG_COMPOUND), b.taken, reg);
            loadMap(t.getList("outs", Tag.TAG_COMPOUND), b.outs, reg);
            return b;
        }
    }

    private final BigMachine machine;
    private final MachineEnergy energy;
    private final ItemStackHandler buffer;
    private final Runnable changed;
    private final ItemStackHandler cards, encode, template, display;
    private final ItemStackHandler upgrades;
    private final List<Job> jobs = new ArrayList<>();
    private final Lane[] lanes;
    private int status = OFF, previewKind, laneCap = 1, peerCount = 1;
    private long sizeCap = Long.MAX_VALUE, lastRefresh = Long.MIN_VALUE;
    private List<BigMachineBlockEntity> peers = List.of();
    private boolean bufferHasItems;
    @Nullable private MinecraftServer server;
    @Nullable private String ledgerKey;
    private boolean bound;
    private long bookingSeq;
    @Nullable private Booking pending;
    @Nullable private CompoundTag legacy;
    private boolean cardsDirty = true;
    private List<Pattern> netOnline = List.of(), netKnown = List.of();
    private boolean chunkOk = true;
    private final java.util.Set<Long> forced = new java.util.HashSet<>();

    private static final class Lane {
        int progress, batch, step, cool, state = IDLE;
        long jobId;
        @javax.annotation.Nullable BlockPos owner;
        boolean registered;

        boolean busy() { return batch > 0; }

        void clear() {
            progress = 0;
            batch = 0;
            jobId = 0;
            owner = null;
            registered = false;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("progress", progress);
            t.putInt("batch", batch);
            t.putInt("step", step);
            t.putLong("job", jobId);
            if (owner != null) t.putLong("owner", owner.asLong());
            return t;
        }

        void load(CompoundTag t) {
            clear();
            batch = Math.max(0, t.getInt("batch"));
            if (batch == 0 || !t.contains("owner")) {
                clear();
                return;
            }
            progress = Math.max(0, t.getInt("progress"));
            step = t.getInt("step");
            jobId = t.getLong("job");
            owner = BlockPos.of(t.getLong("owner"));
        }
    }

    private static long newId() {
        long id;
        do id = java.util.concurrent.ThreadLocalRandom.current().nextLong(); while (id == 0);
        return id;
    }

    public ComponentAssembler(BigMachine machine, MachineEnergy energy, ItemStackHandler buffer, Runnable changed) {
        this.machine = machine;
        this.energy = energy;
        this.buffer = buffer;
        this.changed = changed;
        cards = new ItemStackHandler(CARDS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return PatternCardItem.pattern(stack) != null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                cardsDirty = true;
                markChanged();
            }
        };
        encode = new ItemStackHandler(2) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 && PatternCardItem.isBlank(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                markChanged();
            }
        };
        template = new ItemStackHandler(GRID) {
            @Override
            protected void onContentsChanged(int slot) {
                markChanged();
            }
        };
        display = new ItemStackHandler(2);
        upgrades = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(StorageRegistry.ASSEMBLER_TEMPO.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return StorageValues.assembler().tempoMax();
            }

            @Override
            protected void onContentsChanged(int slot) {
                markChanged();
            }
        };
        lanes = new Lane[maxLanes()];
        for (int i = 0; i < lanes.length; i++) lanes[i] = new Lane();
    }

    public static boolean handles(BigMachine m) {
        return m.isAutocrafter();
    }

    public boolean quantum() {
        return machine == BigMachine.QUANTUM_COMPUTER;
    }

    public int maxLanes() {
        int own = machine.extra("max_lanes");
        return own > 0 ? own : StorageValues.assembler().maxLanes();
    }

    private void markChanged() {
        changed.run();
        touch();
    }

    private void touch() {
        if (server != null && bound) DriveStorage.get(server).setDirty();
    }

    private void bind(Level level, BigMachineBlockEntity be) {
        if (bound || !(level instanceof ServerLevel sl)) return;
        server = sl.getServer();
        selfPos = be.getBlockPos();
        ledgerKey = DriveStorage.assemblerKey(level, selfPos);
        CompoundTag t = DriveStorage.get(server).attach(ledgerKey, this);
        bound = true;
        if (t != null) restoreState(t, level.registryAccess());
        else if (legacy != null) restoreState(legacy, level.registryAccess());
        legacy = null;
        lastRefresh = Long.MIN_VALUE;
        cardsDirty = true;
    }

    public void bindNow(Level level, BigMachineBlockEntity be) {
        bind(level, be);
    }

    public void unbind(Level level) {
        if (!bound || server == null || ledgerKey == null) return;
        DriveStorage.get(server).detach(ledgerKey, this, level.registryAccess());
        bound = false;
        jobs.clear();
        for (Lane l : lanes) l.clear();
        pending = null;
        for (int i = 0; i < buffer.getSlots(); i++) buffer.setStackInSlot(i, ItemStack.EMPTY);
    }

    CompoundTag saveState(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        ListTag js = new ListTag();
        for (Job j : jobs) js.add(j.save(reg));
        t.put("jobs", js);
        ListTag ls = new ListTag();
        for (Lane l : lanes) ls.add(l.busy() ? l.save() : new CompoundTag());
        t.put("lanes", ls);
        t.put("buffer", buffer.serializeNBT(reg));
        t.putLong("seq", bookingSeq);
        if (pending != null) t.put("pending", pending.save(reg));
        t.putLongArray("forced", forced.stream().mapToLong(Long::longValue).toArray());
        return t;
    }

    private void restoreState(CompoundTag t, HolderLookup.Provider reg) {
        jobs.clear();
        for (Tag e : t.getList("jobs", Tag.TAG_COMPOUND)) {
            Job j = Job.load((CompoundTag) e, reg);
            if (j != null && j.step < j.left.length && jobById(j.id) == null) jobs.add(j);
            else if (j != null && server != null) DriveStorage.get(server).releaseJob(j.id);
        }
        ListTag ls = t.getList("lanes", Tag.TAG_COMPOUND);
        for (int i = 0; i < lanes.length; i++) {
            lanes[i].clear();
            lanes[i].cool = 0;
            lanes[i].state = IDLE;
            if (i < ls.size()) lanes[i].load(ls.getCompound(i));
        }
        if (t.contains("buffer")) {
            for (int i = 0; i < buffer.getSlots(); i++) buffer.setStackInSlot(i, ItemStack.EMPTY);
            buffer.deserializeNBT(reg, t.getCompound("buffer"));
        }
        bookingSeq = Math.max(bookingSeq, t.getLong("seq"));
        pending = t.contains("pending") ? Booking.load(t.getCompound("pending"), reg) : null;
        forced.clear();
        for (long c : t.getLongArray("forced")) forced.add(c);
        laneCap = Math.max(laneCap, 1);
    }

    static boolean stateHasJob(CompoundTag state, long job) {
        for (Tag e : state.getList("jobs", Tag.TAG_COMPOUND)) if (((CompoundTag) e).getLong("id") == job) return true;
        return state.contains("pending") && state.getCompound("pending").getLong("job") == job;
    }

    boolean hasJob(long job) {
        return jobById(job) != null || pending != null && pending.jobId == job;
    }

    public long bookingSeq() { return bookingSeq; }

    public boolean hasPendingBooking() { return pending != null; }

    private void releaseReservation(Job j) {
        if (server != null) DriveStorage.get(server).releaseJob(j.id);
    }

    private void removeJob(Job j) {
        if (jobs.remove(j)) releaseReservation(j);
    }

    public ItemStackHandler cards() { return cards; }
    public ItemStackHandler encode() { return encode; }
    public ItemStackHandler template() { return template; }
    public ItemStackHandler display() { return display; }
    public ItemStackHandler upgrades() { return upgrades; }
    public List<Job> jobs() { return jobs; }
    public int status() { return status; }
    public int previewKind() { return previewKind; }
    public int baseCycleTicks() { return Math.max(1, machine.extra("cycle_ticks")); }
    public int craftsPerCycle() { return Math.max(1, machine.extra("crafts_per_cycle")); }
    public int basePower() { return machine.extra("fe_per_tick"); }
    public int maxJobs() { return Math.max(1, machine.extra("max_jobs")); }

    public int tempoLevel() {
        ItemStack s = upgrades.getStackInSlot(0);
        if (quantum()) return 0;
        return s.is(StorageRegistry.ASSEMBLER_TEMPO.get()) ? Math.min(s.getCount(), StorageValues.assembler().tempoMax()) : 0;
    }

    public int cycleTicks() {
        return Math.max(1, baseCycleTicks() * 100 / StorageValues.assembler().speed(tempoLevel()));
    }

    public int powerUse() {
        return (int) Math.min(Integer.MAX_VALUE, (long) basePower() * StorageValues.assembler().power(tempoLevel()) / 100);
    }

    public int laneCap() { return laneCap; }

    public int busyLanes() {
        int n = 0;
        for (int i = 0; i < laneCap; i++) if (lanes[i].busy()) n++;
        return n;
    }

    public long sizeUsed() {
        long n = 0;
        for (Job j : jobs) n += j.size;
        return n;
    }

    public long sizeCap() { return sizeCap; }

    public int peers() { return peerCount; }

    public int stalledMinutes(long now) {
        long worst = 0;
        for (Job j : jobs) {
            if (j.lastProgress >= 0 && j.inflight == 0 && now - j.lastProgress >= StorageValues.assembler().stallTicks()) {
                worst = Math.max(worst, now - j.lastProgress);
            }
        }
        return (int) (worst / 1200);
    }

    public int progressPercent() {
        int best = 0;
        for (int i = 0; i < laneCap; i++) if (lanes[i].busy()) best = Math.max(best, lanes[i].progress);
        return Math.min(100, best * 100 / cycleTicks());
    }

    public List<Pattern> patterns() {
        List<Pattern> out = new ArrayList<>();
        for (int i = 0; i < CARDS; i++) {
            Pattern p = PatternCardItem.pattern(cards.getStackInSlot(i));
            if (p != null) out.add(p);
        }
        return out;
    }

    public List<Pattern> allPatterns() {
        List<Pattern> out = patterns();
        out.addAll(netOnline);
        return out;
    }

    public boolean hasPattern(ItemKey key) {
        for (Pattern p : allPatterns()) if (p.outputKey().equals(key)) return true;
        return false;
    }

    public boolean accepts() {
        return jobs.size() < maxJobs();
    }

    public boolean fits(int size) {
        return sizeUsed() + size <= sizeCap;
    }

    public void addJob(ItemStack target, int amount, List<CraftPlanner.Step> steps) {
        addJob(target, amount, steps, null, Map.of());
    }

    public boolean addJob(ItemStack target, int amount, List<CraftPlanner.Step> steps, @Nullable StorageAccess storage, Map<ItemKey, Long> reserve) {
        List<Pattern> ps = new ArrayList<>();
        int[] left = new int[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            ps.add(steps.get(i).pattern());
            left[i] = steps.get(i).crafts();
        }
        Job job = new Job(newId(), target, amount, Math.max(1, Job.sum(left)), ps, left, 0);
        if (storage != null && !reserve.isEmpty()) {
            for (var e : reserve.entrySet()) {
                if (storage.reserve(job.id, e.getKey(), e.getValue()) < e.getValue()) {
                    if (server != null) DriveStorage.get(server).releaseJob(job.id);
                    else for (var r : reserve.keySet()) storage.reserve(job.id, r, 0);
                    return false;
                }
            }
        }
        jobs.add(job);
        markChanged();
        return true;
    }

    public void cancel(boolean all) {
        if (jobs.isEmpty()) return;
        if (all) {
            for (Job j : new ArrayList<>(jobs)) removeJob(j);
        } else {
            removeJob(jobs.get(0));
        }
        markChanged();
    }

    public int abortOrphanedJobs(Level level, BigMachineBlockEntity be) {
        List<Pattern> available = new ArrayList<>();
        List<ComponentAssembler> all = new ArrayList<>();
        all.add(this);
        for (BigMachineBlockEntity p : peers) {
            ComponentAssembler o = p.assembler();
            if (o != null && o != this && !p.isRemoved() && p.getLevel() == level) all.add(o);
        }
        for (ComponentAssembler o : all) available.addAll(o.patterns());
        available.addAll(netKnown);
        int n = 0;
        for (ComponentAssembler o : all) n += o.abortJobsWithout(level, available);
        if (n > 0 && bufferHasItemsNow()) flushBuffer(level, be);
        return n;
    }

    private int abortJobsWithout(Level level, List<Pattern> available) {
        int n = 0;
        for (Job j : new ArrayList<>(jobs)) {
            boolean ok = true;
            for (int i = j.step; i < j.patterns.size() && ok; i++) ok = available.contains(j.patterns.get(i));
            if (ok) continue;
            removeJob(j);
            for (Lane l : lanes) {
                if (l.jobId == j.id && l.owner != null && l.owner.equals(selfPos)) {
                    l.clear();
                    l.state = IDLE;
                }
            }
            n++;
        }
        if (n > 0) markChanged();
        return n;
    }

    private boolean bufferHasItemsNow() {
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    public void updatePreview(Level level) {
        PatternRecipes.Match m = PatternRecipes.match(level, gridStacks());
        display.setStackInSlot(0, m == null ? ItemStack.EMPTY : m.pattern().output().item().copyWithCount(m.pattern().output().count()));
        previewKind = m == null ? PatternRecipes.NONE : m.kind();
    }

    private List<ItemStack> gridStacks() {
        List<ItemStack> grid = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) grid.add(template.getStackInSlot(i));
        return grid;
    }

    public boolean writeCard(Level level) {
        ItemStack blank = encode.getStackInSlot(0);
        if (!PatternCardItem.isBlank(blank) || !encode.getStackInSlot(1).isEmpty()) return false;
        PatternRecipes.Match m = PatternRecipes.match(level, gridStacks());
        if (m == null) return false;
        encode.setStackInSlot(1, PatternCardItem.write(blank, m.pattern()));
        ItemStack rest = blank.copy();
        rest.shrink(1);
        encode.setStackInSlot(0, rest);
        return true;
    }

    private BlockPos selfPos = BlockPos.ZERO;

    public int tick(Level level, BigMachineBlockEntity be, boolean formed, boolean enabled) {
        selfPos = be.getBlockPos();
        bind(level, be);
        Job first = jobs.isEmpty() ? null : jobs.get(0);
        display.setStackInSlot(1, first == null ? ItemStack.EMPTY : first.target.copyWithCount(Math.max(1, Math.min(first.amount, 99))));
        if (!formed || !enabled) {
            status = OFF;
            for (Lane l : lanes) {
                if (l.busy()) releaseClaim(level, l);
                l.clear();
                l.cool = 0;
                l.state = OFF;
            }
            return 0;
        }
        long now = level.getGameTime();
        for (Job j : jobs) if (j.lastProgress < 0) j.lastProgress = now;
        if (lastRefresh == Long.MIN_VALUE || now - lastRefresh >= REFRESH_TICKS || now < lastRefresh) refresh(level, be);
        if (cardsDirty && chunkOk) {
            cardsDirty = false;
            abortOrphanedJobs(level, be);
        }
        if (pending != null) recover(level, be);
        int working = 0;
        int worst = IDLE;
        for (int i = 0; i < lanes.length; i++) {
            Lane l = lanes[i];
            if (i >= laneCap) {
                if (l.busy()) releaseClaim(level, l);
                l.clear();
                l.state = IDLE;
                continue;
            }
            if (l.cool > 0) {
                l.cool--;
                worst = Math.max(worst, rank(l.state));
                continue;
            }
            laneTick(level, be, l);
            if (l.state == WORKING) working++;
            worst = Math.max(worst, rank(l.state));
        }
        if (working > 0) status = WORKING;
        else status = unrank(worst);
        if (jobs.isEmpty() && working == 0 && pending == null) status = IDLE;
        if (working > 0 || !jobs.isEmpty()) touch();
        return working;
    }

    private static int rank(int state) {
        return switch (state) {
            case NO_POWER -> 5;
            case CHUNK -> 8;
            case BLOCKED -> 4;
            case NO_STORAGE -> 3;
            case WAITING -> 2;
            case WORKING -> 6;
            default -> 0;
        };
    }

    private static int unrank(int r) {
        return switch (r) {
            case 6 -> WORKING;
            case 8 -> CHUNK;
            case 5 -> NO_POWER;
            case 4 -> BLOCKED;
            case 3 -> NO_STORAGE;
            case 2 -> WAITING;
            default -> IDLE;
        };
    }

    private void refresh(Level level, BigMachineBlockEntity be) {
        lastRefresh = level.getGameTime();
        List<BigMachineBlockEntity> found = new ArrayList<>(StorageNet.assemblers(level, be.dataStarts()));
        found.removeIf(b -> b.machine() != machine);
        if (!found.contains(be)) found.add(be);
        found.sort(java.util.Comparator.comparingLong(b -> b.getBlockPos().asLong()));
        peers = found;
        peerCount = Math.max(1, found.size());
        StorageValues.Assembler v = StorageValues.assembler();
        if (!com.sqc.nuclearsq.data.ComputeNet.required()) {
            laneCap = maxLanes();
            sizeCap = Long.MAX_VALUE / 4;
        } else if (quantum()) {
            boolean served = com.sqc.nuclearsq.data.ComputeNet.status(level, be.dataStarts(), be.getBlockPos()).served();
            laneCap = served ? maxLanes() : 0;
            sizeCap = Long.MAX_VALUE / 4;
        } else {
            var share = com.sqc.nuclearsq.data.ComputeNet.share(level, be);
            laneCap = Math.min(maxLanes(), v.baseLanes() + share.cpu());
            sizeCap = (long) v.baseSize() + (long) share.ram() * v.craftsPerGb() / StorageValues.MB_PER_GB;
        }
        accessCache = null;
        access(level, be);
        StorageNet.Patterns np = StorageNet.patterns(level, be.dataStarts());
        if (np.complete()) {
            if (!np.known().equals(netKnown)) cardsDirty = true;
            netKnown = np.known();
            netOnline = np.online();
        }
        if (chunkOk && (cardsDirty || peerCount > 1)) {
            abortOrphanedJobs(level, be);
            cardsDirty = false;
        }
        keepLoaded(level, be);
        flushBuffer(level, be);
    }

    private void keepLoaded(Level level, BigMachineBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        java.util.Set<Long> want = new java.util.HashSet<>();
        if (com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.ASSEMBLER_KEEP_LOADED)) {
            want.addAll(ChunkTickets.chunksOf(be.cellPositions()));
            want.addAll(netChunks);
            int max = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.ASSEMBLER_MAX_CHUNKS);
            if (want.size() > max) {
                java.util.List<Long> keep = new ArrayList<>(want);
                keep.sort(java.util.Comparator.comparingDouble(c -> ChunkTickets.distSq(c, be.getBlockPos())));
                want = new java.util.HashSet<>(keep.subList(0, max));
            }
        }
        if (ChunkTickets.sync(sl, be.getBlockPos(), forced, want)) markChanged();
    }

    public java.util.Set<Long> forcedChunks() { return java.util.Collections.unmodifiableSet(forced); }

    private void releaseTickets(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) ChunkTickets.sync(sl, pos, forced, java.util.Set.of());
    }

    public void refreshNow(Level level, BigMachineBlockEntity be) {
        selfPos = be.getBlockPos();
        refresh(level, be);
    }

    private void flushBuffer(Level level, BigMachineBlockEntity be) {
        bufferHasItems = false;
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) bufferHasItems = true;
        if (!bufferHasItems) return;
        StorageAccess a = StorageNet.access(level, be.dataStarts());
        if (a.onlineRacks() == 0) return;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;
            ItemStack rest = a.insert(s, false);
            if (rest.getCount() != s.getCount()) {
                buffer.setStackInSlot(i, rest);
                markChanged();
            }
        }
        bufferHasItems = false;
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) bufferHasItems = true;
    }

    private StorageAccess access(Level level, BigMachineBlockEntity be) {
        long now = level.getGameTime();
        if (accessCache == null || accessTick != now) {
            accessCache = StorageNet.access(level, be.dataStarts());
            accessTick = now;
            chunkOk = accessCache.complete() && ChunkGuard.allLoaded(level, be.cellPositions());
            netChunks = accessCache.chunks();
        }
        return accessCache;
    }

    private java.util.Set<Long> netChunks = java.util.Set.of();
    private StorageAccess accessCache;
    private long accessTick = Long.MIN_VALUE;

    private void laneTick(Level level, BigMachineBlockEntity be, Lane l) {
        if (!l.busy()) {
            StorageAccess a = access(level, be);
            if (!chunkOk) {
                l.state = CHUNK;
                l.cool = 10;
                return;
            }
            if (a.onlineRacks() == 0) {
                l.state = NO_STORAGE;
                l.cool = 10;
                return;
            }
            if (!claim(level, be, l, a)) {
                l.state = jobs.isEmpty() && !anyPeerWork() ? IDLE : WAITING;
                l.cool = 10;
                return;
            }
        }
        Job j = jobOf(level, l);
        if (j == null) {
            if (ownerGone(level, l)) {
                l.clear();
                l.state = IDLE;
            } else {
                l.state = CHUNK;
                l.cool = 20;
            }
            return;
        }
        ComponentAssembler owner = ownerOf(level, l);
        if (!l.registered) {
            if (j.step != l.step || j.available() < l.batch) {
                l.clear();
                l.state = IDLE;
                return;
            }
            j.inflight += l.batch;
            l.registered = true;
        }
        if (j.step != l.step) {
            releaseClaim(level, l);
            l.clear();
            return;
        }
        j.beat = level.getGameTime();
        if (!chunkOk) {
            l.state = CHUNK;
            return;
        }
        int power = powerUse();
        if (l.progress < cycleTicks() && energy.getEnergyStored() < power) {
            l.state = NO_POWER;
            return;
        }
        if (l.progress < cycleTicks()) {
            energy.drain(power);
            l.progress++;
            l.state = WORKING;
            if (l.progress < cycleTicks()) return;
        }
        finish(level, be, l, owner, j, access(level, be));
    }

    private boolean anyPeerWork() {
        for (BigMachineBlockEntity p : peers) {
            ComponentAssembler o = p.assembler();
            if (o != null && !o.jobs.isEmpty()) return true;
        }
        return false;
    }

    @javax.annotation.Nullable
    private ComponentAssembler ownerOf(Level level, Lane l) {
        if (l.owner == null) return null;
        if (l.owner.equals(selfPos)) return this;
        if (!ChunkGuard.loaded(level, l.owner)) return null;
        return level.getBlockEntity(l.owner) instanceof BigMachineBlockEntity b && !b.isRemoved() ? b.assembler() : null;
    }

    private boolean ownerGone(Level level, Lane l) {
        if (l.owner == null) return true;
        ComponentAssembler o = ownerOf(level, l);
        if (o == null) return ChunkGuard.loaded(level, l.owner);
        return o.jobById(l.jobId) == null;
    }

    @javax.annotation.Nullable
    private Job jobOf(Level level, Lane l) {
        ComponentAssembler o = ownerOf(level, l);
        return o == null ? null : o.jobById(l.jobId);
    }

    @javax.annotation.Nullable
    Job jobById(long id) {
        for (Job j : jobs) if (j.id == id) return j;
        return null;
    }

    private void releaseClaim(Level level, Lane l) {
        if (!l.registered) return;
        Job j = jobOf(level, l);
        if (j != null && j.step == l.step) j.inflight = Math.max(0, j.inflight - l.batch);
        l.registered = false;
    }

    private boolean claim(Level level, BigMachineBlockEntity be, Lane l, StorageAccess a) {
        if (claimFrom(this, be.getBlockPos(), l, a, level)) return true;
        for (BigMachineBlockEntity p : peers) {
            if (p == be || p.isRemoved() || p.getLevel() != level || !p.isFormed()) continue;
            ComponentAssembler o = p.assembler();
            if (o != null && claimFrom(o, p.getBlockPos(), l, a, level)) return true;
        }
        return false;
    }

    private boolean claimFrom(ComponentAssembler owner, BlockPos ownerPos, Lane l, StorageAccess a, Level level) {
        for (Job j : owner.jobs) {
            if (j.inflight > 0 && level.getGameTime() - j.beat > STALE_CLAIM_TICKS) j.inflight = 0;
            int avail = j.available();
            if (avail <= 0) continue;
            int k = possible(a, j.current(), Math.min(avail, craftsPerCycle()), j.id);
            if (k <= 0) continue;
            j.inflight += k;
            j.lastProgress = level.getGameTime();
            j.beat = j.lastProgress;
            l.owner = ownerPos.immutable();
            l.jobId = j.id;
            l.step = j.step;
            l.batch = k;
            l.progress = 0;
            l.registered = true;
            return true;
        }
        return false;
    }

    private static int possible(StorageAccess a, Pattern p, int max, long job) {
        long k = max;
        for (Pattern.Part in : p.inputs()) k = Math.min(k, a.countFor(in.key(), job) / in.count());
        return (int) Math.max(0, k);
    }

    private static java.util.Map<ItemKey, Long> outputs(Pattern p, int k) {
        java.util.Map<ItemKey, Long> out = new java.util.LinkedHashMap<>();
        out.merge(p.outputKey(), (long) p.output().count() * k, Long::sum);
        for (Pattern.Part r : p.remainders()) out.merge(r.key(), (long) r.count() * k, Long::sum);
        return out;
    }

    private boolean bufferFits(java.util.Map<ItemKey, Long> outs) {
        ItemStackHandler copy = new ItemStackHandler(buffer.getSlots());
        for (int i = 0; i < buffer.getSlots(); i++) copy.setStackInSlot(i, buffer.getStackInSlot(i).copy());
        for (var e : outs.entrySet()) {
            long rest = e.getValue();
            while (rest > 0) {
                ItemStack s = e.getKey().stack((int) Math.min(rest, e.getKey().maxStack()));
                rest -= s.getCount();
                if (!ItemHandlerHelper.insertItem(copy, s, false).isEmpty()) return false;
            }
        }
        return true;
    }

    private boolean hasRoom(StorageAccess a, java.util.Map<ItemKey, Long> outs) {
        if (bufferFits(outs)) return true;
        for (var e : outs.entrySet()) if (a.insert(e.getKey(), e.getValue(), true) != e.getValue()) return false;
        return true;
    }

    private void finish(Level level, BigMachineBlockEntity be, Lane l, ComponentAssembler owner, Job j, StorageAccess a) {
        if (owner == null) return;
        if (!chunkOk) {
            l.state = CHUNK;
            l.cool = 10;
            l.progress = cycleTicks();
            return;
        }
        try {
            book(level, be, l, owner, j, a);
        } catch (RuntimeException e) {
            LOG.error("Bauteilassembler {}: Buchung {} abgebrochen, wird wiederhergestellt", selfPos, bookingSeq, e);
            l.state = WAITING;
            l.cool = 5;
        }
    }

    private void book(Level level, BigMachineBlockEntity be, Lane l, ComponentAssembler owner, Job j, StorageAccess a) {
        Pattern p = j.current();
        int want = Math.min(l.batch, Math.max(0, j.left[j.step]));
        int k = possible(a, p, want, j.id);
        if (k <= 0) {
            releaseClaim(level, l);
            l.clear();
            l.state = WAITING;
            l.cool = 10;
            return;
        }
        java.util.Map<ItemKey, Long> outs = outputs(p, k);
        if (!hasRoom(a, outs)) {
            l.state = BLOCKED;
            l.cool = 10;
            l.progress = cycleTicks();
            if (bufferHasItems) flushBuffer(level, be);
            return;
        }
        Booking b = new Booking();
        b.seq = ++bookingSeq;
        b.jobId = j.id;
        b.step = j.step;
        b.k = k;
        b.phase = 1;
        b.owner = l.owner;
        b.outs.putAll(outs);
        pending = b;
        boolean ok = true;
        for (Pattern.Part in : p.inputs()) {
            long need = (long) in.count() * k;
            long got = a.extract(in.key(), need, false, j.id);
            if (got > 0) b.taken.merge(in.key(), got, Long::sum);
            if (got != need) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            for (var e : b.taken.entrySet()) store(level, be.getBlockPos(), a, e.getKey(), e.getValue());
            pending = null;
            releaseClaim(level, l);
            l.clear();
            l.state = WAITING;
            l.cool = 10;
            return;
        }
        if (testFault == 1) {
            testFault = 0;
            throw new IllegalStateException("Testfehler nach dem Entnehmen");
        }
        for (var e : outs.entrySet()) store(level, be.getBlockPos(), a, e.getKey(), e.getValue());
        b.phase = 2;
        if (testFault == 2) {
            testFault = 0;
            throw new IllegalStateException("Testfehler nach dem Ablegen");
        }
        j.inflight = Math.max(0, j.inflight - l.batch);
        l.registered = false;
        j.lastProgress = level.getGameTime();
        if (j.done(k)) owner.removeJob(j);
        pending = null;
        owner.markChanged();
        l.clear();
        l.state = WORKING;
        markChanged();
    }

    private void recover(Level level, BigMachineBlockEntity be) {
        Booking b = pending;
        if (b == null) return;
        StorageAccess a = StorageNet.access(level, be.dataStarts());
        if (b.phase <= 1) {
            if (!a.complete()) return;
            for (var e : b.taken.entrySet()) store(level, be.getBlockPos(), a, e.getKey(), e.getValue());
            LOG.warn("Bauteilassembler {}: Buchung {} zurueckgerollt ({} Sorten zurueck in die Racks)", selfPos, b.seq, b.taken.size());
        } else {
            ComponentAssembler owner = b.owner.equals(selfPos) ? this : ownerAt(level, b.owner);
            if (owner == null) {
                if (!ChunkGuard.loaded(level, b.owner)) return;
            } else {
                Job j = owner.jobById(b.jobId);
                if (j != null && j.step == b.step) {
                    for (Lane l : lanes) {
                        if (l.busy() && l.jobId == b.jobId && l.step == b.step) {
                            if (l.registered) j.inflight = Math.max(0, j.inflight - l.batch);
                            l.clear();
                        }
                    }
                    if (j.done(b.k)) owner.removeJob(j);
                    owner.markChanged();
                }
            }
            LOG.warn("Bauteilassembler {}: Buchung {} abgeschlossen (Ergebnis lag schon in den Racks)", selfPos, b.seq);
        }
        pending = null;
        markChanged();
    }

    @Nullable
    private ComponentAssembler ownerAt(Level level, BlockPos pos) {
        if (!ChunkGuard.loaded(level, pos)) return null;
        return level.getBlockEntity(pos) instanceof BigMachineBlockEntity b && !b.isRemoved() ? b.assembler() : null;
    }

    private void store(Level level, BlockPos pos, StorageAccess a, ItemKey key, long n) {
        long rest = n - a.insert(key, n, false);
        while (rest > 0) {
            ItemStack s = key.stack((int) Math.min(rest, key.maxStack()));
            rest -= s.getCount();
            ItemStack left = ItemHandlerHelper.insertItem(buffer, s, false);
            if (!left.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, left);
        }
    }

    public void returnBuffer(Level level, BigMachineBlockEntity be) {
        if (level.isClientSide) return;
        releaseTickets(level, be.getBlockPos());
        if (!be.isFormed()) return;
        flushBuffer(level, be);
    }

    public void discard(Level level) {
        if (level.isClientSide || (!bound && jobs.isEmpty())) return;
        if (server != null) for (Job j : jobs) DriveStorage.get(server).releaseJob(j.id);
        releaseTickets(level, selfPos);
        forget();
    }

    public net.neoforged.neoforge.items.IItemHandler input(BigMachineBlockEntity be) {
        return new net.neoforged.neoforge.items.IItemHandler() {
            @Override public int getSlots() { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return be.isFormed(); }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                Level level = be.getLevel();
                if (stack.isEmpty() || level == null || level.isClientSide || !be.isFormed()) return stack;
                return StorageNet.access(level, be.dataStarts()).insert(stack, simulate);
            }
        };
    }

    public CompoundTag save(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.put("cards", cards.serializeNBT(reg));
        t.put("encode", encode.serializeNBT(reg));
        t.put("template", template.serializeNBT(reg));
        t.put("upgrades", upgrades.serializeNBT(reg));
        return t;
    }

    public void load(CompoundTag t, HolderLookup.Provider reg) {
        if (t.contains("cards")) cards.deserializeNBT(reg, t.getCompound("cards"));
        if (t.contains("encode")) encode.deserializeNBT(reg, t.getCompound("encode"));
        if (t.contains("template")) template.deserializeNBT(reg, t.getCompound("template"));
        if (t.contains("upgrades")) upgrades.deserializeNBT(reg, t.getCompound("upgrades"));
        legacy = t.contains("jobs") ? t.copy() : null;
        if (!bound) {
            jobs.clear();
            for (Lane l : lanes) l.clear();
        }
        lastRefresh = Long.MIN_VALUE;
        laneCap = Math.max(laneCap, 1);
        cardsDirty = true;
    }

    private void forget() {
        if (server != null && ledgerKey != null) DriveStorage.get(server).forget(ledgerKey, this);
        bound = false;
    }

    public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < CARDS; i++) out.add(cards.getStackInSlot(i).copy());
        for (int i = 0; i < 2; i++) out.add(encode.getStackInSlot(i).copy());
        out.add(upgrades.getStackInSlot(0).copy());
        for (int i = 0; i < CARDS; i++) cards.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < 2; i++) encode.setStackInSlot(i, ItemStack.EMPTY);
        upgrades.setStackInSlot(0, ItemStack.EMPTY);
        for (Job j : new ArrayList<>(jobs)) removeJob(j);
        for (Lane l : lanes) l.clear();
        pending = null;
        forget();
        return out;
    }
}
