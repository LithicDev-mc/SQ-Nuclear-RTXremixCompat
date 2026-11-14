package com.sqc.nuclearsq.client;

import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigShellBlock;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class OcclusionCull {
    private OcclusionCull() {}

    private static final int MAX_DIST = 560, MAX_CACHE = 16384, RECHECK_TICKS = 20, RECHECK_HIDDEN_TICKS = 1, RECHECK_MOVED_TICKS = 8;
    private static final long BUDGET_NANOS = 2_500_000L;

    private static final class State {
        boolean visible = true;
        boolean tested;
        long tick;
        double cx, cy, cz;
    }

    private static final Long2ObjectOpenHashMap<State> CACHE = new Long2ObjectOpenHashMap<>();
    private static Level cacheLevel;
    private static long spent;
    private static boolean starvedLast, starvedNow;
    private static final long STARVED_MIN_AGE = 30;
    private static int tests, testsHidden, skippedBudget;
    public static volatile int snapTests, snapHidden, snapBudget;
    private static int rInside, rFar, rCached, rRay, rHiddenCached;
    public static volatile String snapReasons = "";
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        spent = 0;
        com.sqc.nuclearsq.compat.iris.IrisCompat.newFrame();
        starvedLast = starvedNow;
        starvedNow = false;
        snapTests = tests;
        snapHidden = testsHidden;
        snapBudget = skippedBudget;
        tests = testsHidden = skippedBudget = 0;
        if (PerfProbe.on) snapReasons = "Kamera in Box " + rInside + ", zu weit " + rFar + ", gemerkt sichtbar " + rCached + ", Strahl frei " + rRay + ", gemerkt verdeckt " + rHiddenCached;
        rInside = rFar = rCached = rRay = rHiddenCached = 0;
    }

    public static void clear() {
        CACHE.clear();
        cacheLevel = null;
    }

    public static boolean visibleBig(Level level, BlockPos key, AABB box) {
        return inFrustum(box) && visible(level, key, box);
    }

    public static boolean inFrustum(AABB box) {
        try {
            return Minecraft.getInstance().levelRenderer.getFrustum().isVisible(box);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean visible(Level level, BlockPos key, AABB box) {
        if (level == null || !NuclearClientConfig.occlusionCulling()) return true;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.x >= box.minX - 1.5 && cam.x < box.maxX + 1.5 && cam.y >= box.minY - 1.5 && cam.y < box.maxY + 1.5
                && cam.z >= box.minZ - 1.5 && cam.z < box.maxZ + 1.5) {
            rInside++;
            return true;
        }
        double mx = (box.minX + box.maxX) * 0.5 - cam.x, my = (box.minY + box.maxY) * 0.5 - cam.y, mz = (box.minZ + box.maxZ) * 0.5 - cam.z;
        double reach = box.getSize() * 0.5 + MAX_DIST;
        if (mx * mx + my * my + mz * mz > reach * reach) {
            rFar++;
            return true;
        }
        if (level != cacheLevel || CACHE.size() > MAX_CACHE) {
            CACHE.clear();
            cacheLevel = level;
        }
        long now = level.getGameTime();
        long k = key.asLong();
        State s = CACHE.get(k);
        if (s == null) {
            s = new State();
            CACHE.put(k, s);
        }
        double mv = (cam.x - s.cx) * (cam.x - s.cx) + (cam.y - s.cy) * (cam.y - s.cy) + (cam.z - s.cz) * (cam.z - s.cz);
        long age = now - s.tick;
        boolean stale = !s.tested || now < s.tick || age >= RECHECK_TICKS
                || (mv > 0.25 && age >= RECHECK_MOVED_TICKS) || (!s.visible && mv > 0.04 && age >= RECHECK_HIDDEN_TICKS);
        if (stale && starvedLast && s.tested && age < STARVED_MIN_AGE) stale = false;
        if (stale && spent >= BUDGET_NANOS) {
            starvedNow = true;
            skippedBudget++;
        }
        if (stale && spent < BUDGET_NANOS) {
            long t0 = System.nanoTime();
            s.visible = test(level, cam, box);
            if (!s.visible && mx * mx + my * my + mz * mz < NEAR_DIST * NEAR_DIST && peek(level, cam, box)) s.visible = true;
            if (s.visible) rRay++;
            tests++;
            if (!s.visible) testsHidden++;
            spent += System.nanoTime() - t0;
            s.tested = true;
            s.tick = now;
            s.cx = cam.x;
            s.cy = cam.y;
            s.cz = cam.z;
        }
        if (!(stale && spent < BUDGET_NANOS)) {
            if (s.visible) rCached++;
            else rHiddenCached++;
        }
        return s.visible;
    }

    private static final double NEAR_DIST = 112, PEEK = 1.5;

    private static boolean peek(Level level, Vec3 cam, AABB b) {
        double cx = (b.minX + b.maxX) * 0.5, cy = (b.minY + b.maxY) * 0.5, cz = (b.minZ + b.maxZ) * 0.5;
        double iy = (b.maxY - b.minY) * 0.4;
        double[][] off = {{PEEK, 0}, {-PEEK, 0}, {0, PEEK}, {0, -PEEK}};
        for (double[] o : off) {
            Vec3 p = new Vec3(cam.x + o[0], cam.y, cam.z + o[1]);
            if (b.contains(p)) return true;
            BlockState st = level.getBlockState(MUT.set(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z)));
            if (st.canOcclude() && st.isSolidRender(level, MUT)) continue;
            if (ray(level, p, b, cx, cy, cz) || ray(level, p, b, cx, cy + iy, cz)) return true;
        }
        return false;
    }

    private static boolean test(Level level, Vec3 cam, AABB b) {
        double cx = (b.minX + b.maxX) * 0.5, cy = (b.minY + b.maxY) * 0.5, cz = (b.minZ + b.maxZ) * 0.5;
        double ix = (b.maxX - b.minX) * 0.4, iy = (b.maxY - b.minY) * 0.4, iz = (b.maxZ - b.minZ) * 0.4;
        return ray(level, cam, b, cx, cy, cz)
                || ray(level, cam, b, cx + ix, cy, cz) || ray(level, cam, b, cx - ix, cy, cz)
                || ray(level, cam, b, cx, cy, cz + iz) || ray(level, cam, b, cx, cy, cz - iz)
                || ray(level, cam, b, cx, cy + iy, cz) || ray(level, cam, b, cx, cy - iy, cz);
    }

    private static boolean ray(Level level, Vec3 cam, AABB b, double tx, double ty, double tz) {
        double dx = tx - cam.x, dy = ty - cam.y, dz = tz - cam.z;
        double t0 = 0, t1 = 1;
        double[] d = {dx, dy, dz}, o = {cam.x, cam.y, cam.z}, lo = {b.minX, b.minY, b.minZ}, hi = {b.maxX, b.maxY, b.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (o[i] < lo[i] || o[i] > hi[i]) return true;
            } else {
                double a = (lo[i] - o[i]) / d[i], c = (hi[i] - o[i]) / d[i];
                t0 = Math.max(t0, Math.min(a, c));
                t1 = Math.min(t1, Math.max(a, c));
            }
        }
        if (t0 > t1) return true;
        int x = (int) Math.floor(cam.x), y = (int) Math.floor(cam.y), z = (int) Math.floor(cam.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tmx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 : x) - cam.x) / dx;
        double tmy = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 : y) - cam.y) / dy;
        double tmz = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 : z) - cam.z) / dz;
        double tdx = dx == 0 ? Double.MAX_VALUE : sx / dx, tdy = dy == 0 ? Double.MAX_VALUE : sy / dy, tdz = dz == 0 ? Double.MAX_VALUE : sz / dz;
        BlockState st0 = level.getBlockState(MUT.set(x, y, z));
        if (st0.getBlock() instanceof BigShellBlock && !b.contains(x + 0.5, y + 0.5, z + 0.5)) {
            VoxelShape sh0 = st0.getCollisionShape(level, MUT);
            if (!sh0.isEmpty() && sh0.clip(cam, new Vec3(tx, ty, tz), MUT) != null) return false;
        }
        for (int steps = 0; steps < 1700; steps++) {
            double t;
            if (tmx <= tmy && tmx <= tmz) {
                t = tmx;
                tmx += tdx;
                x += sx;
            } else if (tmy <= tmz) {
                t = tmy;
                tmy += tdy;
                y += sy;
            } else {
                t = tmz;
                tmz += tdz;
                z += sz;
            }
            if (t >= t0) return true;
            BlockState st = level.getBlockState(MUT.set(x, y, z));
            if (st.canOcclude() && st.isSolidRender(level, MUT)) return false;
            if (st.getBlock() instanceof BigShellBlock sb && !b.contains(x + 0.5, y + 0.5, z + 0.5)) {
                VoxelShape sh = st.getCollisionShape(level, MUT);
                if (!sh.isEmpty() && sh.clip(cam, new Vec3(tx, ty, tz), MUT) != null) return false;
            }
        }
        return true;
    }
}
