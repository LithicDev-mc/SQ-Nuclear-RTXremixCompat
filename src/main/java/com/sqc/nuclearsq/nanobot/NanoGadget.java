package com.sqc.nuclearsq.nanobot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class NanoGadget {
    private NanoGadget() {}

    public static final int PREVIEW_MAX = 20000;

    record Done(BlockPos pos, BlockState before, BlockState after, List<ItemStack> drops, boolean placedMaterial, @Nullable Item item, int exp) {}

    public record Entry(BlockPos rel, BlockState state) {}

    public record Template(List<Entry> entries) {}

    private static final Map<UUID, Template> TEMPLATES = new HashMap<>();

    @Nullable
    public static Template template(ServerPlayer sp) {
        return TEMPLATES.get(sp.getUUID());
    }

    public static void forgetTemplate(ServerPlayer sp) {
        TEMPLATES.remove(sp.getUUID());
    }

    public static BlockPos transform(BlockPos rel, int rot, boolean mirror) {
        int x = mirror ? -rel.getX() : rel.getX(), z = rel.getZ();
        for (int i = 0, n = Math.floorMod(rot, 4); i < n; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new BlockPos(x, rel.getY(), z);
    }

    public static BlockState transform(BlockState st, int rot, boolean mirror) {
        if (mirror) st = st.mirror(Mirror.FRONT_BACK);
        return st.rotate(Rotation.values()[Math.floorMod(rot, 4)]);
    }

    public static BlockPos pastePos(BlockPos anchor, GadgetData.Paste p, BlockPos rel) {
        return anchor.offset(p.ox(), p.oy(), p.oz()).offset(transform(rel, p.rot(), p.mirror()));
    }

    public static final class Order {
        final NanoTask task;
        final ResourceKey<Level> dimension;
        final java.util.LinkedHashSet<BlockPos> ops = new java.util.LinkedHashSet<>();
        @Nullable final Item material;
        @Nullable final Block from;
        final int source;
        final List<BlockPos> cells;
        final List<Done> log = new ArrayList<>();
        final ArrayDeque<BlockPos> tunnel = new ArrayDeque<>();
        final List<BlockPos> tunnelAll = new ArrayList<>();
        final java.util.Set<BlockPos> tunnelSet = new java.util.HashSet<>();
        int tunnelDone;
        final Map<BlockPos, BlockState> states = new HashMap<>();
        boolean replace;
        final int total;
        int done, skipped;
        int gone;
        int initial;
        final Map<BlockPos, String> start = new HashMap<>();
        final Map<BlockPos, Long> broken = new HashMap<>();
        @Nullable String why;
        final java.util.Map<BlockPos, Integer> attempts = new HashMap<>();
        final List<BlockPos> retry = new ArrayList<>();
        int dropStacks, dropItems, absorbed, noDrop;
        boolean warnedMaterial;

        Order(NanoTask task, ResourceKey<Level> dimension, List<BlockPos> cells, @Nullable Item material, @Nullable Block from, int source) {
            this.task = task;
            this.dimension = dimension;
            this.ops.addAll(cells);
            this.cells = new ArrayList<>(cells);
            this.material = material;
            this.from = from;
            this.source = source;
            this.total = cells.size();
        }

        public java.util.Set<BlockPos> ops() {
            return ops;
        }

        public int tunnelTotal() {
            return tunnelAll.size();
        }

        public int tunnelDone() {
            return tunnelDone;
        }

        public List<BlockPos> tunnelCells() {
            return java.util.List.copyOf(tunnelAll);
        }

        boolean tunnelPending() {
            return !tunnel.isEmpty();
        }

        @Nullable
        BlockPos nextTunnel() {
            return tunnel.peekFirst();
        }

        boolean isTunnel(BlockPos p) {
            return tunnelSet.contains(p);
        }

        void setTunnel(List<BlockPos> cells) {
            tunnel.clear();
            tunnelAll.clear();
            tunnelSet.clear();
            tunnel.addAll(cells);
            tunnelAll.addAll(cells);
            tunnelSet.addAll(cells);
        }

        public int total() {
            return total;
        }

        public int done() {
            return done;
        }

        public int skipped() {
            return skipped;
        }

        public int gone() {
            return gone;
        }

        public int initial() {
            return initial;
        }

        public boolean wasBrokenBySwarm(BlockPos p) {
            return broken.containsKey(p);
        }

        public long brokenAt(BlockPos p) {
            return broken.getOrDefault(p, -1L);
        }

        public String startBlock(BlockPos p) {
            return start.getOrDefault(p, "?");
        }

        public int dropItems() {
            return dropItems + absorbed;
        }

        public int logged() {
            return log.size();
        }

        public String report() {
            return "bearbeitet " + done + ", schon weg " + gone + ", uebersprungen " + skipped + ", Bloecke im Bereich " + initial + ", Drops " + dropItems + " Items in " + dropStacks + " Stapeln, eingesammelte Welt-Items " + absorbed
                    + ", Bloecke ohne Drop " + noDrop + ", Tunnel " + tunnelDone + " von " + tunnelAll.size();
        }
    }

    private static final Map<UUID, Order> ORDERS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Order>> HISTORY = new HashMap<>(), REDO = new HashMap<>();

    private static int historyLimit() {
        return NuclearConfig.value(NuclearConfig.NANOBOT_GADGET_HISTORY);
    }

    private static void remember(ServerPlayer sp, Order o, boolean clearRedo) {
        ArrayDeque<Order> h = HISTORY.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        h.addFirst(o);
        while (h.size() > historyLimit()) h.removeLast();
        if (clearRedo) REDO.remove(sp.getUUID());
    }

    private static void rememberRedo(ServerPlayer sp, Order o) {
        ArrayDeque<Order> r = REDO.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        r.addFirst(o);
        while (r.size() > historyLimit()) r.removeLast();
    }

    public static int historySize(ServerPlayer sp) {
        ArrayDeque<Order> h = HISTORY.get(sp.getUUID());
        return h == null ? 0 : h.size();
    }

    public static boolean canRedo(ServerPlayer sp) {
        ArrayDeque<Order> r = REDO.get(sp.getUUID());
        return r != null && !r.isEmpty();
    }

    @Nullable
    public static Order order(ServerPlayer sp) {
        return ORDERS.get(sp.getUUID());
    }

    @Nullable
    public static Order last(ServerPlayer sp) {
        ArrayDeque<Order> h = HISTORY.get(sp.getUUID());
        return h == null ? null : h.peekFirst();
    }

    public static boolean canUndo(ServerPlayer sp) {
        return historySize(sp) > 0;
    }

    public static List<BlockPos> cells(int shape, BlockPos a, BlockPos b, int limit) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        List<BlockPos> out = new ArrayList<>();
        if (shape == GadgetData.LINE) {
            int n = Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0));
            if (n + 1 > limit) return List.of();
            for (int i = 0; i <= n; i++) {
                double t = n == 0 ? 0 : (double) i / n;
                BlockPos p = new BlockPos((int) Math.round(Mth.lerp(t, a.getX(), b.getX())), (int) Math.round(Mth.lerp(t, a.getY(), b.getY())),
                        (int) Math.round(Mth.lerp(t, a.getZ(), b.getZ())));
                if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) out.add(p);
            }
            return out;
        }
        if (shape == GadgetData.PLANE) {
            if (a.getY() == b.getY() || (a.getX() != b.getX() && a.getZ() != b.getZ())) {
                y0 = y1 = a.getY();
            } else if (a.getX() == b.getX()) {
                x1 = x0;
            } else {
                z1 = z0;
            }
        }
        long vol = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (vol > limit * 8L && shape != GadgetData.HULL) return List.of();
        if (vol > limit * 64L) return List.of();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (shape == GadgetData.HULL && x1 - x0 >= 2 && y1 - y0 >= 2 && z1 - z0 >= 2
                            && x > x0 && x < x1 && y > y0 && y < y1 && z > z0 && z < z1) continue;
                    out.add(new BlockPos(x, y, z));
                    if (out.size() > limit) return List.of();
                }
            }
        }
        return out;
    }

    public static List<BlockPos> cells(GadgetData d, int limit) {
        if (!GadgetData.isRound(d.shape())) {
            if (d.p1().isEmpty() || d.p2().isEmpty()) return List.of();
            return cells(d.shape(), d.p1().get(), d.p2().get(), limit);
        }
        if (d.p1().isEmpty()) return List.of();
        BlockPos c = d.p1().get();
        int r = d.radius(), axis = d.axis();
        int h = d.shape() == GadgetData.CYLINDER ? d.height() : 1;
        long span = 2L * r + 1;
        if (span * span * (d.shape() == GadgetData.SPHERE ? span : h) > limit * 64L) return List.of();
        double lim = (r + 0.5) * (r + 0.5);
        int w0 = d.shape() == GadgetData.SPHERE ? -r : 0, w1 = d.shape() == GadgetData.SPHERE ? r : h - 1;
        List<BlockPos> out = new ArrayList<>();
        for (int w = w0; w <= w1; w++) {
            for (int u = -r; u <= r; u++) {
                for (int v = -r; v <= r; v++) {
                    if (!insideRound(d.shape(), lim, u, v, w, h)) continue;
                    if (d.hollow()) {
                        boolean edge;
                        if (d.shape() == GadgetData.SPHERE) {
                            edge = !insideRound(d.shape(), lim, u + 1, v, w, h) || !insideRound(d.shape(), lim, u - 1, v, w, h) || !insideRound(d.shape(), lim, u, v + 1, w, h)
                                    || !insideRound(d.shape(), lim, u, v - 1, w, h) || !insideRound(d.shape(), lim, u, v, w + 1, h) || !insideRound(d.shape(), lim, u, v, w - 1, h);
                        } else {
                            edge = !insideRound(d.shape(), lim, u + 1, v, w, h) || !insideRound(d.shape(), lim, u - 1, v, w, h) || !insideRound(d.shape(), lim, u, v + 1, w, h)
                                    || !insideRound(d.shape(), lim, u, v - 1, w, h);
                        }
                        if (!edge) continue;
                    }
                    out.add(switch (axis) {
                        case 0 -> c.offset(w, u, v);
                        case 2 -> c.offset(u, v, w);
                        default -> c.offset(u, w, v);
                    });
                    if (out.size() > limit) return List.of();
                }
            }
        }
        return out;
    }

    private static boolean insideRound(int shape, double lim, int u, int v, int w, int h) {
        if (shape == GadgetData.SPHERE) return u * u + v * v + w * w <= lim;
        return u * u + v * v <= lim && w >= 0 && w < h;
    }

    public static GadgetData data(ItemStack canister) {
        return canister.getOrDefault(NanobotRegistry.GADGET.get(), GadgetData.EMPTY);
    }

    public static void setData(ItemStack canister, GadgetData d) {
        canister.set(NanobotRegistry.GADGET.get(), d);
    }

    public static void click(ServerPlayer sp, ItemStack canister, BlockPos clicked, net.minecraft.core.Direction face) {
        NanoTask task = NanoCanisterItem.task(canister);
        GadgetData d = data(canister);
        BlockPos pos = task == NanoTask.GADGET_BUILD || task == NanoTask.GADGET_PASTE ? clicked.relative(face) : clicked;
        int max = NuclearConfig.value(NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS);
        if (d.ready(task)) {
            confirm(sp, canister);
        } else if (task == NanoTask.GADGET_PASTE) {
            setData(canister, d.withPoints(Optional.of(pos.immutable()), Optional.empty()));
            Template t = TEMPLATES.get(sp.getUUID());
            if (t == null) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_template"), true);
            } else {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_paste_anchor", pos.getX(), pos.getY(), pos.getZ(), t.entries().size(), d.paste().rot() * 90), true);
            }
        } else if (d.p1().isEmpty()) {
            GadgetData n = d.withPoints(Optional.of(pos.immutable()), Optional.empty());
            setData(canister, n);
            if (GadgetData.isRound(d.shape())) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_center", pos.getX(), pos.getY(), pos.getZ(), cells(n, max).size()), true);
            } else {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_p1", pos.getX(), pos.getY(), pos.getZ()), true);
            }
        } else {
            GadgetData n = d.withPoints(d.p1(), Optional.of(pos.immutable()));
            setData(canister, n);
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_p2", pos.getX(), pos.getY(), pos.getZ(), cells(n, max).size()), true);
        }
    }

    public static void cancel(ServerPlayer sp, ItemStack canister) {
        GadgetData d = data(canister);
        if (d.points() > 0) setData(canister, d.withPoints(Optional.empty(), Optional.empty()));
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_cancelled"), true);
    }

    public static boolean confirm(ServerPlayer sp, ItemStack canister) {
        NanoTask task = NanoCanisterItem.task(canister);
        GadgetData d = data(canister);
        if (!task.gadget() || !d.ready(task)) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_points"), true);
            return false;
        }
        if (task == NanoTask.GADGET_COPY) return copy(sp, canister, d);
        if (task == NanoTask.GADGET_PASTE) return paste(sp, canister, d);
        Level level = sp.level();
        BlockPos a = d.p1().get();
        int max = NuclearConfig.value(NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS);
        List<BlockPos> cells = cells(d, max);
        if (cells.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_too_big", max), true);
            return false;
        }
        if (!checkArea(sp, canister, cells)) return false;
        Item material = null;
        Block from = null;
        if (task != NanoTask.GADGET_BREAK) {
            ItemStack off = sp.getOffhandItem();
            if (!(off.getItem() instanceof BlockItem bi)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_need_block"), true);
                return false;
            }
            material = bi;
            if (task == NanoTask.GADGET_REPLACE) {
                BlockState at = level.getBlockState(a);
                if (at.isAir()) {
                    sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_need_from"), true);
                    return false;
                }
                from = at.getBlock();
                if (from == bi.getBlock()) {
                    sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_same"), true);
                    return false;
                }
            }
            if (d.source() == GadgetData.PORT && NanoPort.access(sp, canister) == null && !sp.getAbilities().instabuild) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_port"), true);
                return false;
            }
        }
        Vec3 here = sp.position();
        List<BlockPos> sorted;
        List<BlockPos> tunnel = List.of();
        if (task == NanoTask.GADGET_BREAK) {
            NanoAccess.Result acc = NanoAccess.plan(sp.serverLevel(), sp, cells, NuclearConfig.value(NuclearConfig.NANOBOT_TUNNEL_DIGGING), NuclearConfig.value(NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS));
            if (!acc.ok()) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_way_up"), true);
                return false;
            }
            tunnel = acc.tunnel();
            sorted = NanoAccess.topDown(cells, acc.ref() != null && !tunnel.isEmpty() ? Vec3.atCenterOf(acc.ref()) : here);
        } else {
            sorted = new ArrayList<>(cells);
            sorted.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distToCenterSqr(here)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        }
        Order o = new Order(task, level.dimension(), sorted, material, from, d.source());
        o.setTunnel(tunnel);
        if (!tunnel.isEmpty()) sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_tunnel", tunnel.size()), true);
        o.initial = (int) sorted.stream().filter(p -> !level.getBlockState(p).isAir()).count();
        for (BlockPos p : sorted) o.start.put(p, level.getBlockState(p).getBlock().getDescriptionId());
        setData(canister, d.withPoints(Optional.empty(), Optional.empty()));
        start(sp, canister, o, true);
        return true;
    }

    private static boolean checkArea(ServerPlayer sp, ItemStack canister, Iterable<BlockPos> cells) {
        Level level = sp.level();
        int radius = NanoSettings.of(canister).radius();
        double r2 = (double) radius * radius;
        for (BlockPos p : cells) {
            if (p.distSqr(sp.blockPosition()) > r2) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_too_far", radius), true);
                return false;
            }
            if (!level.isLoaded(p) || level.isOutsideBuildHeight(p)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_unloaded"), true);
                return false;
            }
        }
        return true;
    }

    private static boolean copyable(Level level, BlockPos p, BlockState st) {
        if (st.isAir() || st.getBlock() instanceof LiquidBlock || st.getBlock().asItem() == net.minecraft.world.item.Items.AIR) return false;
        return st.getDestroySpeed(level, p) >= 0;
    }

    private static boolean copy(ServerPlayer sp, ItemStack canister, GadgetData d) {
        Level level = sp.level();
        int max = NuclearConfig.value(NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS);
        List<BlockPos> cells = cells(d, max);
        if (cells.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_too_big", max), true);
            return false;
        }
        if (!checkArea(sp, canister, cells)) return false;
        BlockPos a = d.p1().get();
        BlockPos anchor = GadgetData.isRound(d.shape()) ? a : new BlockPos(Math.min(a.getX(), d.p2().get().getX()), Math.min(a.getY(), d.p2().get().getY()), Math.min(a.getZ(), d.p2().get().getZ()));
        List<Entry> entries = new ArrayList<>();
        for (BlockPos p : cells) {
            BlockState st = level.getBlockState(p);
            if (copyable(level, p, st)) entries.add(new Entry(p.subtract(anchor), st));
        }
        if (entries.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_copy_empty"), true);
            return false;
        }
        TEMPLATES.put(sp.getUUID(), new Template(entries));
        setData(canister, d.withPoints(Optional.empty(), Optional.empty()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new GadgetTemplatePayload(entries.stream().map(Entry::rel).toList()));
        NuclearSQ.LOGGER.info("[nanobots] Gadget kopiert: {} Bloecke, Anker {}", entries.size(), anchor);
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_copied", entries.size(), anchor.getX(), anchor.getY(), anchor.getZ()), true);
        return true;
    }

    private static boolean paste(ServerPlayer sp, ItemStack canister, GadgetData d) {
        Template t = TEMPLATES.get(sp.getUUID());
        if (t == null || t.entries().isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_template"), true);
            return false;
        }
        Level level = sp.level();
        GadgetData.Paste ps = d.paste();
        BlockPos anchor = d.p1().get();
        Map<BlockPos, BlockState> states = new HashMap<>();
        List<BlockPos> cells = new ArrayList<>();
        for (Entry e : t.entries()) {
            BlockPos pos = pastePos(anchor, ps, e.rel());
            if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_unloaded"), true);
                return false;
            }
            BlockState want = transform(e.state(), ps.rot(), ps.mirror());
            BlockState cur = level.getBlockState(pos);
            if (cur == want) continue;
            if (!ps.replace() && !(cur.isAir() || cur.canBeReplaced())) continue;
            cells.add(pos);
            states.put(pos, want);
        }
        if (cells.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_paste_nothing"), true);
            return false;
        }
        if (!checkArea(sp, canister, cells)) return false;
        if (d.source() == GadgetData.PORT && NanoPort.access(sp, canister) == null && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_port"), true);
            return false;
        }
        Vec3 here = sp.position();
        List<BlockPos> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distToCenterSqr(here)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        Order o = new Order(NanoTask.GADGET_PASTE, level.dimension(), sorted, null, null, d.source());
        o.states.putAll(states);
        o.replace = ps.replace();
        o.initial = (int) sorted.stream().filter(p -> !level.getBlockState(p).isAir()).count();
        for (BlockPos p : sorted) o.start.put(p, level.getBlockState(p).getBlock().getDescriptionId());
        setData(canister, d.withPoints(Optional.empty(), Optional.empty()));
        start(sp, canister, o, true);
        return true;
    }

    private static void start(ServerPlayer sp, ItemStack canister, Order o, boolean clearRedo) {
        ORDERS.put(sp.getUUID(), o);
        remember(sp, o, clearRedo);
        NuclearSQ.LOGGER.info("[nanobots] Gadget {}: {} Bloecke, Material {}, Quelle {}, Verlauf {}", o.task, o.total, o.material, o.source, historySize(sp));
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_started", o.total), true);
        canister.set(NanobotRegistry.TASK.get(), o.task.ordinal());
        NanoSwarm running = NanoSwarms.get(sp);
        if (running == null || running.isEnded()) {
            NanoCanisterItem.toggle(sp, canister);
        } else if (running.task() != o.task) {
            running.setTask(o.task);
        }
    }

    static void finished(ServerPlayer sp, Order o) {
        ORDERS.remove(sp.getUUID(), o);
        NuclearSQ.LOGGER.info("[nanobots] Gadget {} fertig: {}", o.task, o.report());
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_done", o.done, o.skipped), true);
    }

    static void abortNoWay(ServerPlayer sp, Order o) {
        o.ops.clear();
        o.retry.clear();
        o.tunnel.clear();
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_way_up"), true);
        NuclearSQ.LOGGER.info("[nanobots] Gadget {}: kein Weg nach oben, Tunnel {} von {} Bloecken, Auftrag beendet", o.task, o.tunnelDone, o.tunnelAll.size());
        finished(sp, o);
    }

    public static void drop(ServerPlayer sp) {
        ORDERS.remove(sp.getUUID());
    }

    public static void clearAll() {
        ORDERS.clear();
        HISTORY.clear();
        REDO.clear();
        TEMPLATES.clear();
    }

    static boolean breaks(NanoTask task, BlockState st) {
        if (task == NanoTask.GADGET_PASTE) return !st.isAir() && !st.canBeReplaced();
        return (task == NanoTask.GADGET_BREAK || task == NanoTask.GADGET_REPLACE) && !st.isAir();
    }

    public static final String GONE = "bereits Luft";

    private static boolean fail(Order o, String why) {
        o.why = why;
        return false;
    }

    static boolean execute(ServerPlayer sp, ServerLevel level, Order o, BlockPos pos, List<ItemStack> cargo, ItemStack canister) {
        o.why = null;
        if (!level.dimension().equals(o.dimension)) return fail(o, "andere Dimension");
        if (!level.isLoaded(pos)) return fail(o, "Chunk nicht geladen");
        if (level.isOutsideBuildHeight(pos)) return fail(o, "ausserhalb der Bauhoehe");
        if (!level.mayInteract(sp, pos)) return fail(o, "Spawnschutz oder Weltrand");
        if (sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer())) return fail(o, "Spielmodus erlaubt es nicht");
        boolean creative = sp.getAbilities().instabuild;
        BlockState cur = level.getBlockState(pos);
        if (o.tunnelSet.contains(pos)) {
            if (NanoAccess.passable(level, pos)) return true;
            return breakBlock(sp, level, o, pos, cur, cargo, creative);
        }
        switch (o.task) {
            case GADGET_BREAK -> {
                return breakBlock(sp, level, o, pos, cur, cargo, creative);
            }
            case GADGET_BUILD -> {
                return place(sp, level, o, pos, cur, canister, creative);
            }
            case GADGET_REPLACE -> {
                if (cur.getBlock() != o.from) return fail(o, cur.isAir() ? GONE : "Block ist nicht mehr der zu ersetzende (" + cur.getBlock().getDescriptionId() + ")");
                if (!hasMaterial(sp, o, o.material, canister, creative)) return noMaterial(sp, o);
                if (!breakBlock(sp, level, o, pos, cur, cargo, creative)) return false;
                return place(sp, level, o, pos, level.getBlockState(pos), canister, creative);
            }
            case GADGET_PASTE -> {
                return pasteBlock(sp, level, o, pos, cur, cargo, canister, creative);
            }
            default -> {
                return fail(o, "kein Gadget-Modus");
            }
        }
    }

    private static boolean pasteBlock(ServerPlayer sp, ServerLevel level, Order o, BlockPos pos, BlockState cur, List<ItemStack> cargo, ItemStack canister, boolean creative) {
        BlockState want = o.states.get(pos);
        if (want == null) return fail(o, "kein Zielzustand");
        if (cur == want) return fail(o, GONE);
        Item item = want.getBlock().asItem();
        if (!(cur.isAir() || cur.canBeReplaced())) {
            if (!o.replace) return fail(o, "Ziel nicht frei (" + cur.getBlock().getDescriptionId() + ")");
            if (!hasMaterial(sp, o, item, canister, creative)) return noMaterial(sp, o);
            if (!breakBlock(sp, level, o, pos, cur, cargo, creative)) return false;
            cur = level.getBlockState(pos);
        }
        return placeState(sp, level, o, pos, cur, want, item, canister, creative);
    }

    private static boolean breakBlock(ServerPlayer sp, ServerLevel level, Order o, BlockPos pos, BlockState st, List<ItemStack> cargo, boolean creative) {
        if (st.isAir()) return fail(o, GONE);
        if (st.getDestroySpeed(level, pos) < 0) return fail(o, "unzerstoerbar (" + st.getBlock().getDescriptionId() + ")");
        ItemStack tool = NanoSwarm.bestTool(sp, st);
        if (!creative && !NanoPower.drain(sp, 1)) return fail(o, "kein Strom (Exo-Kern)");
        BlockEntity be = level.getBlockEntity(pos);
        BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(level, pos, st, sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return fail(o, "BreakEvent abgebrochen (Schutz oder Claim)");
        int exp = st.getExpDrop(level, pos, be, sp, tool);
        List<ItemStack> drops = creative ? List.of() : Block.getDrops(st, level, pos, be, sp, tool);
        if (!level.destroyBlock(pos, false, sp)) return fail(o, "destroyBlock lieferte false (Block " + st.getBlock().getDescriptionId() + ")");
        o.broken.put(pos.immutable(), level.getGameTime());
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack d : drops) if (!d.isEmpty()) kept.add(d.copy());
        o.log.add(new Done(pos.immutable(), st, Blocks.AIR.defaultBlockState(), kept, false, null, creative ? 0 : exp));
        int items = 0;
        for (ItemStack d : drops) {
            if (d.isEmpty()) continue;
            NanoSwarm.addCargo(cargo, d);
            o.dropStacks++;
            items += d.getCount();
        }
        o.dropItems += items;
        if (!creative) {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(0.6), x -> x.isAlive() && x.tickCount < 20)) {
                NanoSwarm.addCargo(cargo, e.getItem());
                o.absorbed += e.getItem().getCount();
                e.discard();
            }
            if (items == 0) {
                o.noDrop++;
                NuclearSQ.LOGGER.info("[nanobots] Gadget: {} bei {} lieferte keinen Drop (Werkzeug {})", st.getBlock().getDescriptionId(), pos, tool);
            }
        }
        if (exp > 0 && !creative) ExperienceOrb.award(level, Vec3.atCenterOf(pos), exp);
        return true;
    }

    private static boolean place(ServerPlayer sp, ServerLevel level, Order o, BlockPos pos, BlockState cur, ItemStack canister, boolean creative) {
        if (o.material == null) return fail(o, "kein Baumaterial");
        return placeState(sp, level, o, pos, cur, ((BlockItem) o.material).getBlock().defaultBlockState(), o.material, canister, creative);
    }

    private static boolean placeState(ServerPlayer sp, ServerLevel level, Order o, BlockPos pos, BlockState cur, BlockState st, Item item, ItemStack canister, boolean creative) {
        if (!(cur.isAir() || cur.canBeReplaced())) return fail(o, "Ziel nicht frei (" + cur.getBlock().getDescriptionId() + ")");
        if (!st.canSurvive(level, pos)) return fail(o, "Block kann dort nicht stehen (canSurvive)");
        if (!hasMaterial(sp, o, item, canister, creative)) return noMaterial(sp, o);
        if (!creative && !NanoPower.drain(sp, 1)) return fail(o, "kein Strom (Exo-Kern)");
        BlockEvent.EntityPlaceEvent ev = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), level.getBlockState(pos.below()), sp);
        NeoForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return fail(o, "EntityPlaceEvent abgebrochen (Schutz oder Claim)");
        if (!creative && !take(sp, o, item, canister)) return noMaterial(sp, o);
        level.setBlock(pos, st, 3);
        level.playSound(null, pos, st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.0f);
        o.log.add(new Done(pos.immutable(), cur, st, List.of(), !creative, item, 0));
        return true;
    }

    private static boolean noMaterial(ServerPlayer sp, Order o) {
        if (!o.warnedMaterial) {
            o.warnedMaterial = true;
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_material"), true);
        }
        return fail(o, "Material fehlt");
    }

    private static boolean hasMaterial(ServerPlayer sp, Order o, @Nullable Item item, ItemStack canister, boolean creative) {
        if (item == null) return false;
        if (creative) return true;
        if (o.source == GadgetData.PORT) {
            StorageAccess a = NanoPort.access(sp, canister);
            return a != null && a.extract(ItemKey.of(new ItemStack(item)), 1, true) >= 1;
        }
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }

    private static boolean take(ServerPlayer sp, Order o, Item item, ItemStack canister) {
        if (o.source == GadgetData.PORT) {
            StorageAccess a = NanoPort.access(sp, canister);
            return a != null && a.extract(ItemKey.of(new ItemStack(item)), 1, false) >= 1;
        }
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean hasAll(ServerPlayer sp, List<ItemStack> drops) {
        var inv = sp.getInventory();
        for (ItemStack d : drops) {
            int need = 0, have = 0;
            for (ItemStack e : drops) if (ItemStack.isSameItemSameComponents(e, d)) need += e.getCount();
            for (int i = 0; i < inv.getContainerSize(); i++) if (ItemStack.isSameItemSameComponents(inv.getItem(i), d)) have += inv.getItem(i).getCount();
            if (have < need) return false;
        }
        return true;
    }

    private static int takeBack(ServerPlayer sp, List<ItemStack> drops) {
        int taken = 0;
        var inv = sp.getInventory();
        for (ItemStack d : drops) {
            int want = d.getCount();
            for (int i = 0; i < inv.getContainerSize() && want > 0; i++) {
                ItemStack in = inv.getItem(i);
                if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, d)) {
                    int n = Math.min(want, in.getCount());
                    in.shrink(n);
                    want -= n;
                    taken += n;
                }
            }
        }
        return taken;
    }

    public static void undo(ServerPlayer sp, ItemStack canister) {
        ArrayDeque<Order> h = HISTORY.get(sp.getUUID());
        if (h == null || h.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_nothing_to_undo"), true);
            return;
        }
        Order o = h.peekFirst();
        ServerLevel level = sp.serverLevel();
        if (!level.dimension().equals(o.dimension)) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_undo_dimension"), true);
            return;
        }
        if (ORDERS.get(sp.getUUID()) == o) {
            NanoSwarms.stop(sp);
            ORDERS.remove(sp.getUUID(), o);
        }
        o.ops.clear();
        o.retry.clear();
        h.pollFirst();
        boolean creative = sp.getAbilities().instabuild;
        int restored = 0, removed = 0, kept = 0, taken = 0;
        Map<Item, Integer> refunds = new HashMap<>();
        for (int i = o.log.size() - 1; i >= 0; i--) {
            Done d = o.log.get(i);
            boolean pay = !creative && (!d.drops().isEmpty() || d.exp() > 0);
            if (!level.isLoaded(d.pos()) || !level.mayInteract(sp, d.pos()) || level.getBlockState(d.pos()).getBlock() != d.after().getBlock()
                    || pay && (!hasAll(sp, d.drops()) || sp.totalExperience < d.exp())) {
                kept++;
                continue;
            }
            if (pay) {
                taken += takeBack(sp, d.drops());
                sp.giveExperiencePoints(-d.exp());
            }
            level.setBlock(d.pos(), d.before(), 3);
            if (d.after().isAir()) {
                restored++;
            } else {
                removed++;
                if (d.placedMaterial() && d.item() != null) refunds.merge(d.item(), 1, Integer::sum);
            }
        }
        if (!creative) {
            for (Map.Entry<Item, Integer> e : refunds.entrySet()) {
                ItemStack back = new ItemStack(e.getKey(), e.getValue());
                if (o.source == GadgetData.PORT) {
                    StorageAccess a = NanoPort.access(sp, canister);
                    if (a != null) back = a.insert(back, false);
                }
                while (!back.isEmpty()) {
                    ItemStack part = back.split(Math.min(back.getCount(), back.getMaxStackSize()));
                    if (!sp.getInventory().add(part) && !part.isEmpty()) {
                        ItemEntity drop = new ItemEntity(level, sp.getX(), sp.getY(), sp.getZ(), part);
                        drop.setDefaultPickUpDelay();
                        level.addFreshEntity(drop);
                    }
                }
            }
        }
        rememberRedo(sp, o);
        NuclearSQ.LOGGER.info("[nanobots] Gadget {} rueckgaengig: {} Bloecke wiederhergestellt, {} entfernt (erstattet), {} unveraendert gelassen, {} Items wieder entnommen",
                o.task, restored, removed, kept, taken);
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_undone", restored, removed, kept), true);
    }

    public static boolean redo(ServerPlayer sp, ItemStack canister) {
        ArrayDeque<Order> r = REDO.get(sp.getUUID());
        if (r == null || r.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_nothing_to_redo"), true);
            return false;
        }
        if (ORDERS.containsKey(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_busy"), true);
            return false;
        }
        Order old = r.peekFirst();
        ServerLevel level = sp.serverLevel();
        if (!level.dimension().equals(old.dimension)) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_undo_dimension"), true);
            return false;
        }
        int radius = NanoSettings.of(canister).radius();
        double r2 = (double) radius * radius;
        for (BlockPos p : old.cells) {
            if (p.distSqr(sp.blockPosition()) > r2) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_too_far", radius), true);
                return false;
            }
            if (!level.isLoaded(p) || level.isOutsideBuildHeight(p)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_unloaded"), true);
                return false;
            }
        }
        if ((old.material != null || old.task == NanoTask.GADGET_PASTE) && old.source == GadgetData.PORT && NanoPort.access(sp, canister) == null && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_port"), true);
            return false;
        }
        List<BlockPos> redoCells = old.cells;
        List<BlockPos> redoTunnel = List.of();
        if (old.task == NanoTask.GADGET_BREAK) {
            NanoAccess.Result acc = NanoAccess.plan(level, sp, old.cells, NuclearConfig.value(NuclearConfig.NANOBOT_TUNNEL_DIGGING), NuclearConfig.value(NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS));
            if (!acc.ok()) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_no_way_up"), true);
                return false;
            }
            redoTunnel = acc.tunnel();
            redoCells = NanoAccess.topDown(old.cells, acc.ref() != null && !redoTunnel.isEmpty() ? Vec3.atCenterOf(acc.ref()) : sp.position());
        }
        r.pollFirst();
        Order n = new Order(old.task, level.dimension(), redoCells, old.material, old.from, old.source);
        n.setTunnel(redoTunnel);
        n.states.putAll(old.states);
        n.replace = old.replace;
        n.initial = (int) old.cells.stream().filter(p -> !level.getBlockState(p).isAir()).count();
        for (BlockPos p : old.cells) n.start.put(p, level.getBlockState(p).getBlock().getDescriptionId());
        start(sp, canister, n, false);
        return true;
    }

    public static final int KEY_UNDO = 0, KEY_REDO = 1, KEY_SIZE = 2, KEY_SHAPE = 3, KEY_HEIGHT = 4, KEY_ROTATE = 5, KEY_MIRROR = 6, KEY_TASK = 7, KEY_TOGGLE = 8, KEY_MENU = 9;

    public static void handleKey(ServerPlayer sp, int action, int value) {
        ItemStack can = sp.getMainHandItem().getItem() instanceof NanoCanisterItem ? sp.getMainHandItem() : sp.getOffhandItem();
        if (!(can.getItem() instanceof NanoCanisterItem)) return;
        switch (action) {
            case KEY_UNDO -> undo(sp, can);
            case KEY_REDO -> redo(sp, can);
            case KEY_TASK -> {
                if (value >= 0 && value < NanoTask.values().length) NanoCanisterItem.applyTask(sp, can, NanoTask.byOrdinal(value));
            }
            case KEY_TOGGLE -> NanoCanisterItem.toggle(sp, can);
            case KEY_MENU -> NanoCanisterItem.openMenu(sp, can == sp.getMainHandItem() ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
            case KEY_ROTATE, KEY_MIRROR -> {
                if (NanoCanisterItem.task(can) != NanoTask.GADGET_PASTE) return;
                GadgetData d = data(can);
                GadgetData.Paste p = d.paste();
                p = action == KEY_ROTATE ? p.withRot(p.rot() + (value < 0 ? -1 : 1)) : p.withMirror(!p.mirror());
                setData(can, d.withPaste(p));
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_rot", p.rot() * 90, Component.translatable("gui.nuclearsq.nanobots.mirror." + (p.mirror() ? 1 : 0))), true);
            }
            default -> {
                if (!NanoCanisterItem.task(can).gadget()) return;
                GadgetData d = data(can);
                int dir = Integer.signum(value);
                if (action == KEY_SIZE) {
                    if (GadgetData.isRound(d.shape())) setData(can, d.withRadius(d.radius() + dir));
                } else if (action == KEY_SHAPE) {
                    setData(can, d.withShape(d.shape() + (dir == 0 ? 1 : dir)));
                } else if (action == KEY_HEIGHT && d.shape() == GadgetData.CYLINDER) {
                    setData(can, d.withHeight(d.height() + dir));
                }
                GadgetData n = data(can);
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.gadget_size", Component.translatable("gui.nuclearsq.nanobots.shape." + n.shape()), n.radius(), n.height()), true);
            }
        }
    }
}
