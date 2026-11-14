package com.sqc.nuclearsq.nanobot.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Quaternionf;

import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.BotEntry;
import com.sqc.nuclearsq.nanobot.NanoSmooth;
import com.sqc.nuclearsq.nanobot.NanoSwarmPayload;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ClientSwarm {

    private enum Mode { WANDER, EXPLORE, HUDDLE, FOLLOW, CIRCLE, CLEAN, WORK, STALK }

    private enum Loco { PAUSE, ORIENT, MOVE }

    private static final class Bot {
        Surface f = Surface.of(Direction.UP);
        double x, y, z;
        double wx, wy, wz, px, py, pz, kx, ky, kz;
        final Quaternionf q = new Quaternionf(), qp = new Quaternionf();
        double v;
        float heading, prevHeading;
        float u, uRate, beta, meta, amp, tap, groom, pitch, prevPitch, climb, clock, work;
        float animOffset, animRate = 1f;
        double jx, jz;
        Mode mode = Mode.WANDER;
        int modeLeft;
        Loco loco = Loco.PAUSE;
        boolean dash;
        int timer = 5, moveLeft, tapLeft, groomLeft, micro, orientMin, climbLeft;
        int hidden;
        boolean travel;
        int avoidLeft, avoidDir = 1;
        String event = "";
        float eventClock;
        double climbTop;
        double segV;
        double tx, tz;
        boolean hasTarget;
        int follow = -1;
        float ringAngle, ringR, ringSpeed;
        @Nullable Direction workDir;
        @Nullable BlockPos apFor;
        int stuck;
        double htx, htz;
        int hintLeft, wallLeft;
        boolean descending;
        float pace, sprinty, nervous;
        @Nullable BlockPos target;
        int tProgress = -1, noTarget;
        float lastPlace = -1000, placedAt = -1000;
        int leaveDelay, enterT = -1;
        boolean entered;
        double enterDirX, enterDirZ;
        Vec3 enterStart;
    }

    public final UUID owner;
    private Vec3 toCenter;
    private final NanoSmooth.Follower cc = new NanoSmooth.Follower();
    private long lastPacket;
    private int state, lastState;
    private int pendingState;
    private long stateSince;
    private static final int MIN_DWELL = 8;
    private int jumps;
    boolean external;
    private long born = Long.MIN_VALUE;
    private final List<Bot> bots = new ArrayList<>();
    private final Random rnd;
    private long simTick;
    private volatile long visualFrame;
    private int invalidResets;
    private double speedFactor = 1.0;
    @Nullable private Vec3 ownerRef;
    private int leaveClock, nextEntry;
    private boolean finishing;
    private long finishingSince = -1;

    boolean leaving() {
        return state == 5;
    }

    boolean allEntered() {
        if (bots.isEmpty()) return false;
        for (Bot b : bots) if (!b.entered) return false;
        return true;
    }

    void finishing(long wallMs) {
        finishing = true;
        if (finishingSince < 0) finishingSince = wallMs;
    }

    boolean isFinishing() {
        return finishing;
    }

    ClientSwarm(UUID owner) {
        this.owner = owner;
        this.rnd = new Random(owner.getLeastSignificantBits() ^ System.nanoTime());
    }

    void update(NanoSwarmPayload p, long gameTime, long wallMs) {
        boolean firstPacket = toCenter == null;
        if (firstPacket) cc.snap(p.center());
        Vec3 shown = cc.pos();
        toCenter = p.center();
        lastPacket = wallMs;
        pendingState = p.state();
        if (firstPacket) {
            state = lastState = pendingState;
            stateSince = simTick;
        }
        speedFactor = Math.max(0.1, p.speed() / 10.0);
        if (born == Long.MIN_VALUE) born = gameTime;
        int n = p.count();
        while (bots.size() < n) {
            Bot b = new Bot();
            b.x = b.wx = b.px = shown.x + (rnd.nextDouble() - 0.5) * 0.2;
            b.z = b.wz = b.pz = shown.z + (rnd.nextDouble() - 0.5) * 0.2;
            b.y = b.wy = b.py = shown.y;
            b.heading = b.prevHeading = rnd.nextFloat() * Mth.TWO_PI;
            b.u = rnd.nextFloat() * Mth.TWO_PI;
            b.animOffset = rnd.nextFloat() * 100f;
            b.animRate = 0.9f + rnd.nextFloat() * 0.2f;
            b.beta = SpiderModel.DUTY_SLOW;
            b.pace = 0.75f + rnd.nextFloat() * 0.5f;
            b.sprinty = rnd.nextFloat();
            b.nervous = rnd.nextFloat();
            bots.add(b);
        }
        while (bots.size() > n) bots.remove(bots.size() - 1);
        for (int i = 0; i < n; i++) {
            Bot b = bots.get(i);
            BlockPos nt = p.targets()[i] == NanoSwarmPayload.NONE ? null : BlockPos.of(p.targets()[i]);
            if (nt == null && b.target != null && p.state() == 3 && ++b.noTarget < 6) continue;
            b.noTarget = 0;
            b.target = nt;
            b.tProgress = p.progress()[i];
        }
    }

    void tickSim(ClientLevel level, @Nullable Vec3 ownerPoint) {
        if (toCenter == null || bots.isEmpty()) return;
        simTick++;
        ownerRef = ownerPoint;
        cc.step(toCenter, 3.5);
        Vec3 anchor = cc.pos();
        if (pendingState != state && (pendingState == 5 || simTick - stateSince >= MIN_DWELL)) {
            state = pendingState;
            stateSince = simTick;
        }
        double base = Math.min(NuclearConfig.value(NuclearConfig.NANOBOT_BOT_SPEED) * speedFactor, 0.2);
        double catchUp = Math.min(Math.max(NuclearConfig.value(NuclearConfig.NANOBOT_SWARM_SPEED) * speedFactor * 1.4, base * 3.0), 1.2);
        boolean stateChanged = state != lastState;
        if (stateChanged && NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.DEBUG_BOT.get()) {
            NuclearSQ.LOGGER.info("[nanobots-debug] schwarm zustandswechsel {} -> {} (Modi aller Bots werden neu gewaehlt)", lastState, state);
        }
        lastState = state;
        net.minecraft.world.entity.player.Player leavePlayer = null;
        if (state == 5) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            leavePlayer = mc.level == null ? null : mc.level.getPlayerByUUID(owner);
            if (stateChanged) startLeave(leavePlayer);
            leaveClock++;
        }
        for (int i = 0; i < bots.size(); i++) {
            Bot b = bots.get(i);
            b.px = b.wx;
            b.py = b.wy;
            b.pz = b.wz;
            b.prevHeading = b.heading;
            b.prevPitch = b.pitch;
            b.qp.set(b.q);
            if (stateChanged || (state == 3 && (b.target != null) != (b.mode == Mode.WORK))) b.modeLeft = 0;
            if (--b.modeLeft <= 0) pickMode(b, i, anchor);
            Vec3 a = state == 5 && ownerPoint != null ? ownerPoint : anchor;
            if (state == 5 && leavePlayer != null) leaveBot(level, b, i, base, catchUp, leavePlayer);
            else step(level, b, i, a, base, catchUp);
            Vec3 w = b.f.toWorld(b.x + b.jx, b.y, b.z + b.jz);
            b.kx *= 0.62;
            b.ky *= 0.62;
            b.kz *= 0.62;
            b.wx = w.x + b.kx;
            b.wy = w.y + b.ky;
            b.wz = w.z + b.kz;
            b.q.slerp(b.f.quat, 0.34f);
            double jump = Math.sqrt((b.wx - b.px) * (b.wx - b.px) + (b.wy - b.py) * (b.wy - b.py) + (b.wz - b.pz) * (b.wz - b.pz));
            if (jump > 0.6) {
                jumps++;
                if (NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.DEBUG_BOT.get()) {
                    NuclearSQ.LOGGER.info("[nanobots-debug] SPRUNG bot {}: {} Block in einem Tick (zustand {} modus {} flaeche {}), letztes Ereignis '{}' vor {} Ticks",
                            i, String.format("%.2f", jump), state, b.mode, b.f.n, b.event, (int) (b.clock - b.eventClock));
                }
            }
        }
    }

    private void pickMode(Bot b, int i, Vec3 anchor) {
        int[] w;
        switch (state) {
            case 3 -> w = b.target != null ? new int[] {0, 0, 0, 0, 0, 0, 100, 0} : new int[] {60, 0, 40, 0, 0, 0, 0, 0};
            case 2, 4 -> w = new int[] {35, 0, 20, 35, 0, 0, 0, 0};
            case 5 -> w = new int[] {0, 0, 60, 40, 0, 0, 0, 0};
            default -> w = new int[] {25, 12, 10, 12, 8, 3, 0, 18};
        }
        int total = 0;
        for (int v : w) total += v;
        int r = rnd.nextInt(Math.max(1, total));
        int idx = 0;
        for (; idx < w.length - 1; idx++) {
            if (r < w[idx]) break;
            r -= w[idx];
        }
        Mode m = Mode.values()[idx];
        if (m == Mode.FOLLOW && bots.size() < 2) m = Mode.WANDER;
        b.mode = m;
        b.modeLeft = m == Mode.CLEAN ? 25 + rnd.nextInt(35) : 50 + rnd.nextInt(120);
        b.hasTarget = false;
        switch (m) {
            case FOLLOW -> {
                List<Integer> leaders = new ArrayList<>();
                for (int j = 0; j < bots.size(); j++) {
                    Bot o = bots.get(j);
                    if (j != i && (o.mode == Mode.EXPLORE || (o.loco == Loco.MOVE && o.dash))) leaders.add(j);
                }
                if (!leaders.isEmpty()) {
                    b.follow = leaders.get(rnd.nextInt(leaders.size()));
                    b.modeLeft = 30 + rnd.nextInt(40);
                } else {
                    do {
                        b.follow = rnd.nextInt(bots.size());
                    } while (b.follow == i);
                }
            }
            case CIRCLE, WORK -> {
                b.apFor = null;
                Vec3 la = b.f.toLocal(anchor);
                b.ringAngle = (float) Math.atan2(b.z - la.z, b.x - la.x);
                b.ringR = m == Mode.WORK ? (bots.size() == 1 ? 0.04f : 0.1f + rnd.nextFloat() * 0.3f) : 0.8f + rnd.nextFloat() * 0.7f;
                b.ringSpeed = (rnd.nextBoolean() ? 1 : -1) * (m == Mode.WORK ? (bots.size() == 1 ? 0f : 0.008f) : 0.015f + rnd.nextFloat() * 0.02f);
            }
            case CLEAN -> {
                b.groomLeft = b.modeLeft;
                b.loco = Loco.PAUSE;
                b.timer = b.modeLeft;
                b.v = 0;
            }
            default -> {}
        }
    }

    private void startLeave(@Nullable net.minecraft.world.entity.player.Player p) {
        leaveClock = 0;
        nextEntry = 0;
        Vec3 feet = p == null ? new Vec3(0, 0, 0) : p.position();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bots.size(); i++) order.add(i);
        order.sort(java.util.Comparator.comparingDouble(i -> new Vec3(bots.get(i).wx, bots.get(i).wy, bots.get(i).wz).distanceToSqr(feet)));
        int stagger = NuclearConfig.value(NuclearConfig.NANOBOT_ENTRY_STAGGER);
        for (int r = 0; r < order.size(); r++) {
            Bot b = bots.get(order.get(r));
            b.leaveDelay = r * stagger;
            b.enterT = -1;
            b.entered = false;
        }
    }

    private void leaveBot(ClientLevel level, Bot b, int i, double base, double catchUp, net.minecraft.world.entity.player.Player p) {
        if (b.entered) return;
        Vec3 feet = p.position();
        if (b.enterT >= 0) {
            enterStep(b, p, feet);
            return;
        }
        double ang = i * 2.399963;
        Vec3 slot = feet.add(Math.cos(ang) * 0.75, 0, Math.sin(ang) * 0.75);
        double dx = b.wx - feet.x, dz = b.wz - feet.z, horiz = Math.sqrt(dx * dx + dz * dz);
        double boost = horiz > 48 ? 3.0 : 1.0;
        step(level, b, i, slot, base * boost, catchUp * boost);
        if (leaveClock >= b.leaveDelay && simTick >= nextEntry && horiz < 1.6 && Math.abs(b.wy - feet.y) < 1.5 && b.f.isFloor()) {
            b.enterT = 0;
            nextEntry = (int) simTick + NuclearConfig.value(NuclearConfig.NANOBOT_ENTRY_STAGGER);
            b.enterStart = new Vec3(b.wx, b.wy, b.wz);
            if (horiz > 1e-3) {
                b.enterDirX = dx / horiz;
                b.enterDirZ = dz / horiz;
            } else {
                b.enterDirX = Math.cos(ang);
                b.enterDirZ = Math.sin(ang);
            }
        }
    }

    private void enterStep(Bot b, net.minecraft.world.entity.player.Player p, Vec3 feet) {
        b.enterT++;
        Vec3 arm = BotEntry.armPoint(feet, p.yBodyRot, p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        Vec3 w = BotEntry.position(Math.min(b.enterT, BotEntry.TOTAL), b.enterStart, feet, b.enterDirX, b.enterDirZ, arm);
        b.f = b.enterT > BotEntry.APPROACH ? Surface.of(Direction.getNearest(b.enterDirX, 0, b.enterDirZ)) : b.f;
        Vec3 loc = b.f.toLocal(w);
        b.x = loc.x;
        b.y = loc.y;
        b.z = loc.z;
        b.jx = b.jz = 0;
        b.kx = b.ky = b.kz = 0;
        b.climb = b.enterT > BotEntry.APPROACH ? 1f : b.climb;
        b.amp = 1f;
        b.uRate = 0.5f;
        b.u += b.uRate;
        if (b.enterT >= BotEntry.TOTAL) b.entered = true;
    }

    private void pickTarget(ClientLevel level, Bot b, int i, Vec3 la, boolean onFace) {
        if (b.hintLeft > 0) {
            b.tx = b.htx;
            b.tz = b.htz;
            b.hasTarget = true;
            return;
        }
        switch (b.mode) {
            case CIRCLE, WORK -> {
                b.ringAngle += b.ringSpeed;
                double rr = b.mode == Mode.WORK ? (onFace ? b.ringR + (bots.size() > 1 ? 0.06 * Math.sin(simTick * 0.3 - i) : 0) : 0) : b.ringR;
                b.tx = la.x + Math.cos(b.ringAngle) * rr;
                b.tz = la.z + Math.sin(b.ringAngle) * rr;
                b.hasTarget = true;
            }
            case FOLLOW -> {
                if (b.follow >= 0 && b.follow < bots.size()) {
                    Bot o = bots.get(b.follow);
                    Vec3 behind = b.f.toLocal(new Vec3(o.wx, o.wy, o.wz).subtract(o.f.heading(o.heading).scale(0.12)));
                    b.tx = behind.x;
                    b.tz = behind.z;
                    b.hasTarget = true;
                }
            }
            case CLEAN -> {}
            default -> {
                int tries = b.mode == Mode.WANDER && rnd.nextFloat() < 0.4f ? 5 : 1;
                boolean idleAtWork = state == 3 && b.target == null;
                if (idleAtWork) tries = 6;
                for (int k = 0; k < tries; k++) {
                    double ang = rnd.nextDouble() * Mth.TWO_PI, rad;
                    switch (b.mode) {
                        case EXPLORE -> rad = 2.2 + rnd.nextDouble() * 2.2;
                        case HUDDLE -> rad = rnd.nextDouble() * 0.3;
                        case STALK -> rad = 0.6 + rnd.nextDouble() * 1.4;
                        default -> rad = 0.3 + rnd.nextDouble() * (state == 1 ? 1.6 : 0.9);
                    }
                    if (idleAtWork) rad = 1.8 + rnd.nextDouble() * 1.4;
                    b.tx = la.x + Math.cos(ang) * rad;
                    b.tz = la.z + Math.sin(ang) * rad;
                    b.hasTarget = true;
                    if (idleAtWork) {
                        if (!nearOtherTarget(b, i)) break;
                        continue;
                    }
                    if (tries == 1 || nearWall(level, b, b.tx, b.tz)) break;
                }
            }
        }
    }

    private boolean nearOtherTarget(Bot b, int i) {
        Vec3 w = b.f.toWorld(b.tx, b.y, b.tz);
        for (int j = 0; j < bots.size(); j++) {
            BlockPos t = bots.get(j).target;
            if (j != i && t != null && Vec3.atCenterOf(t).distanceToSqr(w) < 1.6 * 1.6) return true;
        }
        return false;
    }

    private static double lift() {
        return -SpiderModel.FOOT_Y * SpiderModel.scale() + 0.003;
    }

    private static AABB boxAt(Surface f, double x, double y, double z) {
        double sc = SpiderModel.scale();
        return f.box(x, y, z, 0.12 * sc, SpiderModel.FOOT_Y * sc + 0.002, 0.07 * sc);
    }

    private static AABB boxOf(Bot b) {
        return boxAt(b.f, b.x, b.y, b.z);
    }

    private static Vec3 collide(ClientLevel level, Surface f, AABB box, Vec3 localMove) {
        Vec3 w = f.toWorld(localMove.x, localMove.y, localMove.z);
        return f.toLocal(Entity.collideBoundingBox(null, w, box, level, List.of()));
    }

    private static boolean collides(ClientLevel level, AABB box) {
        for (var shape : level.getBlockCollisions(null, box)) {
            if (shape != null) return true;
        }
        return false;
    }

    private static void settle(ClientLevel level, Bot b) {
        for (int k = 1; k <= 14; k++) {
            double d = 0.02 * k;
            double[][] offs = {{0, d, 0}, {0, 0, d}, {0, d, d}, {d, 0, 0}, {-d, 0, 0}, {0, 0, -d}, {d, d, 0}, {-d, d, 0}, {0, d, -d}};
            for (double[] o : offs) {
                if (!collides(level, boxAt(b.f, b.x + o[0], b.y + o[1], b.z + o[2]))) {
                    b.x += o[0];
                    b.y += o[1];
                    b.z += o[2];
                    return;
                }
            }
        }
    }

    private static boolean solidAt(ClientLevel level, Vec3 w) {
        BlockPos pos = BlockPos.containing(w);
        return level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static double surfaceLy(ClientLevel level, Surface f, double lx, double ly, double lz) {
        Vec3 w = f.toWorld(lx, ly, lz);
        Direction.Axis axis = f.n.getAxis();
        int s = f.sign();
        double[] c = {w.x, w.y, w.z};
        int ai = axis.ordinal();
        int idx = Mth.floor(c[ai]);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= 12; k++) {
            int cell = idx - s * k;
            m.set(ai == 0 ? cell : Mth.floor(c[0]), ai == 1 ? cell : Mth.floor(c[1]), ai == 2 ? cell : Mth.floor(c[2]));
            if (!level.hasChunkAt(m)) return Double.NaN;
            var shape = level.getBlockState(m).getCollisionShape(level, m);
            if (!shape.isEmpty()) {
                double surf = cell + (s > 0 ? shape.max(axis) : shape.min(axis));
                return s * surf + lift();
            }
        }
        return Double.NaN;
    }

    private static boolean freeRay(ClientLevel level, Surface f, Bot b, double off) {
        int sgn = off > 0 ? 1 : -1;
        for (double d = 0.25; d < Math.abs(off) - 0.05; d += 0.25) {
            if (solidAt(level, f.toWorld(b.x, b.y + sgn * d, b.z))) return false;
        }
        return true;
    }

    private void hint(ClientLevel level, Bot b, int ticks) {
        double best = 1e9, bx = b.x, bz = b.z;
        for (int k = 0; k < 4; k++) {
            double dx = Math.sin(k * Math.PI / 2), dz = Math.cos(k * Math.PI / 2);
            for (double d = 0.3; d <= 6; d += 0.25) {
                Vec3 w = b.f.toWorld(b.x + dx * d, b.y, b.z + dz * d);
                BlockPos p = BlockPos.containing(w);
                if (!level.hasChunkAt(p)) break;
                if (solidAt(level, w)) {
                    if (d < best) {
                        best = d;
                        bx = b.x + dx * (d + 0.1);
                        bz = b.z + dz * (d + 0.1);
                    }
                    break;
                }
            }
        }
        if (best < 1e8) {
            b.htx = bx;
            b.htz = bz;
            b.hintLeft = ticks;
            b.hasTarget = true;
            b.tx = bx;
            b.tz = bz;
        }
    }

    private boolean enter(ClientLevel level, Bot b, Direction nNew, boolean convex) {
        Surface old = b.f, nf = Surface.of(nNew);
        if (nNew == old.n || nNew == old.n.getOpposite()) return false;
        Vec3 oldW = old.toWorld(b.x, b.y, b.z);
        Vec3 nOld = Vec3.atLowerCornerOf(old.n.getNormal());
        Vec3 dir = Vec3.atLowerCornerOf(nNew.getNormal());
        Vec3 d = convex ? dir : dir.scale(-1);
        Vec3 fw = old.heading(b.heading);
        double a = fw.dot(d);
        Vec3 lat = fw.subtract(d.scale(a)).subtract(nOld.scale(fw.dot(nOld)));
        Vec3 fwNew = (convex ? nOld.scale(-a) : nOld.scale(a)).add(lat);
        Vec3 pw = convex ? oldW.subtract(nOld.scale(lift() + 0.03)) : oldW;
        double ox = b.x, oy = b.y, oz = b.z;
        Surface keep = b.f;
        b.f = nf;
        Vec3 lp = nf.toLocal(pw);
        b.x = lp.x;
        b.y = lp.y;
        b.z = lp.z;
        double sy = surfaceLy(level, nf, b.x, b.y, b.z);
        if (Double.isNaN(sy)) {
            b.f = keep;
            b.x = ox;
            b.y = oy;
            b.z = oz;
            return false;
        }
        b.y = sy;
        b.heading = (float) (fwNew.lengthSqr() > 1e-6 ? nf.headingOf(fwNew) : b.heading);
        b.prevHeading = b.heading;
        if (collides(level, boxOf(b))) settle(level, b);
        Vec3 newW = nf.toWorld(b.x, b.y, b.z);
        b.kx += oldW.x - newW.x;
        b.ky += oldW.y - newW.y;
        b.kz += oldW.z - newW.z;
        b.hasTarget = false;
        b.hintLeft = 0;
        b.climbLeft = 0;
        b.descending = false;
        b.wallLeft = 90 + rnd.nextInt(120);
        b.stuck = 0;
        return true;
    }

    private void note(Bot b, String what) {
        b.event = what;
        b.eventClock = b.clock;
        if (NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.DEBUG_BOT.get()) {
            NuclearSQ.LOGGER.info("[nanobots-debug] bot {} t{} zustand {} modus {}: {}", bots.indexOf(b), b.clock, state, b.mode, what);
        }
    }

    private void fall(Bot b) {
        Surface old = b.f;
        Vec3 w = old.toWorld(b.x, b.y, b.z);
        Vec3 fw = old.heading(b.heading);
        Surface up = Surface.of(Direction.UP);
        b.f = up;
        b.x = w.x;
        b.y = w.y;
        b.z = w.z;
        b.heading = (float) (Math.abs(fw.x) + Math.abs(fw.z) > 1e-6 ? up.headingOf(fw) : b.heading);
        b.prevHeading = b.heading;
        b.hasTarget = false;
        b.hintLeft = 0;
        b.climbLeft = 0;
        b.descending = false;
        b.stuck = 0;
    }

    private void place(ClientLevel level, Bot b, Vec3 w) {
        b.lastPlace = b.clock;
        b.placedAt = b.clock;
        note(b, "rueckstellung");
        b.f = Surface.of(Direction.UP);
        double sy = surfaceLy(level, b.f, w.x, w.y, w.z);
        b.x = w.x;
        b.z = w.z;
        b.y = Double.isNaN(sy) ? w.y : sy;
        b.v = 0;
        b.hasTarget = false;
        b.hintLeft = 0;
        b.climbLeft = 0;
        b.descending = false;
        b.stuck = 0;
        b.kx = b.ky = b.kz = 0;
        b.q.set(b.f.quat);
        b.qp.set(b.f.quat);
    }

    private void assignApproach(ClientLevel level, Bot b) {
        BlockPos t = b.target;
        b.apFor = t;
        b.workDir = null;
        double bestScore = -1e9;
        Vec3 bw = new Vec3(b.wx, b.wy, b.wz);
        for (Direction d : Direction.values()) {
            BlockPos n = t.relative(d);
            if (!level.hasChunkAt(n) || !level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
            Vec3 fc = Vec3.atCenterOf(t).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
            double score = -bw.distanceTo(fc) + rnd.nextDouble() * (bots.size() > 1 ? 4.0 : 0.3);
            if (d == b.f.n) score += 2.5;
            if (d == Direction.UP) score += 1.0;
            if (d == Direction.DOWN) score -= 1.5;
            if (score > bestScore) {
                bestScore = score;
                b.workDir = d;
            }
        }
    }

    private static boolean nearWall(ClientLevel level, Bot b, double tx, double tz) {
        double r = 0.3 + 0.08 * SpiderModel.scale();
        for (int k = 0; k < 4; k++) {
            Vec3 w = b.f.toWorld(tx + Math.sin(k * Math.PI / 2) * r, b.y, tz + Math.cos(k * Math.PI / 2) * r);
            BlockPos p = BlockPos.containing(w);
            if (level.hasChunkAt(p) && level.getBlockState(p).canOcclude()) return true;
        }
        return false;
    }

    private void step(ClientLevel level, Bot b, int i, Vec3 anchorW, double base, double catchUp) {
        b.clock++;
        double kk = SpiderModel.scale() / 0.45;
        double sc = SpiderModel.scale();
        if (collides(level, boxOf(b))) settle(level, b);
        boolean working = state == 3 && b.target != null && b.mode == Mode.WORK;
        if (working && !b.target.equals(b.apFor)) assignApproach(level, b);
        Vec3 workBase = working ? Vec3.atCenterOf(b.target) : anchorW;
        Vec3 aim = working && b.workDir != null ? workBase.add(b.workDir.getStepX() * 0.5, b.workDir.getStepY() * 0.5, b.workDir.getStepZ() * 0.5) : anchorW;
        if (state != 5 && new Vec3(b.wx, b.wy, b.wz).distanceToSqr(anchorW) > 16 * 16 && b.clock - b.lastPlace > 100) {
            place(level, b, anchorW.add((rnd.nextDouble() - 0.5), 0, (rnd.nextDouble() - 0.5)));
        }
        Surface f = b.f;
        if (!f.isFloor()) {
            double under = surfaceLy(level, f, b.x, b.y, b.z);
            if (Double.isNaN(under) || Math.abs(under - b.y) > 0.6) {
                fall(b);
                f = b.f;
            }
        }
        Vec3 la = f.toLocal(aim);
        double dxA = la.x - b.x, dzA = la.z - b.z;
        double distA = Math.sqrt(dxA * dxA + dzA * dzA);
        double contact = b.y + SpiderModel.FOOT_Y * sc;
        double off = la.y - contact;
        boolean onFace = working && b.workDir != null && b.workDir == f.n;
        double leash = state == 1 ? 3.4 : state == 3 ? 2.4 : 1.7;
        boolean atBlock = onFace && distA <= b.ringR + 0.35 && Math.abs(off) < 0.3;
        double enter = b.travel ? 0.4 : 0.8;
        boolean travel = b.mode != Mode.CLEAN && (state == 2 || state == 4 || state == 5 || b.hintLeft > 0 || (working && !atBlock && distA > enter)) && distA > enter;
        if (travel != b.travel) note(b, travel ? "anreise" : "angekommen");
        b.travel = travel;
        boolean intent = working || b.hintLeft > 0 || (travel && off > 0.6);

        if (!f.isFloor()) {
            if (!intent && --b.wallLeft <= 0 && !b.descending) {
                b.descending = true;
                if (f.ez == Direction.UP) {
                    b.htx = b.x;
                    b.htz = b.z - 8;
                    b.hintLeft = 160;
                    b.tx = b.htx;
                    b.tz = b.htz;
                    b.hasTarget = true;
                } else {
                    hint(level, b, 160);
                }
            }
        }
        if (working && b.workDir != null && Math.abs(off) > 0.45 && distA < 0.5 && b.hintLeft <= 0 && !solidAt(level, f.toWorld(la.x, b.y, la.z)) && freeRay(level, f, b, off)) hint(level, b, 120);

        if (!working && ((distA > leash && b.loco != Loco.MOVE) || (distA > leash + 0.6 && !b.dash))) {
            b.tx = la.x + (rnd.nextDouble() - 0.5) * 0.6;
            b.tz = la.z + (rnd.nextDouble() - 0.5) * 0.6;
            b.hasTarget = true;
            b.loco = Loco.MOVE;
            b.dash = true;
            b.segV = Math.min(catchUp, base * (3.0 + 2.0 * Math.min(1.0, (distA - leash) / 1.5)));
            b.moveLeft = 14 + rnd.nextInt(10);
            b.groomLeft = 0;
            b.tapLeft = 0;
        }
        if (b.hintLeft > 0) b.hintLeft--;
        if (b.mode == Mode.CIRCLE || b.mode == Mode.WORK || b.mode == Mode.FOLLOW || b.hintLeft > 0) pickTarget(level, b, i, la, onFace);
        if (travel) {
            if (!(b.mode == Mode.WORK || b.hintLeft > 0)) {
                b.tx = la.x + Math.cos(i * 2.4) * 0.3;
                b.tz = la.z + Math.sin(i * 2.4) * 0.3;
            }
            b.hasTarget = true;
            b.loco = Loco.MOVE;
            b.dash = false;
            b.moveLeft = 8;
            b.groomLeft = 0;
            b.tapLeft = 0;
            b.segV = Mth.clamp(base * b.pace + distA * 0.1, base * 0.8, catchUp);
        }

        switch (b.loco) {
            case PAUSE -> {
                b.v *= 0.5;
                if (atBlock) b.timer = Math.max(b.timer, 4);
                if (b.tapLeft > 0) b.tapLeft--;
                if (b.groomLeft > 0) b.groomLeft--;
                if (--b.timer <= 0) {
                    if (b.mode == Mode.CLEAN) {
                        b.modeLeft = 0;
                    } else {
                        if (!b.hasTarget) pickTarget(level, b, i, la, onFace);
                        beginMove(level, b, i, la, onFace, base);
                    }
                }
            }
            case ORIENT -> {
                double diff = Mth.wrapDegrees((float) (Math.atan2(b.tx - b.x, b.tz - b.z) - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                b.heading += Mth.clamp(diff, -0.2, 0.2);
                b.v *= 0.5;
                if (b.orientMin > 0) b.orientMin--;
                if (Math.abs(diff) < 0.2 && b.orientMin <= 0) b.loco = Loco.MOVE;
            }
            default -> {
                double dx = b.tx - b.x, dz = b.tz - b.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double want = Math.atan2(dx, dz);
                double diff = Mth.wrapDegrees((float) (want - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                double maxTurn = b.dash ? 0.054 : 0.12;
                for (int j = 0; j < bots.size(); j++) {
                    if (j == i) continue;
                    Bot o = bots.get(j);
                    Vec3 rel3 = f.toLocal(new Vec3(o.wx - b.wx, o.wy - b.wy, o.wz - b.wz));
                    double ox = rel3.x, oz = rel3.z;
                    double d = Math.sqrt(ox * ox + oz * oz);
                    if (d > 1e-6 && d < 0.22 * kk && Math.abs(rel3.y) < 0.2) {
                        double rel = Mth.wrapDegrees((float) (Math.atan2(ox, oz) - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                        if (Math.abs(rel) < 1.0) b.heading -= (float) (Math.copySign(0.12 * (1 - d / (0.22 * kk)), rel));
                    }
                }
                if (b.avoidLeft > 0) b.avoidLeft--;
                else b.heading += (float) Mth.clamp(diff, -maxTurn, maxTurn);
                double rest = Mth.wrapDegrees((float) (want - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                double align = b.avoidLeft > 0 ? 1.0 : Math.pow(Math.max(0.0, Math.cos(rest)), 2);
                double spd = b.segV * align;
                if (!b.dash && !b.travel) {
                    spd *= 1.0 + 0.35 * Math.sin(simTick * 0.37 + i * 1.9);
                    if (b.micro > 0) {
                        b.micro--;
                        spd = 0;
                    } else if (rnd.nextFloat() < 0.025f) {
                        b.micro = 3 + rnd.nextInt(5);
                    }
                }
                boolean ring = b.mode == Mode.CIRCLE || b.mode == Mode.WORK || b.mode == Mode.FOLLOW;
                if (!ring && dist < 0.25) spd *= Math.max(0.35, dist / 0.25);
                b.v += (spd - b.v) * (b.dash ? 0.6 : 0.35);
                if (--b.moveLeft <= 0 || (dist < 0.05 && !ring)) halt(b);
            }
        }

        double sepX = 0, sepZ = 0;
        for (int j = 0; j < bots.size(); j++) {
            if (j == i) continue;
            Bot o = bots.get(j);
            Vec3 rel3 = f.toLocal(new Vec3(b.wx - o.wx, b.wy - o.wy, b.wz - o.wz));
            double ox = rel3.x, oz = rel3.z;
            double d2 = ox * ox + oz * oz;
            if (d2 < 0.0324 * kk * kk && d2 > 1e-8 && Math.abs(rel3.y) < 0.2) {
                double d = Math.sqrt(d2);
                double fo = (0.18 * kk - d) / (0.18 * kk);
                sepX += ox / d * fo;
                sepZ += oz / d * fo;
            }
        }
        double mx = Math.sin(b.heading) * b.v + sepX * base * 0.5;
        double mz = Math.cos(b.heading) * b.v + sepZ * base * 0.5;
        Vec3 real = collide(level, f, boxOf(b), new Vec3(mx, 0, mz));
        double want = Math.sqrt(mx * mx + mz * mz), got = Math.sqrt(real.x * real.x + real.z * real.z);
        b.x += real.x;
        b.z += real.z;
        boolean moved = false;
        if (want > 1e-4 && got < want * 0.4) {
            double half = 0.12 * sc + 0.05;
            double ax = b.x + mx / want * half, az = b.z + mz / want * half;
            double step = surfaceLy(level, f, ax, b.y, az);
            if (!Double.isNaN(step) && step > b.y && step - b.y <= 0.7) {
                b.climbTop = step;
                b.climbLeft = 20;
                note(b, "stufe");
            } else {
                Vec3 blockedW = f.toWorld(mx - real.x, 0, mz - real.z);
                Direction hit = Direction.getNearest(blockedW.x, blockedW.y, blockedW.z);
                boolean allowed = intent || (b.wallLeft > 0 && !f.isFloor());
                if (allowed && hit.getAxis() != f.n.getAxis() && enter(level, b, hit.getOpposite(), false)) {
                    moved = true;
                    if (!intent) b.wallLeft = 90 + rnd.nextInt(120);
                } else if (b.travel) {
                    b.avoidDir = b.avoidLeft > 0 ? -b.avoidDir : (rnd.nextBoolean() ? 1 : -1);
                    b.avoidLeft = 18;
                    b.heading += b.avoidDir * 1.2f;
                    note(b, "hindernis: umgehen " + (b.avoidDir > 0 ? "rechts" : "links"));
                } else {
                    b.heading += 1.4f;
                    b.hasTarget = false;
                    note(b, "hindernis: abgedreht");
                    if (b.loco == Loco.MOVE) halt(b);
                }
            }
        }
        if (!moved && !b.f.isFloor() && b.v > base * 0.15) {
            Surface fs = b.f;
            double reach = 0.12 * sc + 0.07;
            double sx = b.x + Math.sin(b.heading) * reach, sz = b.z + Math.cos(b.heading) * reach;
            double ahead = surfaceLy(level, fs, sx, b.y, sz);
            if (!solidAt(level, fs.toWorld(sx, b.y, sz)) && (Double.isNaN(ahead) || Math.abs(ahead - b.y) > 0.6)) {
                Vec3 fw = fs.heading(b.heading);
                Direction around = Direction.getNearest(fw.x, fw.y, fw.z);
                if (around.getAxis() != fs.n.getAxis()) enter(level, b, around, true);
            }
        }
        f = b.f;
        for (int j = 0; j < i; j++) {
            Bot o = bots.get(j);
            Vec3 rel3 = f.toLocal(new Vec3(b.wx - o.wx, b.wy - o.wy, b.wz - o.wz));
            double ox = rel3.x, oz = rel3.z;
            double d = Math.sqrt(ox * ox + oz * oz);
            if (d > 1e-6 && d < 0.09 * kk && Math.abs(rel3.y) < 0.2) {
                double push = (0.09 * kk - d) / 2;
                Vec3 pb = collide(level, f, boxOf(b), new Vec3(ox / d * push, 0, oz / d * push));
                b.x += pb.x;
                b.z += pb.z;
                Surface fo = o.f;
                Vec3 lo = fo.toLocal(f.toWorld(-ox / d * push, 0, -oz / d * push));
                Vec3 po = collide(level, fo, boxOf(o), new Vec3(lo.x, 0, lo.z));
                o.x += po.x;
                o.z += po.z;
            }
        }

        Vec3 wc = b.f.toWorld(b.x, b.y, b.z);
        BlockPos cp = BlockPos.containing(wc);
        if (!Double.isFinite(wc.x + wc.y + wc.z)) {
            invalidResets++;
            place(level, b, anchorW);
        } else if (level.hasChunkAt(cp) && level.getBlockState(cp).isSolidRender(level, cp)) {
            if (++b.hidden > 12 && b.clock - b.lastPlace > 100) {
                Vec3 ref = ownerRef != null ? ownerRef : anchorW;
                place(level, b, new Vec3(ref.x + (rnd.nextDouble() - 0.5) * 0.6, ref.y, ref.z + (rnd.nextDouble() - 0.5) * 0.6));
                b.hidden = 0;
            }
        } else {
            b.hidden = 0;
        }
        if (collides(level, boxOf(b))) {
            if (++b.stuck > 30 && b.clock - b.lastPlace > 100) {
                Vec3 ref = ownerRef != null ? ownerRef : anchorW;
                place(level, b, new Vec3(ref.x + (rnd.nextDouble() - 0.5) * 0.6, ref.y, ref.z + (rnd.nextDouble() - 0.5) * 0.6));
            }
        } else {
            b.stuck = 0;
        }
        f = b.f;

        boolean idle = b.v < base * 0.08;
        if (b.work > 0.5f) {
            b.jx = (rnd.nextDouble() - 0.5) * 0.008 * kk;
            b.jz = (rnd.nextDouble() - 0.5) * 0.008 * kk;
        } else if (idle && b.loco != Loco.ORIENT && rnd.nextFloat() < 0.1f + 0.2f * b.nervous) {
            b.jx = (rnd.nextDouble() - 0.5) * 0.005;
            b.jz = (rnd.nextDouble() - 0.5) * 0.005;
            if (b.mode == Mode.CLEAN || b.mode == Mode.WORK) b.heading += (rnd.nextFloat() - 0.5f) * 0.3f;
        } else {
            b.jx *= 0.5;
            b.jz *= 0.5;
        }
        if (b.mode == Mode.WORK && b.loco == Loco.PAUSE) {
            Vec3 lc = f.toLocal(workBase);
            b.heading += Mth.wrapDegrees((float) (Math.atan2(lc.x - b.x, lc.z - b.z) - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * (0.15f + 0.25f * b.work);
        }

        double gy = surfaceLy(level, f, b.x, b.y, b.z);
        if (Double.isNaN(gy)) gy = b.y;
        double climbLim = 0.12;
        if (b.climbLeft > 0) {
            gy = Math.max(gy, b.climbTop);
            climbLim = Math.max(climbLim, Math.max(0.04, base * 2.0));
            if (b.y >= b.climbTop - 0.02) b.climbLeft = 0;
            else b.climbLeft--;
        }
        double dyWant = Mth.clamp(gy - b.y, -0.3, climbLim);
        double dy = collide(level, f, boxOf(b), new Vec3(0, dyWant, 0)).y;
        b.y += dy;
        float targetPitch = dy > 0.01 ? (float) Mth.clamp(Math.atan2(dy, Math.max(b.v, 0.02)), 0.0, 1.4) : 0f;
        b.pitch += (targetPitch - b.pitch) * 0.2f;
        float climbT = f.isFloor() ? (b.pitch > 0.3f ? 1f : 0f) : 0.7f;
        b.climb += (climbT - b.climb) * 0.2f;

        float sn = (float) Mth.clamp(b.v / (base * 5.0), 0.0, 1.0);
        b.beta = SpiderModel.duty(sn);
        double rate = Mth.clamp(Math.PI * b.beta * b.v / (SpiderModel.STRIDE * SpiderModel.scale()), 0.3, 6.0);
        b.meta = Mth.clamp((sn - 0.5f) * 2f, 0f, 1f);
        boolean walking = b.v > base * 0.1;
        if (b.loco == Loco.ORIENT) rate = 1.5;
        if (walking || b.loco == Loco.ORIENT) b.u += (float) rate;
        b.uRate = walking || b.loco == Loco.ORIENT ? (float) rate : 0f;
        float targetAmp = walking ? 1f : b.loco == Loco.ORIENT ? 0.6f : 0f;
        b.amp += (targetAmp - b.amp) * 0.4f;
        boolean paused = b.loco == Loco.PAUSE;
        b.work += ((atBlock && b.v < base * 0.2 ? 1f : 0f) - b.work) * 0.25f;
        b.tap += ((paused && b.tapLeft > 0 ? 1f : 0f) - b.tap) * 0.3f;
        b.groom += ((paused && b.groomLeft > 0 ? 1f : 0f) - b.groom) * 0.2f;
    }

    private void beginMove(ClientLevel level, Bot b, int i, Vec3 la, boolean onFace, double base) {
        double dashP = switch (b.mode) {
            case EXPLORE -> 0.85;
            case STALK -> 0.15;
            case WANDER -> 0.25;
            case FOLLOW -> 0.3;
            case HUDDLE -> 0.1;
            case WORK -> 0.05;
            default -> 0.0;
        };
        dashP *= (0.6 + 0.8 * b.sprinty) * 1.6;
        boolean dash = rnd.nextDouble() < dashP;
        if (b.mode == Mode.WORK && bots.size() == 1 && b.hasTarget && Math.hypot(b.tx - b.x, b.tz - b.z) > 0.5) dash = true;
        if (state == 2 || state == 4 || state == 5) dash = dash || rnd.nextFloat() < 0.35f;
        b.dash = dash;
        if (dash) {
            b.segV = base * (3.0 + 3.0 * rnd.nextDouble());
            b.moveLeft = 4 + rnd.nextInt(9);
        } else {
            double modeFactor = switch (b.mode) {
                case STALK -> 0.5;
                case CIRCLE -> 0.8;
                case WORK -> 0.7;
                default -> 1.0;
            };
            b.segV = base * b.pace * (0.3 + 0.6 * rnd.nextDouble()) * modeFactor;
            b.moveLeft = 12 + rnd.nextInt(35);
        }
        if (b.mode == Mode.CIRCLE) b.moveLeft = 40 + rnd.nextInt(60);
        if (b.mode == Mode.STALK) b.moveLeft = 8 + rnd.nextInt(10);
        if (b.hintLeft <= 0 && (b.mode == Mode.WANDER || b.mode == Mode.HUDDLE || b.mode == Mode.EXPLORE || b.mode == Mode.STALK) && rnd.nextFloat() < 0.55f) {
            b.hasTarget = false;
            pickTarget(level, b, i, la, onFace);
        }
        b.loco = Loco.MOVE;
        b.orientMin = 0;
        double want = b.hasTarget ? Math.atan2(b.tx - b.x, b.tz - b.z) : b.heading;
        if (Math.abs(Mth.wrapDegrees((float) (want - b.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD) > 0.5) {
            b.loco = Loco.ORIENT;
        } else if (dash) {
            b.loco = Loco.ORIENT;
            b.orientMin = 4 + rnd.nextInt(5);
        }
    }

    private void halt(Bot b) {
        b.v = b.dash ? 0 : b.v * 0.3;
        b.loco = Loco.PAUSE;
        b.tapLeft = b.dash ? 8 + rnd.nextInt(8) : 3 + rnd.nextInt(4);
        double median = b.mode == Mode.WORK ? 8 : 14;
        b.timer = (int) Mth.clamp(median * Math.exp(rnd.nextGaussian() * 0.6), 3, 140);
        boolean ring = b.mode == Mode.CIRCLE || b.mode == Mode.WORK || b.mode == Mode.FOLLOW;
        b.hasTarget = b.hasTarget && (ring || b.hintLeft > 0);
        if (rnd.nextFloat() < 0.03f + (b.mode == Mode.CLEAN ? 0.1f : 0f)) {
            b.groomLeft = 40 + rnd.nextInt(50);
            b.timer = Math.max(b.timer, b.groomLeft);
        }
        if (b.mode == Mode.STALK && rnd.nextFloat() < 0.5f) b.modeLeft = Math.min(b.modeLeft, 1);
    }

    public float yaw(int i, float partialTick) {
        if (i >= bots.size()) return 0f;
        Bot b = bots.get(i);
        return b.prevHeading + Mth.wrapDegrees((b.heading - b.prevHeading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * partialTick;
    }

    public float pitch(int i, float partialTick) {
        if (i >= bots.size()) return 0f;
        Bot b = bots.get(i);
        return Mth.lerp(partialTick, b.prevPitch, b.pitch);
    }

    public Quaternionf orientation(int i, float partialTick, Quaternionf out) {
        if (i >= bots.size()) return out.identity();
        Bot b = bots.get(i);
        out.set(b.qp).slerp(b.q, partialTick);
        return out.rotateY(yaw(i, partialTick)).rotateX(-pitch(i, partialTick));
    }

    public Vec3 normal(int i) {
        return i < bots.size() ? Vec3.atLowerCornerOf(bots.get(i).f.n.getNormal()) : Vec3.ZERO;
    }

    public void pose(int i, float partialTick, float[] out) {
        Bot b = bots.get(i);
        out[SpiderModel.P_U] = b.u + b.uRate * partialTick;
        out[SpiderModel.P_BETA] = b.beta;
        out[SpiderModel.P_META] = b.meta;
        out[SpiderModel.P_AMP] = b.amp;
        out[SpiderModel.P_TAP] = b.tap * (1f - b.work);
        out[SpiderModel.P_WORK] = b.work;
        out[SpiderModel.P_GROOM] = b.groom;
        out[SpiderModel.P_CLIMB] = b.climb;
        out[SpiderModel.P_CLOCK] = (b.clock + partialTick) * b.animRate + b.animOffset;
    }

    public float visibility(int i, long gameTime, float partialTick, @Nullable Vec3 ownerPoint) {
        float spawn = Mth.clamp((gameTime + partialTick - born - i * 1.5f) / 8f, 0f, 1f);
        spawn = spawn * spawn * (3f - 2f * spawn);
        float absorb = 1f;
        if (i < bots.size()) {
            Bot eb = bots.get(i);
            if (eb.entered) absorb = 0f;
            else if (eb.enterT >= 0) absorb = BotEntry.scale(eb.enterT);
        }
        float fade = i < bots.size() ? Mth.clamp((bots.get(i).clock - bots.get(i).placedAt) / 12f, 0f, 1f) : 1f;
        return spawn * absorb * fade;
    }


    @Nullable
    public BlockPos target(int i) {
        return i < bots.size() ? bots.get(i).target : null;
    }

    public int progress(int i) {
        return i < bots.size() ? bots.get(i).tProgress : -1;
    }

    public int jumps() {
        return jumps;
    }

    public boolean isWorking(int i) {
        return i < bots.size() && bots.get(i).work > 0.5f;
    }

    public AABB hitbox(int i, float partialTick) {
        Bot b = bots.get(i);
        return boxAt(b.f, b.x, b.y, b.z).move(b.kx, b.ky, b.kz);
    }

    public int count() {
        return bots.size();
    }

    public void markVisualFrame(long gameTime) {
        visualFrame = gameTime;
    }

    public boolean visualAlive(long gameTime) {
        return external && (gameTime - visualFrame <= 20 || gameTime - born < 30);
    }

    public boolean invalid(int i, float partialTick) {
        if (i >= bots.size()) return true;
        Bot b = bots.get(i);
        if (!Double.isFinite(b.wx + b.wy + b.wz + b.px + b.py + b.pz)) return true;
        Quaternionf q = orientation(i, partialTick, new Quaternionf());
        float n = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
        return !Float.isFinite(n) || Math.abs(n - 1f) > 0.01f;
    }

    public String debugLine(int i, ClientLevel level, long gameTime, @Nullable Vec3 ownerPoint) {
        Bot b = bots.get(i);
        Vec3 p = bot(i, gameTime, 0f);
        BlockPos bp = BlockPos.containing(p);
        Quaternionf q = orientation(i, 0f, new Quaternionf());
        return String.format("bot %d: pos (%.2f %.2f %.2f) flaeche %s mode %s loco %s hidden %d licht %d sichtbar %.2f block %s ausrichtung (%.2f %.2f %.2f %.2f) ungueltig %b anreise %b v %.3f ereignis '%s' (vor %.0f Ticks)",
                i, p.x, p.y, p.z, b.f.n, b.mode, b.loco, b.hidden, SwarmFx.lightAt(level, p), visibility(i, gameTime, 0f, ownerPoint),
                level.getBlockState(bp).getBlock().getDescriptionId(), q.x, q.y, q.z, q.w, invalid(i, 0f), b.travel, b.v, b.event, b.clock - b.eventClock);
    }

    public int invalidResets() {
        return invalidResets;
    }

    public int state() {
        return state;
    }

    long lastPacket() {
        return lastPacket;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public Vec3 center(long gameTime, float partialTick) {
        if (toCenter == null) return Vec3.ZERO;
        return cc.lerp(partialTick);
    }

    public Vec3 bot(int i, long gameTime, float partialTick) {
        Bot b = bots.get(i);
        return new Vec3(Mth.lerp(partialTick, b.px, b.wx), Mth.lerp(partialTick, b.py, b.wy), Mth.lerp(partialTick, b.pz, b.wz));
    }
}
