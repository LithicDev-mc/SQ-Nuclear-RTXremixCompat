package com.sqc.nuclearsq.oil;

import com.sqc.nuclearsq.NuclearSQ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class OilClearJob {
    private OilClearJob() {}

    private static final int PER_TICK = 8;
    private static final int REPORT_TICKS = 100;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    @Nullable
    private static Job job;

    private static final class Job {
        final ArrayDeque<Long> queue;
        final CommandSourceStack source;
        final int total;
        int checked, wells, blocks, ticks;

        Job(ArrayDeque<Long> queue, CommandSourceStack source) {
            this.queue = queue;
            this.source = source;
            this.total = queue.size();
        }
    }

    public static boolean running() {
        return job != null;
    }

    public static int startRadius(ServerLevel level, CommandSourceStack source, ChunkPos center, int radius) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long seed = level.getSeed();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ChunkPos cp = new ChunkPos(x, z);
                if (OilWellFeature.plan(seed, OilWellFeature.LEGACY_REGION, cp) != null) queue.add(cp.toLong());
            }
        }
        return start(source, queue);
    }

    public static int startAll(ServerLevel level, CommandSourceStack source) {
        MinecraftServer server = level.getServer();
        Path dir = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("region");
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long seed = level.getSeed();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    Matcher m = REGION_FILE.matcher(f.getFileName().toString());
                    if (!m.matches()) continue;
                    int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
                    for (int x = rx * 32; x < rx * 32 + 32; x++) {
                        for (int z = rz * 32; z < rz * 32 + 32; z++) {
                            ChunkPos cp = new ChunkPos(x, z);
                            if (OilWellFeature.plan(seed, OilWellFeature.LEGACY_REGION, cp) != null) queue.add(cp.toLong());
                        }
                    }
                }
            } catch (IOException e) {
                NuclearSQ.LOGGER.warn("Region folder could not be read: {}", dir, e);
            }
        }
        return start(source, queue);
    }

    private static int start(CommandSourceStack source, ArrayDeque<Long> queue) {
        if (job != null) {
            source.sendFailure(Component.translatable("commands.nuclearsq.oil.busy"));
            return 0;
        }
        if (OilWellFeature.region() == OilWellFeature.LEGACY_REGION) {
            source.sendFailure(Component.translatable("commands.nuclearsq.oil.legacy"));
            return 0;
        }
        job = new Job(queue, source);
        int n = queue.size();
        source.sendSuccess(() -> Component.translatable("commands.nuclearsq.oil.started", n), true);
        if (n == 0) finish(false);
        return Math.max(1, n);
    }

    public static int stop(CommandSourceStack source) {
        if (job == null) {
            source.sendFailure(Component.translatable("commands.nuclearsq.oil.idle"));
            return 0;
        }
        finish(true);
        return 1;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Job j = job;
        if (j == null || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (int i = 0; i < PER_TICK; i++) {
            Long key = j.queue.poll();
            if (key == null) {
                finish(false);
                return;
            }
            j.checked++;
            ChunkPos cp = new ChunkPos(key);
            if (!level.hasChunk(cp.x, cp.z) && !existsOnDisk(level, cp)) continue;
            level.getChunk(cp.x, cp.z);
            int n = OilWellCleanup.clean(level, cp);
            if (n > 0) {
                j.wells++;
                j.blocks += n;
            }
        }
        if (++j.ticks % REPORT_TICKS == 0) {
            int c = j.checked, t = j.total, w = j.wells;
            j.source.sendSuccess(() -> Component.translatable("commands.nuclearsq.oil.progress", c, t, w), false);
        }
    }

    private static boolean existsOnDisk(ServerLevel level, ChunkPos cp) {
        try {
            Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(cp).join();
            if (tag.isEmpty()) return false;
            String status = tag.get().getString("Status");
            return status.equals("minecraft:full") || status.equals("full");
        } catch (RuntimeException e) {
            NuclearSQ.LOGGER.warn("Chunk {} could not be read", cp, e);
            return false;
        }
    }

    private static void finish(boolean stopped) {
        Job j = job;
        job = null;
        if (j == null) return;
        int c = j.checked, w = j.wells, b = j.blocks;
        String key = stopped ? "commands.nuclearsq.oil.stopped" : "commands.nuclearsq.oil.done";
        j.source.sendSuccess(() -> Component.translatable(key, w, b, c), true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        job = null;
    }
}
