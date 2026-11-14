package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.BlastJob;
import com.sqc.nuclearsq.explosion.BlastJobs;
import com.sqc.nuclearsq.explosion.FalloutScatterJob;
import com.sqc.nuclearsq.explosion.NuclearDetonationEvent;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BlastJobTests {
    private BlastJobTests() {}

    private static final String FLOOR = "floor";

    private static Tag pos(BlockPos p) {
        return BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
    }

    private static Tag vec(Vec3 v) {
        return Vec3.CODEC.encodeStart(NbtOps.INSTANCE, v).getOrThrow();
    }

    private static List<CompoundTag> sampleJobs() {
        List<CompoundTag> out = new ArrayList<>();
        BlockPos c = new BlockPos(100, 64, -40);
        Vec3 cv = new Vec3(100.5, 64.25, -39.5);

        CompoundTag crater = new CompoundTag();
        crater.putString("type", "crater");
        crater.put("center", pos(c));
        crater.putInt("radius", 6);
        crater.putInt("section", 2);
        out.add(crater);

        CompoundTag burn = new CompoundTag();
        burn.putString("type", "burn");
        burn.put("center", pos(c));
        burn.putInt("inner", 8);
        burn.putInt("width", 5);
        burn.putInt("index", 17);
        out.add(burn);

        CompoundTag shock = new CompoundTag();
        shock.putString("type", "shock");
        shock.put("center", vec(cv));
        shock.put("blast", NuclearExplosion.Blast.CODEC.encodeStart(NbtOps.INSTANCE,
                new NuclearExplosion.Blast(5, 0.1f, 20f, 30.0, 40, true)).getOrThrow());
        ListTag hit = new ListTag();
        hit.add(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, new UUID(1, 2)).getOrThrow());
        hit.add(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, new UUID(3, 4)).getOrThrow());
        shock.put("hit", hit);
        shock.putInt("age", 3);
        out.add(shock);

        CompoundTag shatter = new CompoundTag();
        shatter.putString("type", "shatter");
        shatter.put("center", pos(c));
        shatter.putInt("radius", 4);
        shatter.putInt("range", 40);
        shatter.putInt("index", 3);
        shatter.putInt("age", 2);
        out.add(shatter);

        CompoundTag glow = new CompoundTag();
        glow.putString("type", "glow");
        glow.put("center", pos(c));
        glow.putInt("radius", 6);
        glow.putInt("index", 50);
        glow.putInt("age", 12);
        out.add(glow);

        CompoundTag ash = new CompoundTag();
        ash.putString("type", "ash");
        ash.put("center", vec(cv));
        ash.putInt("inner", 10);
        ash.putInt("range", 60);
        ash.putInt("age", 99);
        out.add(ash);

        CompoundTag fallout = new CompoundTag();
        fallout.putString("type", "fallout");
        fallout.put("center", vec(cv));
        fallout.putInt("inner", 8);
        fallout.putInt("range", 90);
        fallout.putInt("count", 700);
        fallout.putInt("placed", 21);
        fallout.putInt("tries", 33);
        out.add(fallout);
        return out;
    }

    @GameTest(template = FLOOR, batch = "blastjob_codec", timeoutTicks = 20)
    public static void everyJobTypeRoundTrips(GameTestHelper h) {
        List<String> types = new ArrayList<>();
        for (CompoundTag tag : sampleJobs()) {
            BlastJob job = BlastJobs.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(GameTestAssertException::new);
            String type = tag.getString("type");
            if (!job.type().id().equals(type)) throw new GameTestAssertException("type " + type + " loaded as " + job.type().id());
            Tag again = BlastJobs.CODEC.encodeStart(NbtOps.INSTANCE, job).getOrThrow(GameTestAssertException::new);
            if (!tag.equals(again)) throw new GameTestAssertException("job " + type + " changed: " + tag + " -> " + again);
            types.add(type);
        }
        if (types.size() != 7) throw new GameTestAssertException("expected 7 job types, got " + types);
        Tag fresh = BlastJobs.CODEC.encodeStart(NbtOps.INSTANCE, new FalloutScatterJob(new Vec3(1, 2, 3), 4, 20, 50)).getOrThrow();
        Tag back = BlastJobs.CODEC.encodeStart(NbtOps.INSTANCE,
                BlastJobs.CODEC.parse(NbtOps.INSTANCE, fresh).getOrThrow()).getOrThrow();
        if (!fresh.equals(back)) throw new GameTestAssertException("fresh fallout job changed: " + fresh + " -> " + back);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "blastjob_saveddata", timeoutTicks = 20)
    public static void savedDataRoundTrips(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        ListTag list = new ListTag();
        list.addAll(sampleJobs());
        CompoundTag saved = new CompoundTag();
        saved.put("jobs", list);

        CompoundTag withJunk = saved.copy();
        CompoundTag junk = new CompoundTag();
        junk.putString("type", "no_such_job");
        withJunk.getList("jobs", Tag.TAG_COMPOUND).add(junk);

        BlastJobs data = BlastJobs.load(withJunk, registries);
        if (data.size() != list.size()) throw new GameTestAssertException("loaded " + data.size() + " jobs, expected " + list.size());
        CompoundTag again = data.save(new CompoundTag(), registries);
        if (!saved.equals(again)) throw new GameTestAssertException("saved data changed: " + saved + " -> " + again);
        CompoundTag third = BlastJobs.load(again, registries).save(new CompoundTag(), registries);
        if (!saved.equals(third)) throw new GameTestAssertException("second round trip changed the data");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "blastjob_cancel", timeoutTicks = 40)
    public static void cancelledDetonationChangesNothing(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7);
        BlockPos center = h.absolutePos(mid);
        h.setBlock(new BlockPos(7, 4, 9), Blocks.GLASS);
        h.setBlock(new BlockPos(9, 3, 7), Blocks.GRASS_BLOCK);
        ItemEntity item = h.spawnItem(Items.DIAMOND, new BlockPos(7, 4, 7));
        item.setNoGravity(true);
        AABB bounds = h.getBounds();
        List<BlockState> before = snapshot(h, bounds);

        boolean[] seen = {false};
        Consumer<NuclearDetonationEvent> veto = e -> {
            if (e.getLevel() != h.getLevel() || !bounds.contains(e.getCenter())) return;
            seen[0] = true;
            e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, NuclearDetonationEvent.class, veto);
        try {
            NuclearExplosion.detonate(h.getLevel(), Vec3.atCenterOf(center), new NuclearExplosion.Blast(3, 0.01f, 5f, 8.0, 0, false));
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
        }
        if (!seen[0]) throw new GameTestAssertException("NuclearDetonationEvent was not posted");
        if (item.isRemoved()) throw new GameTestAssertException("item vaporized although the detonation was cancelled");
        h.runAfterDelay(20, () -> {
            List<BlockState> after = snapshot(h, bounds);
            for (int i = 0; i < before.size(); i++)
                if (before.get(i) != after.get(i))
                    throw new GameTestAssertException("block changed after a cancelled detonation: " + before.get(i) + " -> " + after.get(i));
            if (item.isRemoved()) throw new GameTestAssertException("item removed after a cancelled detonation");
            h.succeed();
        });
    }

    private static List<BlockState> snapshot(GameTestHelper h, AABB bounds) {
        List<BlockState> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1)))
            out.add(h.getLevel().getBlockState(p));
        return out;
    }

    @GameTest(template = FLOOR, batch = "blastjob_fallout", timeoutTicks = 100)
    public static void falloutLandsBelowLeaves(GameTestHelper h) {
        int canopy = 7, ground = 4;
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 14; z++) h.setBlock(new BlockPos(x, canopy, z), leaves);
        List<BlockPos> ceiling = new ArrayList<>();
        int top = (int) Math.ceil(h.getBounds().maxY) + 2;
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 14; z++)
                for (int y = canopy + 1; h.absolutePos(new BlockPos(x, y, z)).getY() <= top; y++) {
                    BlockPos a = h.absolutePos(new BlockPos(x, y, z));
                    if (h.getLevel().getBlockState(a).is(Blocks.BARRIER)) {
                        ceiling.add(a);
                        h.getLevel().setBlock(a, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7, ground, 7)));
        BlastJobs.add(h.getLevel(), new FalloutScatterJob(c, 0, 6, 20));
        var server = h.getLevel().getServer();
        h.succeedWhen(() -> {
            if (BlastJobs.pending(server, FalloutScatterJob.class)) throw new GameTestAssertException("fallout still scattering");
            int below = 0;
            List<BlockPos> found = new ArrayList<>();
            for (int x = 0; x <= 14; x++)
                for (int y = 0; y <= 11; y++)
                    for (int z = 0; z <= 14; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!h.getBlockState(p).is(CatalogRegistry.FALLOUT.get())) continue;
                        found.add(p);
                        if (h.getBlockState(p.below()).is(Blocks.OAK_LEAVES))
                            throw new GameTestAssertException("fallout on leaves at " + p);
                        if (y == ground) below++;
                    }
            for (BlockPos p : found) h.setBlock(p, Blocks.AIR);
            for (BlockPos p : ceiling) h.getLevel().setBlock(p, Blocks.BARRIER.defaultBlockState(), 2);
            if (below == 0) throw new GameTestAssertException("no fallout under the canopy (" + found.size() + " placed)");
        });
    }
}
