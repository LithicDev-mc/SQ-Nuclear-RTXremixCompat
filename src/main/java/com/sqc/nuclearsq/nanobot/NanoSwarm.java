package com.sqc.nuclearsq.nanobot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NanoSwarm {
    public static final int BOTS = 10;

    public enum Phase { SEARCH, TRAVEL, WORK, DELIVER, LEAVE }

    private static int scanBudget() {
        return NuclearConfig.value(NuclearConfig.NANOBOT_SCAN_PER_TICK);
    }

    private static int[] scanStages(int radius) {
        List<Integer> out = new ArrayList<>();
        for (int r : new int[] {6, 12}) if (r < radius) out.add(r);
        out.add(radius);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
    private static final int MAX_LOGS = 160, MAX_VEIN = 48, MAX_STONE = 32, TREE_SPREAD = 8, MAX_LEAVES = 400;
    private static final int TRAVEL_TIMEOUT = 20 * 25;
    private static int cargoLimit() {
        return NuclearConfig.value(NuclearConfig.NANOBOT_CARGO_LIMIT);
    }

    private final UUID owner;
    private final ServerLevel level;
    private NanoTask task;
    private Phase phase = Phase.SEARCH;
    private Vec3 center;
    private final int botCount;
    private NanoSettings settings;
    private final List<ItemStack> cargo = new ArrayList<>();
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<Long> skip = new HashSet<>();
    private final Deque<BlockPos> pointed = new ArrayDeque<>();
    private NanoTask base;
    private boolean pointOnly, pointAny;
    private final java.util.Map<Long, List<BlockPos>> areas = new java.util.HashMap<>();
    private final Set<UUID> skipItems = new HashSet<>();
    @Nullable private UUID itemId;
    @Nullable private BlockPos workPos;
    @Nullable private Scan scan;
    private int scanStage, scanStartAge;
    private int firstTravelAge = -1, firstWorkAge = -1;
    private boolean startLogged;
    private final int[] diag = new int[4];
    private int age, idle, phaseTicks;
    private boolean announced;
    private int lastReject = -1000;
    private boolean ended;
    private final Job[] jobs;
    private final BlockPos[] lastJobPos;
    private final net.minecraft.util.RandomSource[] botRng;
    private final java.util.Map<Long, Integer> treeOf = new java.util.HashMap<>();
    private final int[] botTree;
    private int nextTree;
    @Nullable private BlockPos treeAnchor;
    @Nullable private Scan xscan;
    private boolean xscanDone;
    @Nullable private BlockPos stepPos;
    private int stepBot, stepStage;
    private boolean plantQueued;
    @Nullable private Vec3 stay;
    private final int breakId;

    private static final class Job {
        final BlockPos pos;
        final int total;
        final boolean breaking;
        int delay, done, stage = -1;

        Job(BlockPos pos, int total, int delay, boolean breaking) {
            this.pos = pos;
            this.total = total;
            this.delay = delay;
            this.breaking = breaking;
        }
    }

    public NanoSwarm(ServerPlayer owner, NanoTask task) {
        this.owner = owner.getUUID();
        this.breakId = -(owner.getId() + 1);
        this.level = owner.serverLevel();
        this.settings = NanoSettings.forPlayer(owner);
        this.botCount = settings.bots();
        this.jobs = new Job[botCount];
        this.lastJobPos = new BlockPos[botCount];
        this.botRng = new net.minecraft.util.RandomSource[botCount];
        for (int i = 0; i < botCount; i++) this.botRng[i] = net.minecraft.util.RandomSource.create(owner.getUUID().getMostSignificantBits() ^ (0x9E3779B97F4A7C15L * (i + 1)));
        this.botTree = new int[botCount];
        java.util.Arrays.fill(this.botTree, -1);
        this.task = task;
        this.center = hover(owner);
        NuclearSQ.LOGGER.info("[nanobots] Schwarm erstellt: Aufgabe {}, Besitzer {}, Exo-Kern im Inventar: {}", task, owner.getName().getString(), NanoPower.hasCore(owner));
    }

    public void refreshSettings(ServerPlayer sp) {
        settings = NanoSettings.forPlayer(sp);
    }

    public int botCount() {
        return botCount;
    }

    public int workProgress() {
        int best = -1;
        for (Job j : jobs) {
            if (j != null) best = Math.max(best, progressOf(j));
        }
        return best;
    }

    private static int progressOf(Job j) {
        return j.delay > 0 ? 0 : Math.min(100, 100 * j.done / Math.max(1, j.total));
    }

    public List<BlockPos> activeTargets() {
        List<BlockPos> out = new ArrayList<>();
        for (Job j : jobs) {
            if (j != null) out.add(j.pos);
        }
        return out;
    }

    private boolean hasActiveJobs() {
        for (Job j : jobs) {
            if (j != null) return true;
        }
        return false;
    }

    public Vec3 center() {
        return center;
    }

    public int age() {
        return age;
    }

    public UUID owner() {
        return owner;
    }

    public NanoTask task() {
        return task;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isEnded() {
        return ended;
    }

    public int cargoStacks() {
        return cargo.size();
    }

    public void point(BlockPos pos) {
        if (!pointed.contains(pos)) pointed.add(pos.immutable());
    }

    public void pointArea(BlockPos pos, List<BlockPos> area) {
        areas.put(pos.asLong(), area);
        if (!pointed.contains(pos)) pointed.add(pos.immutable());
    }

    public void markPointOnly() {
        pointOnly = true;
    }

    public void setTask(NanoTask t) {
        if (t == task) return;
        task = t;
        resetTarget();
        if (phase != Phase.LEAVE) setPhase(Phase.SEARCH, "Aufgabenwechsel");
    }

    public void recall() {
        clearJobs(false);
        NuclearSQ.LOGGER.info("[nanobots] {}: Rueckruf in Phase {} (Tick {})", task, phase, age);
        resetTarget();
        setPhase(Phase.LEAVE, "Rueckruf");
    }

    private int leaveEta = 20;

    private void resetTarget() {
        clearJobs(false);
        queue.clear();
        treeOf.clear();
        java.util.Arrays.fill(botTree, -1);
        nextTree = 0;
        treeAnchor = null;
        xscan = null;
        xscanDone = false;
        plantQueued = false;
        itemId = null;
        workPos = null;
        scan = null;
        idle = 0;
        announced = false;
    }

    private void setPhase(Phase p, String why) {
        if (p != phase) NuclearSQ.LOGGER.info("[nanobots] {}: Phase {} -> {} (Tick {}, Fracht {} Stapel, Grund: {})", task, phase, p, age, cargo.size(), why);
        if (p == Phase.DELIVER || p == Phase.LEAVE) stay = null;
        if (p == Phase.TRAVEL && firstTravelAge < 0) firstTravelAge = age;
        if (p == Phase.WORK && firstWorkAge < 0) firstWorkAge = age;
        phase = p;
        phaseTicks = 0;
        if (p != Phase.WORK) clearJobs(true);
        else java.util.Arrays.fill(lastJobPos, null);
    }

    private static Vec3 hover(ServerPlayer sp) {
        return sp.position().add(0, sp.getBbHeight() * 0.8, 0);
    }

    public boolean tick(ServerPlayer sp) {
        if (ended) return false;
        age++;
        phaseTicks++;
        if (!sp.isAlive() || sp.level() != level) {
            NuclearSQ.LOGGER.info("[nanobots] {}: Besitzer tot oder in anderer Dimension", task);
            finish(sp);
            return false;
        }
        if (phase != Phase.LEAVE) {
            if (age % 10 == 0 && !NanoPower.hasCore(sp)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.reactor_lost"), true);
                recall();
            } else if (age % 10 == 0 && !NanoPower.hasPower(sp)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.reactor_empty"), true);
                recall();
            } else if (!NuclearConfig.value(NuclearConfig.NANOBOT_ENABLED)) {
                recall();
            }
        }
        switch (phase) {
            case SEARCH -> tickSearch(sp);
            case TRAVEL -> tickTravel();
            case WORK -> tickWork(sp);
            case DELIVER -> tickDeliver(sp);
            case LEAVE -> {
                if (phaseTicks == 1) leaveEta = (int) Mth.clamp(Math.sqrt(center.distanceToSqr(hover(sp))) / 0.25, 20, 400);
                int stagger = NuclearConfig.value(NuclearConfig.NANOBOT_ENTRY_STAGGER);
                if (phaseTicks >= leaveEta + BotEntry.TOTAL + stagger * botCount) {
                    finish(sp, true);
                    return false;
                }
            }
        }
        move(sp);
        if (age % 2 == 0) send(sp);
        return true;
    }

    public void finish(ServerPlayer sp) {
        finish(sp, false);
    }

    public void finish(ServerPlayer sp, boolean graceful) {
        if (ended) return;
        ended = true;
        clearJobs(false);
        NuclearSQ.LOGGER.info("[nanobots] {}: Schwarm beendet nach {} Ticks, Fracht {} Stapel", task, age, cargo.size());
        deliver(sp);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, graceful ? NanoSwarmPayload.goneAfterEntry(owner) : NanoSwarmPayload.gone(owner));
    }

    private void tickSearch(ServerPlayer sp) {
        if (idle > 0) {
            idle--;
            return;
        }
        if (!pointed.isEmpty()) {
            if (base == null) base = task;
            BlockPos hit = pointed.poll();
            BlockState st = level.getBlockState(hit);
            List<BlockPos> area = areas.remove(hit.asLong());
            pointAny = area != null || !(st.is(BlockTags.LOGS) || st.is(Tags.Blocks.ORES) || isStone(st));
            task = st.is(BlockTags.LOGS) && !pointAny ? NanoTask.FELL_TREE : st.is(Tags.Blocks.ORES) && !pointAny ? NanoTask.MINE_ORE : NanoTask.MINE_STONE;
            resetTarget();
            if ((task == NanoTask.MINE_ORE || task == NanoTask.MINE_STONE) && toolFor(sp, st) == null) {
                announceOnce(sp, "message.nuclearsq.nanobots.no_pickaxe");
                idle = 100;
                return;
            }
            boolean ok = pointAny ? planAny(hit, area) : switch (task) {
                case FELL_TREE -> planTree(hit, true);
                case MINE_ORE -> planVein(hit);
                case MINE_STONE -> planStone(hit, sp);
                default -> false;
            };
            if (ok) {
                announced = false;
                setPhase(Phase.TRAVEL, "Zeigen");
            } else skip.add(hit.asLong());
            return;
        }
        if (base != null) {
            task = base;
            base = null;
            pointAny = false;
            return;
        }
        if (pointOnly) {
            stay = null;
            return;
        }
        if (task == NanoTask.PLANT_SAPLINGS && findSapling(sp) == null) {
            if (!announced) NuclearSQ.LOGGER.info("[nanobots] Pflanzen: findSapling liefert null, Inventar hat keine Setzlinge");
            announceOnce(sp, "message.nuclearsq.nanobots.no_saplings");
            idle = 100;
            return;
        }
        if ((task == NanoTask.MINE_STONE || task == NanoTask.MINE_ORE)
                && toolFor(sp, (task == NanoTask.MINE_STONE ? Blocks.STONE : Blocks.IRON_ORE).defaultBlockState()) == null) {
            if (!announced) NuclearSQ.LOGGER.info("[nanobots] {}: keine passende Spitzhacke im Inventar", task);
            announceOnce(sp, "message.nuclearsq.nanobots.no_pickaxe");
            idle = 100;
            return;
        }
        if (task == NanoTask.TILL && findHoe(sp) == null) {
            if (!announced) NuclearSQ.LOGGER.info("[nanobots] Umgraben: keine Hacke im Inventar");
            announceOnce(sp, "message.nuclearsq.nanobots.no_hoe");
            idle = 100;
            return;
        }
        if (task.gadget() && NanoGadget.order(sp) == null) {
            recall();
            return;
        }
        int r = search(sp);
        if (r > 0) {
            announced = false;
            setPhase(Phase.TRAVEL, "Ziel gefunden");
        } else if (r < 0) {
            scan = null;
            idle = task == NanoTask.COLLECT_ITEMS ? 20 : 60;
            if (!cargo.isEmpty()) setPhase(Phase.DELIVER, "kein weiteres Ziel in Reichweite, Fracht abliefern");
            else announceOnce(sp, "message.nuclearsq.nanobots.nothing_found");
        }
    }

    private void announceOnce(ServerPlayer sp, String key) {
        if (announced) return;
        announced = true;
        sp.displayClientMessage(Component.translatable(key, task.label()), true);
    }

    private int search(ServerPlayer sp) {
        if (task.gadget()) return planGadget(sp);
        int radius = task.near() ? settings.nearRadius() : settings.radius();
        if (task == NanoTask.COLLECT_ITEMS) {
            AABB box = new AABB(sp.blockPosition()).inflate(radius);
            ItemEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
                if (!e.isAlive() || e.hasPickUpDelay() || skipItems.contains(e.getUUID()) || e.distanceToSqr(sp) < 9) continue;
                double d = e.distanceToSqr(center);
                if (d < bestD && mayPickUp(sp, e)) {
                    bestD = d;
                    best = e;
                }
            }
            if (best == null) return -1;
            itemId = best.getUUID();
            workPos = best.blockPosition();
            return 1;
        }
        int[] stages = scanStages(radius);
        scanStage = Math.min(scanStage, stages.length - 1);
        if (scan == null) {
            if (scanStage == 0) {
                java.util.Arrays.fill(diag, 0);
                scanStartAge = age;
            }
            scan = new Scan(sp.blockPosition(), stages[scanStage], Math.min(stages[scanStage], 16));
        }
        Predicate<BlockPos> test = switch (task) {
            case FELL_TREE -> this::isTreeCandidate;
            case MINE_ORE -> p -> isMinableOre(sp, p);
            case HARVEST -> this::isRipeCrop;
            case TILL -> tillTest(sp);
            case MINE_STONE -> stoneTest(sp);
            case DECONTAMINATE -> this::isContaminated;
            default -> plantTest(sp);
        };
        int feetY = sp.blockPosition().getY();
        if (!scan.step(scanBudget(), level, test, task == NanoTask.MINE_STONE ? p -> p.getY() < feetY ? 64.0 : 0.0 : null)) return 0;
        BlockPos hit = scan.best;
        scan = null;
        if (hit == null && scanStage < stages.length - 1) {
            scanStage++;
            return 0;
        }
        scanStage = 0;
        if (hit == null) {
            NuclearSQ.LOGGER.info("[nanobots] Suche {} um {}: kein Ziel im Umkreis {} (Filter: passender Block {}, weiter {}, dann {}, Rest {})",
                    task, sp.blockPosition(), radius, diag[0], diag[1], diag[2], diag[3]);
            return -1;
        }
        NuclearSQ.LOGGER.info("[nanobots] Suche {}: Treffer bei {}", task, hit);
        boolean ok = switch (task) {
            case FELL_TREE -> planTree(hit, true);
            case MINE_ORE -> planVein(hit);
            case MINE_STONE -> planStone(hit, sp);
            case HARVEST, TILL, DECONTAMINATE -> {
                queue.clear();
                queue.add(hit);
                workPos = hit;
                yield true;
            }
            default -> {
                workPos = hit;
                yield true;
            }
        };
        if (!ok) {
            skip.add(hit.asLong());
            return 0;
        }
        return 1;
    }

    private static boolean mayPickUp(ServerPlayer sp, ItemEntity e) {
        net.minecraft.nbt.CompoundTag tag = e.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        if (tag.hasUUID("Owner") && !tag.getUUID("Owner").equals(sp.getUUID())) return false;
        return !net.neoforged.neoforge.event.EventHooks.fireItemPickupPre(e, sp).canPickup().isFalse();
    }

    private boolean isTreeCandidate(BlockPos p) {
        if (skip.contains(p.asLong()) || !level.getBlockState(p).is(BlockTags.LOGS)) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (isNaturalLeaves(level.getBlockState(p.offset(dx, dy, dz)))) return true;
                }
            }
        }
        return false;
    }

    private int planGadget(ServerPlayer sp) {
        NanoGadget.Order o = NanoGadget.order(sp);
        if (o == null || o.ops().isEmpty()) return -1;
        queue.clear();
        queue.addAll(o.tunnel);
        queue.addAll(o.ops());
        workPos = queue.peekFirst();
        return workPos != null ? 1 : -1;
    }

    private static boolean isNaturalLeaves(BlockState st) {
        return st.getBlock() instanceof LeavesBlock && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT);
    }

    private boolean planTree(BlockPos start, boolean first) {
        List<BlockPos> logs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        seen.add(start.asLong());
        while (!open.isEmpty()) {
            BlockPos p = open.poll();
            logs.add(p);
            if (logs.size() > MAX_LOGS) return false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - start.getX()) > TREE_SPREAD || Math.abs(n.getZ() - start.getZ()) > TREE_SPREAD) continue;
                        if (!level.isLoaded(n) || !seen.add(n.asLong())) continue;
                        if (level.getBlockState(n).is(BlockTags.LOGS)) open.add(n);
                    }
                }
            }
        }
        int lowest = Integer.MAX_VALUE;
        for (BlockPos p : logs) lowest = Math.min(lowest, p.getY());
        boolean grown = false;
        for (BlockPos p : logs) {
            if (p.getY() == lowest && level.getBlockState(p.below()).is(BlockTags.DIRT)) grown = true;
        }
        if (!grown) return false;
        logs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        List<BlockPos> climb = new ArrayList<>();
        if (NuclearConfig.value(NuclearConfig.NANOBOT_LEAVES_CLEAR)) {
            Set<Long> cseen = new HashSet<>();
            for (BlockPos p : logs) {
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (climb.size() < MAX_LEAVES && level.isLoaded(n) && cseen.add(n.asLong()) && isNaturalLeaves(level.getBlockState(n))) climb.add(n);
                }
            }
            climb = chain(climb);
        }
        List<BlockPos> leaves = new ArrayList<>();
        if (NuclearConfig.value(NuclearConfig.NANOBOT_BREAK_LEAVES)) {
            Set<Long> lseen = new HashSet<>();
            ArrayDeque<BlockPos> lopen = new ArrayDeque<>();
            for (BlockPos p : logs) {
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (lseen.add(n.asLong()) && isNaturalLeaves(level.getBlockState(n))) lopen.add(n);
                }
            }
            while (!lopen.isEmpty() && leaves.size() < MAX_LEAVES) {
                BlockPos p = lopen.poll();
                leaves.add(p);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (Math.abs(n.getX() - start.getX()) > TREE_SPREAD + 4 || Math.abs(n.getZ() - start.getZ()) > TREE_SPREAD + 4) continue;
                    if (level.isLoaded(n) && lseen.add(n.asLong()) && isNaturalLeaves(level.getBlockState(n))) lopen.add(n);
                }
            }
            leaves.removeAll(climb);
            leaves = chain(leaves);
        }
        if (first) {
            queue.clear();
            treeOf.clear();
            nextTree = 0;
            treeAnchor = start;
        }
        int id = nextTree++;
        for (List<BlockPos> part : List.of(climb, logs, leaves)) {
            for (BlockPos b : part) {
                queue.add(b);
                treeOf.put(b.asLong(), id);
            }
        }
        if (first) workPos = queue.peekFirst();
        return !queue.isEmpty();
    }

    static List<BlockPos> chain(List<BlockPos> in) {
        if (in.size() < 2) return in;
        List<BlockPos> rest = new ArrayList<>(in), out = new ArrayList<>(in.size());
        rest.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        BlockPos cur = rest.remove(0);
        out.add(cur);
        while (!rest.isEmpty()) {
            int bi = -1;
            for (int i = 0; i < rest.size(); i++) {
                BlockPos c = rest.get(i);
                if (c.getX() == cur.getX() && c.getZ() == cur.getZ() && c.getY() > cur.getY()) {
                    bi = i;
                    break;
                }
            }
            if (bi < 0) {
                double best = Double.MAX_VALUE;
                for (int i = 0; i < rest.size(); i++) {
                    BlockPos c = rest.get(i);
                    int dx = c.getX() - cur.getX(), dz = c.getZ() - cur.getZ();
                    double d = dx * dx + dz * dz;
                    if (d < best) {
                        best = d;
                        bi = i;
                    }
                }
            }
            cur = rest.remove(bi);
            out.add(cur);
        }
        return out;
    }

    private boolean isMinableOre(ServerPlayer sp, BlockPos p) {
        if (skip.contains(p.asLong())) return false;
        BlockState st = level.getBlockState(p);
        return st.is(Tags.Blocks.ORES) && st.getDestroySpeed(level, p) >= 0 && toolFor(sp, st) != null;
    }

    private boolean planVein(BlockPos start) {
        Block ore = level.getBlockState(start).getBlock();
        List<BlockPos> vein = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        seen.add(start.asLong());
        while (!open.isEmpty() && vein.size() < MAX_VEIN) {
            BlockPos p = open.poll();
            vein.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (level.isLoaded(n) && seen.add(n.asLong()) && level.getBlockState(n).is(ore)) open.add(n);
                    }
                }
            }
        }
        queue.clear();
        queue.addAll(NanoAccess.topDown(vein, Vec3.atCenterOf(start)));
        workPos = start;
        return true;
    }

    static boolean isStone(BlockState st) {
        return st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Tags.Blocks.STONES) || st.is(Tags.Blocks.COBBLESTONES);
    }

    private boolean planStone(BlockPos start, ServerPlayer sp) {
        BlockPos home = sp.blockPosition();
        int feet = home.getY();
        int minY = Math.min(start.getY(), feet);
        int near = settings.nearRadius();
        List<BlockPos> mass = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        seen.add(start.asLong());
        while (!open.isEmpty() && mass.size() < MAX_STONE) {
            BlockPos p = open.poll();
            mass.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() < minY || Math.abs(n.getX() - home.getX()) > near || Math.abs(n.getZ() - home.getZ()) > near
                                || n.getY() > feet + near || !level.isLoaded(n) || !seen.add(n.asLong()) || skip.contains(n.asLong())) continue;
                        if (n.getY() < feet && Math.abs(n.getX() - home.getX()) <= 2 && Math.abs(n.getZ() - home.getZ()) <= 2) continue;
                        if (isStone(level.getBlockState(n))) open.add(n);
                    }
                }
            }
        }
        queue.clear();
        queue.addAll(NanoAccess.topDown(mass, sp.position()));
        workPos = start;
        return true;
    }

    private boolean planAny(BlockPos hit, @Nullable List<BlockPos> area) {
        queue.clear();
        queue.addAll(area == null ? List.of(hit) : NanoAccess.topDown(area, Vec3.atCenterOf(hit)));
        workPos = hit;
        return true;
    }

    private boolean isContaminated(BlockPos p) {
        return !skip.contains(p.asLong()) && com.sqc.nuclearsq.radiation.Contamination.zones(level).get(p) > 0 && !com.sqc.nuclearsq.radiation.Contamination.isFixed(level, p);
    }

    private boolean decontaminate(ServerPlayer sp, BlockPos pos) {
        if (!isContaminated(pos) || !NanoPower.drain(sp, 1)) return false;
        com.sqc.nuclearsq.radiation.Contamination.clean(level, pos, 0, com.sqc.nuclearsq.radiation.DeconSprayerItem.CLEAN_SV_H);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.01);
        return true;
    }

    private boolean isRipeCrop(BlockPos p) {
        if (skip.contains(p.asLong())) return false;
        BlockState st = level.getBlockState(p);
        return st.getBlock() instanceof CropBlock c && c.isMaxAge(st);
    }

    @Nullable
    private static ItemStack findHoe(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (s.is(ItemTags.HOES) || s.getItem() instanceof net.minecraft.world.item.HoeItem)) return s;
        }
        return null;
    }

    private static boolean isTillable(BlockState st) {
        return st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.DIRT_PATH);
    }

    private boolean hydrated(BlockPos p) {
        for (BlockPos n : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 1, 4))) {
            if (level.getFluidState(n).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    static String tillReject(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (!isTillable(st)) return "kein Boden (" + st.getBlock().getDescriptionId() + ")";
        BlockState above = level.getBlockState(p.above());
        if (!above.isAir()) return "oben nicht frei (" + above.getBlock().getDescriptionId() + ")";
        for (BlockPos n : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 1, 4))) {
            if (level.getFluidState(n).is(FluidTags.WATER)) return null;
        }
        return "kein Wasser in 4 Bloecken";
    }

    public static String tillRejectForTest(ServerLevel level, BlockPos p) {
        String r = tillReject(level, p);
        return r == null ? "geeignet" : r;
    }

    private Predicate<BlockPos> tillTest(ServerPlayer sp) {
        if (findHoe(sp) == null) {
            diag[3]++;
            return p -> false;
        }
        return p -> {
            if (skip.contains(p.asLong()) || !isTillable(level.getBlockState(p))) return false;
            diag[0]++;
            if (!level.getBlockState(p.above()).isAir()) return false;
            diag[1]++;
            if (!hydrated(p)) return false;
            diag[2]++;
            return true;
        };
    }

    private Predicate<BlockPos> stoneTest(ServerPlayer sp) {
        BlockPos home = sp.blockPosition();
        int feet = home.getY();
        return p -> {
            if (p.getY() < feet - 1 || skip.contains(p.asLong())) return false;
            if (p.getY() < feet && Math.abs(p.getX() - home.getX()) <= 2 && Math.abs(p.getZ() - home.getZ()) <= 2) return false;
            BlockState st = level.getBlockState(p);
            if (!isStone(st)) return false;
            diag[0]++;
            if (toolFor(sp, st) == null) return false;
            diag[1]++;
            for (Direction d : Direction.values()) {
                if (!level.getBlockState(p.relative(d)).canOcclude()) {
                    diag[2]++;
                    return true;
                }
            }
            return false;
        };
    }

    private Predicate<BlockPos> plantTest(ServerPlayer sp) {
        ItemStack sap = findSapling(sp);
        if (sap == null) return p -> false;
        BlockState sapling = ((BlockItem) sap.getItem()).getBlock().defaultBlockState();
        return p -> {
            if (skip.contains(p.asLong()) || !level.getBlockState(p).isAir()) return false;
            if (!level.getBlockState(p.below()).is(BlockTags.DIRT) || !sapling.canSurvive(level, p)) return false;
            if (p.distSqr(sp.blockPosition()) < 4) return false;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockState near = level.getBlockState(p.offset(dx, 0, dz));
                    if (near.getBlock() instanceof SaplingBlock || near.is(BlockTags.LOGS)) return false;
                }
            }
            return true;
        };
    }

    @Nullable
    private static ItemStack findSapling(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SaplingBlock) return s;
        }
        return null;
    }

    @Nullable
    static ItemStack toolFor(ServerPlayer sp, BlockState st) {
        if (!st.requiresCorrectToolForDrops()) return ItemStack.EMPTY;
        var inv = sp.getInventory();
        ItemStack best = null;
        float bestSpeed = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.isCorrectToolForDrops(st)) continue;
            float speed = s.getDestroySpeed(st);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = s;
            }
        }
        return best;
    }

    private boolean hasPendingWork() {
        return !queue.isEmpty() || itemId != null || hasActiveJobs();
    }

    private Vec3 workPoint() {
        if (itemId != null && level.getEntity(itemId) instanceof ItemEntity e) return e.position().add(0, 0.3, 0);
        if (phase == Phase.WORK && hasActiveJobs()) {
            Vec3 sum = Vec3.ZERO;
            int n = 0;
            for (Job j : jobs) {
                if (j != null) {
                    sum = sum.add(Vec3.atCenterOf(j.pos));
                    n++;
                }
            }
            return sum.scale(1.0 / n);
        }
        BlockPos p = queue.peekFirst() != null ? queue.peekFirst() : workPos;
        return p == null ? center : Vec3.atCenterOf(p);
    }

    private void tickTravel() {
        if (itemId != null && !(level.getEntity(itemId) instanceof ItemEntity e && e.isAlive())) {
            resetTarget();
            setPhase(Phase.SEARCH, "Zielitem weg");
            return;
        }
        if (center.distanceToSqr(workPoint()) < 4.0) {
            setPhase(Phase.WORK, "Ziel erreicht");
        } else if (phaseTicks > TRAVEL_TIMEOUT) {
            giveUpTarget();
        }
    }

    private void giveUpTarget() {
        NuclearSQ.LOGGER.info("[nanobots] {}: Ziel {} aufgegeben (Anflug dauert zu lange, Schwarm bei {})", task, workPos, center);
        if (workPos != null) skip.add(workPos.asLong());
        if (itemId != null) skipItems.add(itemId);
        resetTarget();
        setPhase(Phase.SEARCH, "Anflug dauert zu lange, Ziel aufgegeben");
    }

    private void tickWork(ServerPlayer sp) {
        if (task == NanoTask.COLLECT_ITEMS) {
            int interval = Math.max(1, NuclearConfig.value(NuclearConfig.NANOBOT_BLOCK_INTERVAL));
            if (phaseTicks % interval != 0) return;
            if (!workStep(sp)) return;
            finishTarget();
            return;
        }
        if (task == NanoTask.PLANT_SAPLINGS && queue.isEmpty() && !plantQueued && workPos != null) {
            queue.add(workPos);
            plantQueued = true;
        }
        int finishedThisTick = 0, maxFinish = NuclearConfig.value(NuclearConfig.NANOBOT_MAX_BREAKS_PER_TICK);
        for (int i = 0; i < jobs.length; i++) {
            if (jobs[i] == null) claim(sp, i);
            Job j = jobs[i];
            if (j == null || !advanceJob(i)) continue;
            if (finishedThisTick >= maxFinish) {
                j.done = Math.max(0, j.total - 1);
                continue;
            }
            finishedThisTick++;
            lastJobPos[i] = j.pos;
            int stage = j.stage;
            clearJob(i);
            stepPos = j.pos;
            stepBot = i;
            stepStage = stage;
            workStep(sp);
            if (phase != Phase.WORK) return;
            if (jobs[i] == null) claim(sp, i);
        }
        stay = center;
        if (task == NanoTask.FELL_TREE) extraTrees();
        if (queue.isEmpty() && !hasActiveJobs()) finishTarget();
        if (!queue.isEmpty() && !hasActiveJobs()) {
            if (++stall > 40) {
                stall = 0;
                startJob(sp, 0, queue.pollFirst());
            }
        } else {
            stall = 0;
        }
    }

    private int stall;

    private int columnRule() {
        return switch (task) {
            case GADGET_BREAK, MINE_STONE, MINE_ORE -> -1;
            case GADGET_BUILD, GADGET_REPLACE, GADGET_PASTE -> 1;
            default -> 0;
        };
    }

    private static long column(BlockPos p) {
        return ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
    }

    private void claim(ServerPlayer sp, int slot) {
        BlockPos p = claimNext(sp, slot);
        if (p != null) startJob(sp, slot, p);
    }

    @Nullable
    private BlockPos claimNext(ServerPlayer sp, int slot) {
        if (queue.isEmpty()) return null;
        if (task == NanoTask.FELL_TREE) return claimTree(slot);
        NanoGadget.Order o = task.gadget() ? NanoGadget.order(sp) : null;
        if (o != null && o.tunnelPending()) {
            BlockPos t = o.nextTunnel();
            if (t == null || !queue.contains(t)) return null;
            for (Job j : jobs) if (j != null && o.isTunnel(j.pos)) return null;
            queue.remove(t);
            return t;
        }
        int rule = columnRule();
        if (rule == 0) return queue.pollFirst();
        java.util.Map<Long, int[]> ext = new java.util.HashMap<>();
        for (BlockPos q : queue) widen(ext, q);
        for (Job j : jobs) if (j != null) widen(ext, j.pos);
        int spacing = NuclearConfig.value(NuclearConfig.NANOBOT_CLAIM_SPACING);
        java.util.Iterator<BlockPos> it = queue.iterator();
        BlockPos avoided = null;
        int avoidedBy = -1;
        while (it.hasNext()) {
            BlockPos q = it.next();
            int[] e = ext.get(column(q));
            boolean blocked = rule < 0 ? e[1] > q.getY() : e[0] < q.getY();
            if (blocked) continue;
            int by = nearJob(q, slot, spacing);
            if (by >= 0) {
                if (avoided == null) {
                    avoided = q;
                    avoidedBy = by;
                }
                continue;
            }
            it.remove();
            if (avoided != null) {
                NuclearSQ.LOGGER.info("[nanobots] {}: Ziel {} belegt von Bot {} (Nachbarschaft), Bot {} weicht aus nach {}", task, avoided, avoidedBy, slot, q);
            }
            return q;
        }
        return null;
    }

    private int nearJob(BlockPos p, int slot, int spacing) {
        if (spacing <= 1) return -1;
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            if (j == null || i == slot) continue;
            if (Math.max(Math.abs(j.pos.getX() - p.getX()), Math.max(Math.abs(j.pos.getY() - p.getY()), Math.abs(j.pos.getZ() - p.getZ()))) < spacing) return i;
        }
        return -1;
    }

    @Nullable
    private BlockPos claimTree(int slot) {
        int tid = botTree[slot];
        if (tid >= 0 && !treePending(tid)) {
            botTree[slot] = -1;
            tid = -1;
        }
        if (tid < 0) {
            outer:
            for (BlockPos q : queue) {
                Integer t = treeOf.get(q.asLong());
                if (t == null) continue;
                for (int b : botTree) if (b == t) continue outer;
                tid = t;
                break;
            }
            if (tid < 0) return null;
            botTree[slot] = tid;
        }
        java.util.Iterator<BlockPos> it = queue.iterator();
        while (it.hasNext()) {
            BlockPos q = it.next();
            Integer t = treeOf.get(q.asLong());
            if (t != null && t == tid) {
                it.remove();
                return q;
            }
        }
        return null;
    }

    private boolean treePending(int tid) {
        for (BlockPos q : queue) {
            Integer t = treeOf.get(q.asLong());
            if (t != null && t == tid) return true;
        }
        return false;
    }

    private void extraTrees() {
        if (xscanDone || botCount < 2 || treeAnchor == null) return;
        int owned = 0, trees = 0;
        java.util.Set<Integer> pending = new java.util.HashSet<>();
        for (BlockPos q : queue) {
            Integer t = treeOf.get(q.asLong());
            if (t != null) pending.add(t);
        }
        trees = pending.size();
        for (int b : botTree) if (b >= 0 && pending.contains(b)) owned++;
        if (owned >= botCount || trees > owned) return;
        if (xscan == null) xscan = new Scan(treeAnchor, 16, 16);
        if (!xscan.step(scanBudget() / 3, level, p -> !treeOf.containsKey(p.asLong()) && isTreeCandidate(p), null)) return;
        BlockPos hit = xscan.best;
        xscan = null;
        if (hit == null) {
            xscanDone = true;
            return;
        }
        if (!planTree(hit, false)) skip.add(hit.asLong());
    }

    private static void widen(java.util.Map<Long, int[]> ext, BlockPos p) {
        int[] e = ext.computeIfAbsent(column(p), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
        e[0] = Math.min(e[0], p.getY());
        e[1] = Math.max(e[1], p.getY());
    }

    private void tunnelStep(ServerPlayer sp, NanoGadget.Order o, BlockPos p) {
        if (NanoGadget.execute(sp, level, o, p, cargo, NanoSettings.canisterOf(sp))) {
            o.tunnel.remove(p);
            o.tunnelDone++;
            NuclearSQ.LOGGER.info("[nanobots] Gadget {}: Tunnelblock {} abgebaut ({} von {}, Tick {}, Welt-Tick {})", task, p, o.tunnelDone, o.tunnelTotal(), age, level.getGameTime());
            return;
        }
        int tries = o.attempts.merge(p, 1, Integer::sum);
        NuclearSQ.LOGGER.info("[nanobots] Gadget {}: Tunnelblock {} nicht abgebaut: {} (Versuch {} von 3)", task, p, o.why, tries);
        if (tries >= 3) {
            NanoGadget.abortNoWay(sp, o);
            queue.clear();
        } else {
            queue.addFirst(p);
        }
    }

    private void finishTarget() {
        resetTarget();
        boolean targetTask = task == NanoTask.FELL_TREE || task == NanoTask.MINE_ORE || task == NanoTask.MINE_STONE;
        if (!cargo.isEmpty() && targetTask) setPhase(Phase.DELIVER, "Ziel fertig, Fracht abliefern (" + cargo.size() + " Stapel)");
        else if (cargo.size() >= cargoLimit()) setPhase(Phase.DELIVER, "Fracht voll (" + cargo.size() + " Stapel)");
        else setPhase(Phase.SEARCH, "Ziel erledigt, naechstes suchen (Fracht " + cargo.size() + " Stapel bleibt an Bord)");
    }

    private void startJob(ServerPlayer sp, int slot, BlockPos pos) {
        clearJob(slot);
        boolean breaking = task == NanoTask.FELL_TREE || task == NanoTask.MINE_ORE || task == NanoTask.MINE_STONE || NanoGadget.breaks(task, level.getBlockState(pos));
        int total;
        if (breaking) {
            BlockState st = level.getBlockState(pos);
            ItemStack tool = bestTool(sp, st);
            float speed = tool.isEmpty() ? 1f : Math.max(1f, tool.getDestroySpeed(st));
            float hardness = st.getDestroySpeed(level, pos);
            if (hardness < 0) {
                total = 1;
            } else {
                boolean correct = !st.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(st);
                double ticks = hardness <= 0 ? 1 : hardness * (correct ? 30.0 : 100.0) / speed;
                ticks *= NuclearConfig.value(NuclearConfig.NANOBOT_WORK_TIME_FACTOR) / settings.workSpeed();
                int min = Math.max(3, (int) ((hardness < 0.5f ? 6 : NuclearConfig.value(NuclearConfig.NANOBOT_MIN_WORK_TICKS)) / settings.workSpeed()));
                total = (int) Mth.clamp(Math.ceil(ticks), min, 600);
            }
        } else {
            total = Math.max(3, (int) Math.round((task.gadget() ? 5 : task == NanoTask.HARVEST ? 12 : task == NanoTask.TILL ? 20 : 16) * NuclearConfig.value(NuclearConfig.NANOBOT_WORK_TIME_FACTOR) / settings.workSpeed()));
        }
        net.minecraft.util.RandomSource rng = botRng[slot];
        total = Math.max(2, (int) Math.round(total * (0.8 + 0.4 * rng.nextDouble())));
        int wait = (int) Math.round(travelWait(sp, slot, pos) * (0.85 + 0.3 * rng.nextDouble()));
        if (lastJobPos[slot] == null) wait += rng.nextInt(10);
        jobs[slot] = new Job(pos, total, wait, breaking);
        if (!startLogged) {
            startLogged = true;
            NuclearSQ.LOGGER.info("[nanobots] {}: Start nach {} Ticks bis zum ersten Auftrag (Suche bis Tick {}, Anflug ab Tick {}, erste Arbeitsphase ab Tick {}, Wegzeit {}, Arbeitszeit {}, Bot {})",
                    task, age, firstTravelAge, firstTravelAge, firstWorkAge, wait, total, slot);
        }
        workPos = pos;
        if (task.gadget()) {
            NanoGadget.Order go = NanoGadget.order(sp);
            if (go != null && go.total() <= 64) {
                NuclearSQ.LOGGER.info("[nanobots] Gadget {} bei {}: Job gestartet (Bot {}, Tick {}, Welt-Tick {}, Block jetzt {}, bricht {}, Wegzeit {}, Arbeitszeit {})",
                        task, pos, slot, age, level.getGameTime(), level.getBlockState(pos).getBlock().getDescriptionId(), breaking, jobs[slot].delay, total);
            }
        }
    }

    private int travelWait(ServerPlayer sp, int slot, BlockPos pos) {
        double ticks;
        BlockPos lastJobPos = this.lastJobPos[slot];
        if (lastJobPos == null) {
            ticks = 5 + Math.max(0, pos.getY() + 0.5 - sp.getY()) / 0.2;
        } else {
            double dx = pos.getX() - lastJobPos.getX(), dz = pos.getZ() - lastJobPos.getZ();
            ticks = 4 + Math.abs(pos.getY() - lastJobPos.getY()) / 0.2 + Math.sqrt(dx * dx + dz * dz) / 0.14;
        }
        return (int) Math.min(150, ticks);
    }

    private boolean advanceJob(int slot) {
        Job j = jobs[slot];
        if (j == null) return true;
        if (j.delay > 0) {
            j.delay--;
            return false;
        }
        j.done++;
        if (j.breaking) {
            int stage = Math.min(9, 10 * j.done / j.total);
            if (stage != j.stage) {
                level.destroyBlockProgress(breakId(slot), j.pos, stage);
                j.stage = stage;
            }
        }
        return j.done >= j.total;
    }

    private void clearJob(int slot) {
        Job j = jobs[slot];
        jobs[slot] = null;
        if (j != null && j.breaking && j.stage >= 0) level.destroyBlockProgress(breakId(slot), j.pos, -1);
    }

    private void clearJobs(boolean requeue) {
        for (int i = jobs.length - 1; i >= 0; i--) {
            Job j = jobs[i];
            clearJob(i);
            if (requeue && j != null) queue.addFirst(j.pos);
        }
    }

    private int breakId(int slot) {
        return breakId * 20 - slot;
    }

    static ItemStack bestTool(ServerPlayer sp, BlockState st) {
        var inv = sp.getInventory();
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1f;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || (st.requiresCorrectToolForDrops() && !s.isCorrectToolForDrops(st))) continue;
            float speed = s.getDestroySpeed(st);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = s;
            }
        }
        return best;
    }

    private boolean workStep(ServerPlayer sp) {
        switch (task) {
            case FELL_TREE, MINE_ORE, MINE_STONE, HARVEST, TILL, DECONTAMINATE -> {
                BlockPos p = stepPos;
                if (p == null) return true;
                boolean ok = switch (task) {
                    case HARVEST -> harvest(sp, p);
                    case TILL -> till(sp, p);
                    case DECONTAMINATE -> decontaminate(sp, p);
                    default -> tryBreak(sp, p);
                };
                if (!ok) skip.add(p.asLong());
                if (cargo.size() >= cargoLimit()) {
                    setPhase(Phase.DELIVER, "Fracht voll (" + cargo.size() + " Stapel)");
                    return false;
                }
                return queue.isEmpty();
            }
            case GADGET_BREAK, GADGET_BUILD, GADGET_REPLACE, GADGET_PASTE -> {
                BlockPos p = stepPos;
                NanoGadget.Order o = NanoGadget.order(sp);
                if (p == null || o == null) {
                    queue.clear();
                    return true;
                }
                if (o.isTunnel(p)) {
                    tunnelStep(sp, o, p);
                    if (cargo.size() >= cargoLimit()) {
                        setPhase(Phase.DELIVER, "Fracht voll (" + cargo.size() + " Stapel)");
                        return false;
                    }
                    return queue.isEmpty();
                }
                o.ops().remove(p);
                if (NanoGadget.execute(sp, level, o, p, cargo, NanoSettings.canisterOf(sp))) {
                    o.done++;
                    if (o.total() <= 64) {
                        NuclearSQ.LOGGER.info("[nanobots] Gadget {} bei {}: bearbeitet ueber NanoGadget.execute (Bot {}, Tick {}, Welt-Tick {}, Fortschrittsstufe {}, Block danach {})",
                                task, p, stepBot, age, level.getGameTime(), stepStage, level.getBlockState(p).getBlock().getDescriptionId());
                    }
                } else if (NanoGadget.GONE.equals(o.why)) {
                    o.gone++;
                    NuclearSQ.LOGGER.info("[nanobots] Gadget {} bei {}: Block war schon Luft (bei der Bestaetigung stand dort: {}; Bot {}, Tick {}, Welt-Tick {})", task, p, o.startBlock(p), stepBot, age, level.getGameTime());
                } else {
                    int tries = o.attempts.merge(p, 1, Integer::sum);
                    if (tries < 3) o.retry.add(p);
                    else o.skipped++;
                    NuclearSQ.LOGGER.info("[nanobots] Gadget {} bei {}: {} (Bot {}, Tick {}, Versuch {} von 3{})", task, p, o.why, stepBot, age, tries, tries < 3 ? ", kommt am Ende noch einmal dran" : ", endgueltig uebersprungen");
                }
                if (o.ops().isEmpty() && !o.retry.isEmpty()) {
                    o.ops().addAll(o.retry);
                    queue.addAll(o.retry);
                    o.retry.clear();
                }
                if (o.ops().isEmpty()) {
                    NanoGadget.finished(sp, o);
                    queue.clear();
                }
                if (cargo.size() >= cargoLimit()) {
                    setPhase(Phase.DELIVER, "Fracht voll (" + cargo.size() + " Stapel)");
                    return false;
                }
                return queue.isEmpty();
            }
            case COLLECT_ITEMS -> {
                if (itemId != null && level.getEntity(itemId) instanceof ItemEntity e && e.isAlive()) {
                    if (e.position().distanceToSqr(center) > 16.0) {
                        setPhase(Phase.TRAVEL, "Item zu weit weg");
                        return false;
                    }
                    if (!mayPickUp(sp, e)) {
                        skipItems.add(e.getUUID());
                        return true;
                    }
                    addCargo(cargo, e.getItem());
                    e.discard();
                }
                return true;
            }
            default -> {
                if (stepPos != null && !plant(sp, stepPos)) skip.add(stepPos.asLong());
                return true;
            }
        }
    }

    private boolean plant(ServerPlayer sp, BlockPos pos) {
        ItemStack sap = findSapling(sp);
        if (sap == null || !level.getBlockState(pos).isAir()) return plantFailed(pos, "kein Setzling oder Ziel nicht frei");
        BlockState st = ((BlockItem) sap.getItem()).getBlock().defaultBlockState();
        if (!st.canSurvive(level, pos)) return plantFailed(pos, "Setzling kann dort nicht stehen");
        if (!level.mayInteract(sp, pos)) return plantFailed(pos, "Spawnschutz");
        if (sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer())) return plantFailed(pos, "Spielmodus erlaubt es nicht");
        BlockEvent.EntityPlaceEvent ev = new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, pos), level.getBlockState(pos.below()), sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return plantFailed(pos, "EntityPlaceEvent abgebrochen");
        level.setBlock(pos, st, 3);
        NuclearSQ.LOGGER.info("[nanobots] Setzling gepflanzt bei {} ({})", pos, st.getBlock().getDescriptionId());
        level.playSound(null, pos, st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.1f);
        if (!sp.getAbilities().instabuild) sap.shrink(1);
        return true;
    }

    private boolean harvest(ServerPlayer sp, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(st) || !level.mayInteract(sp, pos)) return false;
        if (sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer()) || !NanoPower.drain(sp, 1)) return false;
        BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(level, pos, st, sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return false;
        ItemStack seed = crop.getCloneItemStack(level, pos, st);
        List<ItemStack> drops = Block.getDrops(st, level, pos, level.getBlockEntity(pos), sp, ItemStack.EMPTY);
        boolean replant = false;
        for (ItemStack d : drops) {
            if (!replant && !seed.isEmpty() && d.is(seed.getItem())) {
                d.shrink(1);
                replant = true;
            }
            addCargo(cargo, d);
        }
        level.levelEvent(2001, pos, Block.getId(st));
        level.setBlock(pos, replant ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private boolean till(ServerPlayer sp, BlockPos pos) {
        if (findHoe(sp) == null || !isTillable(level.getBlockState(pos)) || !level.getBlockState(pos.above()).isAir()) return false;
        if (!level.mayInteract(sp, pos) || sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer())) return false;
        BlockEvent.EntityPlaceEvent ev = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), level.getBlockState(pos), sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled() || !NanoPower.drain(sp, 1)) return false;
        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    private boolean rejected(ServerPlayer sp, BlockPos pos, String why) {
        NuclearSQ.LOGGER.info("[nanobots] {}: Abbau bei {} abgelehnt: {}", task, pos, why);
        if (age - lastReject > 100) {
            lastReject = age;
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.protected"), true);
        }
        return false;
    }

    private static boolean plantFailed(BlockPos pos, String why) {
        NuclearSQ.LOGGER.info("[nanobots] Pflanzen bei {} abgelehnt: {}", pos, why);
        return false;
    }

    private boolean tryBreak(ServerPlayer sp, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        boolean wanted = switch (task) {
            case FELL_TREE -> st.is(BlockTags.LOGS) || isNaturalLeaves(st);
            case MINE_ORE -> st.is(Tags.Blocks.ORES);
            case MINE_STONE -> pointAny ? !st.isAir() : isStone(st);
            default -> false;
        };
        if (!wanted || st.getDestroySpeed(level, pos) < 0 || !level.isLoaded(pos)) return false;
        if (!level.mayInteract(sp, pos)) return rejected(sp, pos, "Spawnschutz");
        if (sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer())) return rejected(sp, pos, "Spielmodus erlaubt es nicht");
        ItemStack tool = task == NanoTask.MINE_ORE || task == NanoTask.MINE_STONE ? toolFor(sp, st) : ItemStack.EMPTY;
        if (tool == null || !NanoPower.drain(sp, 1)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(level, pos, st, sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return rejected(sp, pos, "BreakEvent abgebrochen (Schutz oder Claim)");
        int exp = st.getExpDrop(level, pos, be, sp, tool);
        List<ItemStack> drops = Block.getDrops(st, level, pos, be, sp, tool);
        if (!level.destroyBlock(pos, false, sp)) return false;
        for (ItemStack d : drops) addCargo(cargo, d);
        if (exp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(pos), exp);
        return true;
    }

    private void tickDeliver(ServerPlayer sp) {
        if (center.distanceToSqr(hover(sp)) < 2.25 || phaseTicks > 100) {
            deliver(sp);
            setPhase(hasPendingWork() ? Phase.TRAVEL : Phase.SEARCH, hasPendingWork() ? "Fracht abgeliefert, weiter zum naechsten Block" : "Fracht abgeliefert, naechstes Ziel suchen");
        }
    }

    private void deliver(ServerPlayer sp) {
        if (cargo.isEmpty()) return;
        java.util.Map<net.minecraft.world.item.Item, Integer> before = new java.util.HashMap<>();
        int total = 0;
        for (ItemStack s : cargo) {
            total += s.getCount();
            before.computeIfAbsent(s.getItem(), it -> countInInventory(sp, it));
        }
        for (ItemStack s : cargo) {
            if (sp.isAlive()) {
                net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(sp, s.copy());
            } else {
                ItemEntity drop = new ItemEntity(level, sp.getX(), sp.getY(), sp.getZ(), s.copy());
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        int gained = 0;
        for (var e : before.entrySet()) gained += countInInventory(sp, e.getKey()) - e.getValue();
        NuclearSQ.LOGGER.info("[nanobots] Lieferung: {} Stapel, {} Items, {} davon ins Inventar, {} vor die Fuesse des Spielers", cargo.size(), total, gained, total - gained);
        cargo.clear();
        if (sp.isAlive()) {
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.2f);
        }
    }

    static void addCargo(List<ItemStack> cargo, ItemStack add) {
        if (add.isEmpty()) return;
        ItemStack rest = add.copy();
        for (ItemStack c : cargo) {
            if (rest.isEmpty()) break;
            if (ItemStack.isSameItemSameComponents(c, rest) && c.getCount() < c.getMaxStackSize()) {
                int n = Math.min(rest.getCount(), c.getMaxStackSize() - c.getCount());
                c.grow(n);
                rest.shrink(n);
            }
        }
        if (!rest.isEmpty()) cargo.add(rest);
    }

    private static int countInInventory(ServerPlayer sp, net.minecraft.world.item.Item item) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    private void move(ServerPlayer sp) {
        Vec3 goal;
        double radius;
        switch (phase) {
            case TRAVEL -> {
                goal = workPoint();
                radius = 0.7;
            }
            case WORK -> {
                goal = workPoint();
                radius = 1.0;
            }
            case DELIVER, LEAVE -> {
                goal = hover(sp);
                radius = 0.6;
            }
            default -> {
                double r = settings.radius();
                goal = stay != null && stay.distanceToSqr(hover(sp)) < r * r ? stay : hover(sp);
                radius = 1.4;
            }
        }
        Vec3 d = goal.subtract(center);
        double dist = d.length();
        if (dist > 0.01) {
            double speed = Math.min(1.0, NuclearConfig.value(NuclearConfig.NANOBOT_SWARM_SPEED) * settings.speedFactor());
            double step = Mth.clamp(dist * 0.12, Math.min(0.05, speed), speed);
            center = center.add(d.scale(Math.min(step, dist) / dist));
        }
    }

    private void send(ServerPlayer sp) {
        long[] targets = new long[botCount];
        int[] progress = new int[botCount];
        for (int i = 0; i < botCount; i++) {
            Job j = jobs[i];
            targets[i] = j == null ? NanoSwarmPayload.NONE : j.pos.asLong();
            progress[i] = j == null ? -1 : progressOf(j);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new NanoSwarmPayload(owner, center, phase.ordinal() + 1, targets, progress, (int) Math.round(settings.speedFactor() * 10)));
    }

    static final class Scan {
        private final BlockPos origin;
        private final int rx, ry;
        private final long total;
        private long idx;
        @Nullable BlockPos best;
        private double bestD = Double.MAX_VALUE;

        Scan(BlockPos origin, int rx, int ry) {
            this.origin = origin;
            this.rx = rx;
            this.ry = ry;
            long w = 2L * rx + 1;
            this.total = w * w * (2L * ry + 1);
        }

        boolean step(int budget, ServerLevel level, Predicate<BlockPos> test, @Nullable java.util.function.ToDoubleFunction<BlockPos> bias) {
            int w = 2 * rx + 1;
            double r2 = (double) rx * rx;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (; budget-- > 0 && idx < total; idx++) {
                int x = (int) (idx % w), z = (int) ((idx / w) % w), y = (int) (idx / ((long) w * w));
                m.set(origin.getX() - rx + x, origin.getY() - ry + y, origin.getZ() - rx + z);
                double d = m.distSqr(origin);
                if (d > r2 || d >= bestD || !level.isLoaded(m)) continue;
                if (test.test(m)) {
                    double score = d + (bias == null ? 0 : bias.applyAsDouble(m));
                    if (score < bestD) {
                        best = m.immutable();
                        bestD = score;
                    }
                }
            }
            return idx >= total;
        }
    }
}
