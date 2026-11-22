package com.sqc.nuclearsq;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class NuclearConfig {
    private NuclearConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("explosion");
    }

    public static final ModConfigSpec.DoubleValue REACTOR_YIELD = BUILDER
            .comment("Yield Y of a reactor explosion. All radii follow R = c * cbrt(Y): crater c=35, fireball c=18,",
                    "light damage c=75, fallout 150 * Y^0.4. Y=2.9 gives a crater radius of 50 blocks (volume grows linearly with Y).",
                    "The crater radius is capped at 320 blocks; blocks are removed with a per-tick budget.")
            .defineInRange("reactorYield", 2.9, 0.001, 150.0);

    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = BUILDER
            .comment("If false, explosions only deal damage and effects, no blocks are removed.",
                    "Claim and protection mods that take a chunk out of the normal explosion event keep that chunk untouched.")
            .define("destroyBlocks", true);

    public static final ModConfigSpec.BooleanValue ASH_LAYER = BUILDER
            .comment("After ~20 s a thin layer of ash settles around the crater and stays (not radioactive).")
            .define("ashLayer", true);

    public static final ModConfigSpec.IntValue CRATER_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum number of crater blocks removed per server tick (all explosions together).",
                    "Blocks at the crater edge and special blocks (block entities, light sources) count 4 times.")
            .defineInRange("craterBlocksPerTick", 80000, 1000, 1000000);

    public static final ModConfigSpec.IntValue FALLOUT_MAX_RADIUS = BUILDER
            .comment("Fallout reaches 150 * Y^0.4 blocks, but never further than this (blocks). Fallout blocks are placed a few per tick.")
            .defineInRange("falloutMaxRadius", 500, 10, 2000);

    public static final ModConfigSpec.IntValue TICK_BUDGET_MS = BUILDER
            .comment("Maximum time per server tick spent removing crater blocks (milliseconds, a tick has 50).")
            .defineInRange("tickBudgetMs", 10, 2, 40);

    public static final ModConfigSpec.IntValue CHUNK_LOADS = BUILDER
            .comment("Most chunks one explosion asks the server to load (or generate) at the same time for its crater and burn zone.",
                    "A Tsar Bomba touches about 4000 chunks. Lower = smaller lag spikes with expensive world generation, the crater takes longer.")
            .defineInRange("chunkLoadsAtOnce", 8, 1, 64);

    public static final ModConfigSpec.BooleanValue ANNOUNCE_MELTDOWN = BUILDER
            .comment("A reactor meltdown and a reactor explosion are announced in chat to all players on the server, with coordinates.")
            .define("announceMeltdown", true);

    static {
        BUILDER.pop();
        BUILDER.push("bomb");
    }

    public static final ModConfigSpec.BooleanValue BOMBS_ENABLED = BUILDER
            .comment("Nuclear bombs can be armed and explode. Off: arming is refused, bombs that are already armed fizzle without an explosion.",
                    "Reactor explosions are not affected (see explosion.destroyBlocks).")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue BOMB_YIELD = BUILDER
            .comment("Yield Y of the small nuclear bomb (1x1). Default 10 = crater radius 75. Radii follow R = c * cbrt(Y).",
                    "The crater radius is capped at 320 blocks.")
            .defineInRange("smallYield", 10.0, 0.001, 150.0);

    public static final ModConfigSpec.DoubleValue LARGE_BOMB_YIELD = BUILDER
            .comment("Yield Y of the large nuclear bomb (2x2x4). Default 33 = crater radius 112.")
            .defineInRange("largeYield", 33.0, 0.001, 150.0);

    public static final ModConfigSpec.DoubleValue TSAR_BOMB_YIELD = BUILDER
            .comment("Yield Y of the Tsar Bomba (3x3x6), the biggest bomb. Default 722 = crater radius 314.")
            .defineInRange("tsarYield", 722.0, 0.001, 1000.0);

    public static final ModConfigSpec.IntValue BOMB_FUSE_SECONDS = BUILDER
            .comment("Seconds between arming a bomb and its release (it ticks and counts down meanwhile). 0 = release at once.")
            .defineInRange("countdownSeconds", 20, 0, 120);

    public static final ModConfigSpec.DoubleValue BOMB_IMPACT_SPEED = BUILDER
            .comment("Minimum falling speed (blocks per tick) a bomb needs on impact to detonate.")
            .defineInRange("impactSpeed", 0.45, 0.1, 5.0);

    static {
        BUILDER.pop();
        BUILDER.push("veinMiner");
    }

    public static final ModConfigSpec.BooleanValue VEIN_MINER = BUILDER
            .comment("While the vein miner key (default ^) is held, breaking a block also breaks all connected blocks of the same kind.",
                    "Off by default: it is not part of the nuclear content and clashes with mods like FTB Ultimine.")
            .define("enabled", false);

    public static final ModConfigSpec.IntValue VEIN_MINER_LIMIT = BUILDER
            .comment("Maximum number of blocks broken at once by the vein miner (including the first one).")
            .defineInRange("maxBlocks", 64, 2, 1024);

    static {
        BUILDER.pop();
        BUILDER.push("data");
    }

    public static final ModConfigSpec.BooleanValue COMPUTE_REQUIRED = BUILDER
            .comment("Consoles, data terminals, storage terminals, control walls and control desks need computing power (CPU and RAM)",
                    "from compute modules in a powered server rack on the same data network. Off: they work without it.")
            .define("computeRequired", true);

    static {
        BUILDER.pop();
        BUILDER.push("assembler");
    }

    public static final ModConfigSpec.BooleanValue ASSEMBLER_KEEP_LOADED = BUILDER
            .comment("Component assemblers keep their plant, the server racks on their data network and the data cables in between",
                    "loaded with chunk tickets, even when no player is nearby (autocrafting never stops, orders keep running).",
                    "Off by default: every assembler can pin a few chunks permanently, which costs server performance.",
                    "Off: an assembler pauses (status 'Chunk not loaded') and consumes nothing while a rack chunk is unloaded.")
            .define("keepLoaded", false);

    public static final ModConfigSpec.IntValue ASSEMBLER_MAX_CHUNKS = BUILDER
            .comment("Most chunks one assembler may pin with keepLoaded (the ones closest to the assembler are kept).")
            .defineInRange("maxChunks", 9, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.push("oil");
    }

    public static final ModConfigSpec.DoubleValue OIL_REFILL_PERCENT_PER_DAY = BUILDER
            .comment("An emptied oil well refills by this percentage of its maximum per Minecraft day (24000 ticks). 0 turns refilling off.")
            .defineInRange("refillPercentPerDay", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue OIL_WELL_SPACING = BUILDER
            .comment("Oil wells in newly generated chunks: at most one per square of this many chunks on each side (24 = one well in 24x24 chunks,",
                    "about every 384 blocks). Some squares get none because the spot is water or not natural ground. 4 = the old, very dense layout.")
            .defineInRange("wellSpacingChunks", 24, 4, 64);

    public static final ModConfigSpec.BooleanValue OIL_REMOVE_OLD_WELLS = BUILDER
            .comment("Removes oil wells of the old dense layout (one per 4x4 chunks) when their chunk is loaded, unless the well is in use",
                    "(a derrick or another NuclearSQ block sits on it). Keeps the old well closest to the new layout's spot in each square,",
                    "so explored areas also end up with about one well per square.",
                    "The black stain is replaced by the ground around it. /nuclearsq oil clear <radius> does the same at once for loaded chunks.")
            .define("removeOldWells", true);

    static {
        BUILDER.pop();
        BUILDER.push("nanobots");
    }

    public static final ModConfigSpec.BooleanValue NANOBOT_ENABLED = BUILDER
            .comment("Nano swarm canisters work. Off: running swarms return and the canister refuses to start one.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue NANOBOT_RADIUS = BUILDER
            .comment("How far (blocks) around the player the swarm looks for trees, ores, items and planting spots.")
            .defineInRange("radius", 24, 4, 48);

    public static final ModConfigSpec.IntValue NANOBOT_NEAR_RADIUS = BUILDER
            .comment("How far (blocks) around the player the swarm works for the near tasks: harvest crops, till soil, mine stone.")
            .defineInRange("nearRadius", 12, 3, 24);

    public static final ModConfigSpec.IntValue NANOBOT_GADGET_MAX_BLOCKS = BUILDER
            .comment("Largest order (blocks) the Nano Bot gadget modes accept: break area, build/fill, replace, copy, paste.")
            .defineInRange("gadgetMaxBlocks", 4096, 1, 65536);

    public static final ModConfigSpec.BooleanValue NANOBOT_TUNNEL_DIGGING = BUILDER
            .comment("Break area orders work from the top layer down. If the bots cannot reach the top of the area over free surfaces (walls, ceilings), they dig a vertical",
                    "tunnel to it first (the tunnel blocks do not count towards the order and are not refilled). Off: such an order is refused with 'no way up'.")
            .define("tunnelDigging", true);

    public static final ModConfigSpec.IntValue NANOBOT_TUNNEL_MAX_BLOCKS = BUILDER
            .comment("Longest tunnel (blocks) the bots dig to reach the top of a break area. A longer way, protected or unbreakable blocks: 'no way up'.")
            .defineInRange("tunnelMaxBlocks", 64, 1, 256);

    public static final ModConfigSpec.IntValue NANOBOT_GADGET_HISTORY = BUILDER
            .comment("How many of the last gadget orders each player can undo (and redo) in order. The history lives in memory and is forgotten when the server stops.")
            .defineInRange("gadgetHistory", 10, 1, 50);

    public static final ModConfigSpec.IntValue NANOBOT_PORT_RANGE = BUILDER
            .comment("Greatest distance (blocks) between the player and a coupled Drone Item Port for the bots to take material from the digital storage.")
            .defineInRange("portRange", 64, 8, 512);

    public static final ModConfigSpec.IntValue NANOBOT_CARGO_LIMIT = BUILDER
            .comment("How many item stacks the swarm carries before it flies back to the player to deliver. Until then it keeps working from one target to the next;",
                    "the rest is delivered when the order is finished, nothing is found any more, or the swarm is recalled.")
            .defineInRange("cargoLimit", 8, 1, 64);

    public static final ModConfigSpec.BooleanValue NANOBOT_LEAVES_CLEAR = BUILDER
            .comment("The fell-tree task first removes the leaves that sit directly against the trunk, bottom to top, so the bot can climb the trunk",
                    "instead of working from below. Their drops (saplings, sticks, apples) go to the player. Independent of breakLeaves.")
            .define("leavesClear", true);

    public static final ModConfigSpec.BooleanValue NANOBOT_BREAK_LEAVES = BUILDER
            .comment("The fell-tree task also removes the natural leaves of the tree (saplings, sticks and apples go to the player).",
                    "Off: the leaves decay on their own.")
            .define("breakLeaves", false);

    public static final ModConfigSpec.IntValue NANOBOT_BLOCK_INTERVAL = BUILDER
            .comment("Ticks between two actions of one swarm (breaking, planting, harvesting, tilling; at most one block per tick in any case).")
            .defineInRange("blockIntervalTicks", 8, 1, 40);

    public static final ModConfigSpec.DoubleValue NANOBOT_SWARM_SPEED = BUILDER
            .comment("Flight speed of the swarm centre in blocks per tick (the bots follow it on foot). Higher = the swarm starts working sooner after a target is found.")
            .defineInRange("swarmFlySpeed", 0.3, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue NANOBOT_BOT_SPEED = BUILDER
            .comment("Basic walking speed of a single bot in blocks per tick; sprints are 3 to 6 times as fast (client visuals only).")
            .defineInRange("botWalkSpeed", 0.06, 0.01, 0.3);

    public static final ModConfigSpec.IntValue NANOBOT_ENTRY_STAGGER = BUILDER
            .comment("When the swarm is recalled the bots run to the player one after another and climb into the arm; this is the delay in ticks between two bots starting",
                    "their entry (8 = 0.4 s). The swarm only ends after the last bot has had time to enter.")
            .defineInRange("botEntryStagger", 8, 1, 40);

    public static final ModConfigSpec.IntValue NANOBOT_MAX_BREAKS_PER_TICK = BUILDER
            .comment("Most blocks all bots of one swarm may finish (break, place) in a single server tick. With many bots and a high speed upgrade the rest simply finishes",
                    "a tick later; this keeps the server tick short.")
            .defineInRange("maxBreaksPerTick", 8, 1, 64);

    public static final ModConfigSpec.IntValue NANOBOT_CLAIM_SPACING = BUILDER
            .comment("Minimum distance (blocks, Chebyshev) between the blocks two bots work on at the same time. A bot that would have to take a block next to one that",
                    "is being worked on takes another block further away instead, and waits if there is none (no two bots on the same spot). 1 = only never the same block.")
            .defineInRange("claimSpacing", 2, 1, 4);

    public static final ModConfigSpec.IntValue NANOBOT_SCAN_PER_TICK = BUILDER
            .comment("How many positions the swarm checks per tick while it searches for its next target (fell tree, ore, stone, crops ...). The search goes in rings",
                    "(6, 12, then the full radius) and stops at the first ring with a target. Higher = faster start, slightly more server load while searching.")
            .defineInRange("searchPerTick", 6000, 500, 100000);

    public static final ModConfigSpec.IntValue NANOBOT_SWARM_SIZE = BUILDER
            .comment("How many bots a swarm has (1 to 10; the count upgrades give 2 / 4 / 6 / 8 / 10). Default 1: a single big bot that really works on the block.")
            .defineInRange("swarmSize", 1, 1, 10);

    public static final ModConfigSpec.DoubleValue NANOBOT_BOT_SCALE = BUILDER
            .comment("Size of the bot spiders (model scale; 1.35 is three times the former 0.45).")
            .defineInRange("botScale", 1.35, 0.3, 4.0);

    public static final ModConfigSpec.DoubleValue NANOBOT_WORK_TIME_FACTOR = BUILDER
            .comment("Multiplier on the time a bot needs for one block (hardness and tool from the inventory as in vanilla breaking).",
                    "Applies to felling trees, mining ore and stone.")
            .defineInRange("workTimeFactor", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.IntValue NANOBOT_MIN_WORK_TICKS = BUILDER
            .comment("Shortest time in ticks for one hard block (stone, ore, logs); soft blocks like leaves take at least 6 ticks.")
            .defineInRange("minWorkTicks", 30, 4, 200);

    static {
        BUILDER.pop();
        BUILDER.push("difficulty");
    }

    public enum Difficulty {
        RELAXED(0.5, 1.5, 0.5), NORMAL(1, 1, 1), HARDCORE(1.5, 0.5, 1.5);

        final double blast, fuse, radiation;

        Difficulty(double blast, double fuse, double radiation) {
            this.blast = blast;
            this.fuse = fuse;
            this.radiation = radiation;
        }
    }

    public static final ModConfigSpec.EnumValue<Difficulty> DIFFICULTY = BUILDER
            .comment("Difficulty preset. RELAXED: explosions (and fallout) half as strong, fuse 1.5x, radiation uptake half. NORMAL: the values below as set.",
                    "HARDCORE: explosions 1.5x, fuse half as long, radiation uptake 1.5x, machine kits and their parts cost the higher hardcore amounts.",
                    "The preset multiplies the single values of this file.")
            .defineEnum("preset", Difficulty.NORMAL);

    public static final ModConfigSpec.DoubleValue RADIATION_FACTOR = BUILDER
            .comment("Multiplier on how fast a player takes up radiation from the ground and fallout (1 = default, 0 = none). Also multiplied by the preset.")
            .defineInRange("radiationFactor", 1.0, 0.0, 10.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static <T> T value(ModConfigSpec.ConfigValue<T> v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static int fuseSeconds() {
        return (int) Math.round(value(BOMB_FUSE_SECONDS) * value(DIFFICULTY).fuse);
    }

    public static double blastFactor() {
        return value(DIFFICULTY).blast;
    }

    public static boolean hardcoreCosts() {
        return value(DIFFICULTY) == Difficulty.HARDCORE;
    }

    public static double radiationFactor() {
        return value(RADIATION_FACTOR) * value(DIFFICULTY).radiation;
    }
}
