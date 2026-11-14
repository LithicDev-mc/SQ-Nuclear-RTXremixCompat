package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.NanoCanisterItem;
import com.sqc.nuclearsq.nanobot.NanoCanisterMenu;
import com.sqc.nuclearsq.nanobot.NanoSettings;
import com.sqc.nuclearsq.nanobot.NanoUpgradeItem;
import com.sqc.nuclearsq.nanobot.NanoSwarm;
import com.sqc.nuclearsq.nanobot.NanoSwarmPayload;
import com.sqc.nuclearsq.nanobot.NanoSwarms;
import com.sqc.nuclearsq.nanobot.NanoTask;
import com.sqc.nuclearsq.nanobot.NanobotRegistry;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class NanobotTests {
    private NanobotTests() {}

    private static final String FLOOR = "floor";

    private static ServerPlayer player(GameTestHelper h, String name, boolean reactor) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("nanobot_test_" + name).getBytes()), "nb_" + name));
        NanoSwarms.stop(sp);
        sp.getInventory().clearContent();
        sp.setPos(h.absoluteVec(new Vec3(2.5, 2, 2.5)));
        if (reactor) sp.getInventory().add(chargedReactor(100_000));
        return sp;
    }

    private static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static NanoSwarm run(GameTestHelper h, ServerPlayer sp, NanoTask task) {
        NanoSwarm sw = NanoSwarms.start(sp, task);
        h.onEachTick(() -> {
            if (!sw.isEnded()) sw.tick(sp);
        });
        return sw;
    }

    private static ItemStack chargedReactor(int fe) {
        ItemStack st = new ItemStack(com.sqc.nuclearsq.exo.ExoRegistry.CORE.get());
        if (fe > 0) st.set(com.sqc.nuclearsq.registry.ModDataComponents.STORED_ENERGY.get(), fe);
        return st;
    }

    private static void done(ServerPlayer sp) {
        NanoSwarms.stop(sp);
        sp.getInventory().clearContent();
    }

    @GameTest(template = FLOOR, batch = "nanobots_tree", timeoutTicks = 1500)
    public static void fellsTree(GameTestHelper h) {
        ServerPlayer sp = player(h, "tree", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        h.setBlock(new BlockPos(6, 6, 6), Blocks.OAK_LEAVES.defaultBlockState());
        h.setBlock(new BlockPos(5, 5, 6), Blocks.OAK_LEAVES.defaultBlockState());
        h.setBlock(new BlockPos(7, 5, 6), Blocks.OAK_LEAVES.defaultBlockState());
        run(h, sp, NanoTask.FELL_TREE);
        h.succeedWhen(() -> {
            for (int y = 3; y <= 5; y++) {
                if (h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("Stamm bei y=" + y + " steht noch");
            }
            if (count(sp, Items.OAK_LOG) != 3) throw new GameTestAssertException("Baumstaemme im Inventar: " + count(sp, Items.OAK_LOG) + " statt 3");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_tree_leaves", timeoutTicks = 3500)
    public static void fellsTreeWithLeafCrown(GameTestHelper h) {
        ServerPlayer sp = player(h, "treeleaves", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        int top = 8;
        for (int y = 3; y <= top; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        java.util.List<BlockPos> adjacent = new java.util.ArrayList<>();
        for (int y = 4; y <= top; y++) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos p = new BlockPos(6, y, 6).relative(d);
                if (!h.getBlockState(p).isAir()) continue;
                h.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState());
                adjacent.add(p);
            }
        }
        if (adjacent.add(new BlockPos(6, top + 1, 6))) h.setBlock(new BlockPos(6, top + 1, 6), Blocks.OAK_LEAVES.defaultBlockState());
        BlockPos far = new BlockPos(3, 6, 6);
        h.setBlock(far, Blocks.OAK_LEAVES.defaultBlockState());
        run(h, sp, NanoTask.FELL_TREE);
        h.onEachTick(() -> {
            boolean logMissing = false;
            for (int y = 3; y <= top; y++) if (!h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) logMissing = true;
            if (!logMissing) return;
            for (BlockPos p : adjacent) {
                if (h.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) throw new GameTestAssertException("Stamm faellt, obwohl am Stamm noch ein Blatt haengt: " + p);
            }
        });
        h.succeedWhen(() -> {
            for (int y = 3; y <= top; y++) {
                if (h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("Stamm bei y=" + y + " steht noch");
            }
            for (BlockPos p : adjacent) {
                if (h.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) throw new GameTestAssertException("Blatt am Stamm steht noch: " + p);
            }
            if (!(h.getBlockState(far).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) throw new GameTestAssertException("fernes Blatt wurde abgebaut (breakLeaves ist aus)");
            if (count(sp, Items.OAK_LOG) != top - 2) throw new GameTestAssertException("Baumstaemme im Inventar: " + count(sp, Items.OAK_LOG) + " statt " + (top - 2));
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_house", timeoutTicks = 300)
    public static void leavesBuildingsAlone(GameTestHelper h) {
        ServerPlayer sp = player(h, "house", true);
        for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        run(h, sp, NanoTask.FELL_TREE);
        h.runAfterDelay(200, () -> {
            for (int y = 3; y <= 5; y++) {
                if (!h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("Bauwerk wurde gefaellt");
            }
            done(sp);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_items", timeoutTicks = 600)
    public static void collectsItems(GameTestHelper h) {
        ServerPlayer sp = player(h, "items", true);
        Vec3 at = h.absoluteVec(new Vec3(9.5, 2.2, 2.5));
        h.getLevel().addFreshEntity(new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, 5)));
        run(h, sp, NanoTask.COLLECT_ITEMS);
        h.succeedWhen(() -> {
            if (count(sp, Items.COBBLESTONE) != 5) throw new GameTestAssertException("Bruchstein im Inventar: " + count(sp, Items.COBBLESTONE) + " statt 5");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_full", timeoutTicks = 600)
    public static void fullInventoryDropsAtFeet(GameTestHelper h) {
        ServerPlayer sp = player(h, "full", true);
        for (int i = 1; i < sp.getInventory().items.size(); i++) sp.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        sp.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
        Vec3 at = h.absoluteVec(new Vec3(9.5, 2.2, 2.5));
        h.getLevel().addFreshEntity(new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, 5)));
        run(h, sp, NanoTask.COLLECT_ITEMS);
        h.succeedWhen(() -> {
            int dropped = 0;
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(sp.blockPosition()).inflate(3))) {
                if (e.getItem().is(Items.COBBLESTONE)) dropped += e.getItem().getCount();
            }
            if (dropped != 5) throw new GameTestAssertException("Bruchstein vor den Fuessen: " + dropped + " statt 5");
            done(sp);
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(sp.blockPosition()).inflate(3))) e.discard();
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_ore", timeoutTicks = 900)
    public static void minesOreVein(GameTestHelper h) {
        ServerPlayer sp = player(h, "ore", true);
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        BlockPos[] vein = {new BlockPos(8, 2, 4), new BlockPos(8, 3, 4), new BlockPos(9, 3, 4)};
        for (BlockPos p : vein) h.setBlock(p, Blocks.IRON_ORE.defaultBlockState());
        run(h, sp, NanoTask.MINE_ORE);
        h.succeedWhen(() -> {
            for (BlockPos p : vein) {
                if (!h.getBlockState(p).isAir()) throw new GameTestAssertException("Erz bei " + p + " steht noch");
            }
            if (count(sp, Items.RAW_IRON) != 3) throw new GameTestAssertException("Rohreisen: " + count(sp, Items.RAW_IRON) + " statt 3");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_noore", timeoutTicks = 300)
    public static void oreNeedsPickaxe(GameTestHelper h) {
        ServerPlayer sp = player(h, "noore", true);
        BlockPos ore = new BlockPos(8, 2, 4);
        h.setBlock(ore, Blocks.IRON_ORE.defaultBlockState());
        run(h, sp, NanoTask.MINE_ORE);
        h.runAfterDelay(150, () -> {
            if (!h.getBlockState(ore).is(Blocks.IRON_ORE)) throw new GameTestAssertException("Erz ohne Spitzhacke abgebaut");
            done(sp);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_plant", timeoutTicks = 900)
    public static void plantsSaplings(GameTestHelper h) {
        ServerPlayer sp = player(h, "plant", true);
        sp.getInventory().add(new ItemStack(Items.OAK_SAPLING, 2));
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) h.setBlock(new BlockPos(x, 3, z), Blocks.DIRT.defaultBlockState());
        }
        NanoSwarm sw = run(h, sp, NanoTask.PLANT_SAPLINGS);
        int[] ticks = {0};
        h.onEachTick(() -> {
            if (++ticks[0] % 100 == 0) {
                NuclearSQ.LOGGER.info("[nanobots] Test plantsSaplings Tick {}: Phase {}, beendet {}, Setzlinge im Inventar {}, Reactor {}", ticks[0], sw.phase(),
                        sw.isEnded(), count(sp, Items.OAK_SAPLING), com.sqc.nuclearsq.nanobot.NanoPower.hasCore(sp));
            }
        });
        h.succeedWhen(() -> {
            if (count(sp, Items.OAK_SAPLING) != 0) throw new GameTestAssertException("Setzlinge noch im Inventar: " + count(sp, Items.OAK_SAPLING) + " (Phase " + sw.phase() + ")");
            int planted = 0;
            BlockPos base = h.absolutePos(new BlockPos(2, 2, 2));
            for (BlockPos q : BlockPos.betweenClosed(base.offset(-26, -20, -26), base.offset(26, 20, 26))) {
                if (h.getLevel().getBlockState(q).getBlock() == Blocks.OAK_SAPLING) planted++;
            }
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_harvest", timeoutTicks = 600)
    public static void harvestsAndReplants(GameTestHelper h) {
        ServerPlayer sp = player(h, "harvest", true);
        BlockPos ripe = new BlockPos(6, 3, 6), young = new BlockPos(7, 3, 6);
        for (int x = 6; x <= 7; x++) h.setBlock(new BlockPos(x, 2, 6), Blocks.FARMLAND.defaultBlockState());
        h.setBlock(ripe, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        h.setBlock(young, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2));
        run(h, sp, NanoTask.HARVEST);
        h.succeedWhen(() -> {
            if (count(sp, Items.WHEAT) < 1) throw new GameTestAssertException("kein Weizen im Inventar");
            var st = h.getBlockState(ripe);
            if (st.getBlock() != Blocks.WHEAT || st.getValue(net.minecraft.world.level.block.CropBlock.AGE) != 0) throw new GameTestAssertException("nicht neu ausgesaet: " + st);
            if (h.getBlockState(young).getValue(net.minecraft.world.level.block.CropBlock.AGE) != 2) throw new GameTestAssertException("junge Pflanze wurde geerntet");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_decon", timeoutTicks = 600)
    public static void decontaminatesNearby(GameTestHelper h) {
        ServerPlayer sp = player(h, "decon", true);
        BlockPos spot = h.absolutePos(new BlockPos(6, 4, 6));
        com.sqc.nuclearsq.radiation.Contamination.add(h.getLevel(), spot, 0.01f);
        run(h, sp, NanoTask.DECONTAMINATE);
        h.succeedWhen(() -> {
            if (com.sqc.nuclearsq.radiation.Contamination.zones(h.getLevel()).get(spot) > 0) throw new GameTestAssertException("Kontamination blieb liegen");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_till", timeoutTicks = 600)
    public static void tillsSoilNearWater(GameTestHelper h) {
        ServerPlayer sp = player(h, "till", true);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        sp.getInventory().add(hoe);
        BlockPos[] soil = {new BlockPos(4, 3, 4), new BlockPos(5, 3, 4), new BlockPos(6, 3, 4)};
        for (BlockPos p : soil) h.setBlock(p, Blocks.DIRT.defaultBlockState());
        h.setBlock(new BlockPos(5, 3, 5), Blocks.WATER.defaultBlockState());
        run(h, sp, NanoTask.TILL);
        h.succeedWhen(() -> {
            for (BlockPos p : soil) {
                if (h.getBlockState(p).getBlock() != Blocks.FARMLAND) throw new GameTestAssertException("noch kein Beet bei " + p + ": " + com.sqc.nuclearsq.nanobot.NanoSwarm.tillRejectForTest(h.getLevel(), h.absolutePos(p)));
            }
            if (hoe.getDamageValue() != 0) throw new GameTestAssertException("Hacke wurde abgenutzt");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_nohoe", timeoutTicks = 300)
    public static void tillNeedsHoe(GameTestHelper h) {
        ServerPlayer sp = player(h, "nohoe", true);
        BlockPos p = new BlockPos(4, 3, 4);
        h.setBlock(p, Blocks.DIRT.defaultBlockState());
        h.setBlock(new BlockPos(5, 3, 4), Blocks.WATER.defaultBlockState());
        run(h, sp, NanoTask.TILL);
        h.runAfterDelay(150, () -> {
            if (h.getBlockState(p).getBlock() != Blocks.DIRT) throw new GameTestAssertException("ohne Hacke umgegraben");
            done(sp);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_stone", timeoutTicks = 900)
    public static void minesStoneNearby(GameTestHelper h) {
        ServerPlayer sp = player(h, "stone", true);
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        sp.setPos(h.absoluteVec(new Vec3(2.5, 6, 2.5)));
        var floor = h.getBlockState(new BlockPos(2, 3, 2));
        BlockPos[] col = {new BlockPos(6, 6, 3), new BlockPos(6, 7, 3), new BlockPos(6, 8, 3)};
        for (BlockPos p : col) h.setBlock(p, Blocks.STONE.defaultBlockState());
        run(h, sp, NanoTask.MINE_STONE);
        h.succeedWhen(() -> {
            for (BlockPos p : col) {
                if (!h.getBlockState(p).isAir()) throw new GameTestAssertException("Stein bei " + p + " steht noch");
            }
            if (count(sp, Items.COBBLESTONE) != 3) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 3");
            if (h.getBlockState(new BlockPos(2, 3, 2)) != floor) throw new GameTestAssertException("Boden unter dem Spieler abgebaut");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_mountain", timeoutTicks = 4000)
    public static void minesStoneMountainButNotThePlatform(GameTestHelper h) {
        ServerPlayer sp = player(h, "mountain", true);
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        java.util.List<BlockPos> mass = new java.util.ArrayList<>();
        for (int x = 6; x <= 8; x++) for (int y = 4; y <= 6; y++) for (int z = 3; z <= 5; z++) mass.add(new BlockPos(x, y, z));
        for (BlockPos p : mass) h.setBlock(p, Blocks.STONE.defaultBlockState());
        run(h, sp, NanoTask.MINE_STONE);
        h.succeedWhen(() -> {
            int left = 0;
            for (BlockPos p : mass) if (!h.getBlockState(p).isAir()) left++;
            if (left > 0) throw new GameTestAssertException("Berg: noch " + left + " Steine von 27");
            if (count(sp, Items.COBBLESTONE) != 27) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 27");
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    if (!h.getBlockState(new BlockPos(x, 3, z)).is(Blocks.STONE)) throw new GameTestAssertException("Plattform unter dem Spieler abgebaut bei " + x + "," + z);
                }
            }
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_worktime", timeoutTicks = 1200)
    public static void breakingTakesTime(GameTestHelper h) {
        ServerPlayer sp = player(h, "worktime", true);
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        sp.setPos(h.absoluteVec(new Vec3(2.5, 6, 2.5)));
        BlockPos p = new BlockPos(6, 6, 3);
        h.setBlock(p, Blocks.STONE.defaultBlockState());
        run(h, sp, NanoTask.MINE_STONE);
        h.runAfterDelay(20, () -> {
            if (!h.getBlockState(p).is(Blocks.STONE)) throw new GameTestAssertException("Stein war nach 20 Ticks schon weg (Sofortabbau)");
        });
        h.succeedWhen(() -> {
            if (!h.getBlockState(p).isAir()) throw new GameTestAssertException("Stein steht noch");
            if (count(sp, Items.COBBLESTONE) != 1) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 1");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_nostone", timeoutTicks = 300)
    public static void stoneNeedsPickaxe(GameTestHelper h) {
        ServerPlayer sp = player(h, "nostone", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 6, 2.5)));
        BlockPos p = new BlockPos(6, 6, 3);
        h.setBlock(p, Blocks.STONE.defaultBlockState());
        run(h, sp, NanoTask.MINE_STONE);
        h.runAfterDelay(150, () -> {
            if (!h.getBlockState(p).is(Blocks.STONE)) throw new GameTestAssertException("Stein ohne Spitzhacke abgebaut");
            done(sp);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_power", timeoutTicks = 400)
    public static void withoutReactorTheSwarmLeaves(GameTestHelper h) {
        ServerPlayer sp = player(h, "power", false);
        NanoSwarm sw = run(h, sp, NanoTask.COLLECT_ITEMS);
        h.succeedWhen(() -> {
            if (!sw.isEnded()) throw new GameTestAssertException("Schwarm laeuft ohne Exo-Kern weiter (Phase " + sw.phase() + ")");
            if (NanoSwarms.get(sp) != null && !NanoSwarms.get(sp).isEnded()) throw new GameTestAssertException("Schwarm noch registriert");
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_slot", timeoutTicks = 20)
    public static void reactorCountsInAnySlot(GameTestHelper h) {
        ServerPlayer sp = player(h, "slot", false);
        if (com.sqc.nuclearsq.nanobot.NanoPower.hasCore(sp)) throw new GameTestAssertException("Reactor ohne Item");
        sp.getInventory().offhand.set(0, new ItemStack(com.sqc.nuclearsq.exo.ExoRegistry.CORE.get()));
        if (!com.sqc.nuclearsq.nanobot.NanoPower.hasCore(sp)) throw new GameTestAssertException("Reactor in der Zweithand zaehlt nicht");
        sp.getInventory().clearContent();
        sp.getInventory().items.set(35, new ItemStack(com.sqc.nuclearsq.exo.ExoRegistry.CORE.get()));
        if (!com.sqc.nuclearsq.nanobot.NanoPower.hasCore(sp)) throw new GameTestAssertException("Reactor im Rucksack zaehlt nicht");
        done(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_slot", timeoutTicks = 20)
    public static void reactorDrainsRealEnergy(GameTestHelper h) {
        ServerPlayer sp = player(h, "drain", false);
        sp.getInventory().add(chargedReactor(0));
        if (com.sqc.nuclearsq.nanobot.NanoPower.drain(sp, 1)) throw new GameTestAssertException("leerer Reactor liefert Strom");
        var cap = sp.getInventory().getItem(0).getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (cap == null || cap.receiveEnergy(100, false) != 100) throw new GameTestAssertException("Reactor laesst sich nicht laden");
        if (cap.extractEnergy(1, true) != 0) throw new GameTestAssertException("Reactor gibt Strom an Ladegeraete ab");
        int fe = com.sqc.nuclearsq.nanobot.NanoPower.FE_PER_UNIT;
        if (com.sqc.nuclearsq.exo.ExoCoreItem.energy(sp.getInventory().getItem(0)) != 100) throw new GameTestAssertException("Ladung nicht gespeichert");
        for (int i = 0; i < 100 / fe; i++) if (!com.sqc.nuclearsq.nanobot.NanoPower.drain(sp, 1)) throw new GameTestAssertException("Schritt " + i + " ohne Strom");
        if (com.sqc.nuclearsq.exo.ExoCoreItem.energy(sp.getInventory().getItem(0)) != 100 % fe) throw new GameTestAssertException("Restladung falsch");
        if (100 % fe == 0 && com.sqc.nuclearsq.nanobot.NanoPower.drain(sp, 1)) throw new GameTestAssertException("Strom reicht nicht, drain liefert trotzdem true");
        done(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_slot", timeoutTicks = 20)
    public static void pocketBatteryRefillsReactor(GameTestHelper h) {
        ServerPlayer sp = player(h, "pocket", false);
        sp.getInventory().add(chargedReactor(0));
        ItemStack bat = new ItemStack(NanobotRegistry.POCKET_BATTERIES.get(0).get());
        bat.set(com.sqc.nuclearsq.registry.ModDataComponents.STORED_ENERGY.get(), 500_000);
        sp.getInventory().add(bat);
        bat = sp.getInventory().getItem(1);
        if (bat.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM).extractEnergy(1, true) != 0) throw new GameTestAssertException("Batterie gibt Strom nach aussen ab");
        if (!com.sqc.nuclearsq.nanobot.NanoPower.drain(sp, 1)) throw new GameTestAssertException("Reactor wurde nicht aus der Batterie nachgeladen");
        int cap = 100_000, fe = com.sqc.nuclearsq.nanobot.NanoPower.FE_PER_UNIT;
        if (com.sqc.nuclearsq.exo.ExoCoreItem.energy(sp.getInventory().getItem(0)) != cap - fe) throw new GameTestAssertException("Reactor-Ladung falsch");
        if (com.sqc.nuclearsq.nanobot.PocketBatteryItem.energy(bat) != 500_000 - cap) throw new GameTestAssertException("Batterie-Ladung falsch");
        done(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_slot", timeoutTicks = 20)
    public static void emptyReactorHasNoPower(GameTestHelper h) {
        ServerPlayer sp = player(h, "nopower", false);
        sp.getInventory().add(chargedReactor(0));
        if (com.sqc.nuclearsq.nanobot.NanoPower.hasPower(sp)) throw new GameTestAssertException("leerer Reactor gilt als versorgt");
        ItemStack bat = new ItemStack(NanobotRegistry.POCKET_BATTERIES.get(0).get());
        bat.set(com.sqc.nuclearsq.registry.ModDataComponents.STORED_ENERGY.get(), 1000);
        sp.getInventory().add(bat);
        bat = sp.getInventory().getItem(1);
        if (!com.sqc.nuclearsq.nanobot.NanoPower.hasPower(sp)) throw new GameTestAssertException("geladene Batterie zaehlt nicht");
        done(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_canister", timeoutTicks = 40)
    public static void canisterStartsRecallsAndCyclesTask(GameTestHelper h) {
        ServerPlayer sp = player(h, "canister", true);
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        if (NanoCanisterItem.task(can) != NanoTask.FELL_TREE) throw new GameTestAssertException("Standardaufgabe ist nicht Baum faellen");
        NanoCanisterItem.toggle(sp, can);
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw == null || sw.task() != NanoTask.FELL_TREE) throw new GameTestAssertException("Schwarm nicht gestartet");
        sp.getCooldowns().removeCooldown(NanobotRegistry.CANISTER.get());
        if (NanoSwarms.start(sp, NanoTask.MINE_ORE) != sw) throw new GameTestAssertException("zweiter Schwarm fuer denselben Spieler");
        sp.setShiftKeyDown(true);
        can.getItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        sp.setShiftKeyDown(false);
        if (NanoCanisterItem.task(can) != NanoTask.COLLECT_ITEMS || sw.task() != NanoTask.COLLECT_ITEMS) {
            throw new GameTestAssertException("Aufgabe nicht gewechselt: " + NanoCanisterItem.task(can) + " / " + sw.task());
        }
        sp.getCooldowns().removeCooldown(NanobotRegistry.CANISTER.get());
        NanoCanisterItem.toggle(sp, can);
        if (sw.phase() != NanoSwarm.Phase.LEAVE) throw new GameTestAssertException("Rueckruf wirkt nicht: " + sw.phase());
        done(sp);
        h.succeed();
    }

    private static NanoUpgradeItem up(String key) {
        return NanobotRegistry.UPGRADE_ITEMS.get(key).get();
    }

    @GameTest(template = FLOOR, batch = "nanobots_upgrades", timeoutTicks = 20)
    public static void upgradeMath(GameTestHelper h) {
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        NanoSettings base = NanoSettings.of(can);
        if (base.speedFactor() != 1.0 || base.speedLevel() != 0 || base.bots() != com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_SWARM_SIZE)) {
            throw new GameTestAssertException("Grundwerte ohne Upgrades falsch: " + base);
        }
        if (NanobotRegistry.UPGRADE_ITEMS.size() != 16) throw new GameTestAssertException("Upgrade-Items: " + NanobotRegistry.UPGRADE_ITEMS.size() + " statt 16 (3 Typen x 5 Stufen + Zeigen)");
        double[] speed = {2, 3, 4, 5, 7};
        int[] count = {2, 4, 6, 8, 10}, reach = {8, 24, 40, 60, 80};
        int cfg = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_RADIUS);
        for (int lv = 1; lv <= 5; lv++) {
            var list = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
            list.set(0, new ItemStack(up("speed_" + lv)));
            list.set(1, new ItemStack(up("count_" + lv)));
            list.set(2, new ItemStack(up("reach_" + lv)));
            can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(list));
            NanoSettings s = NanoSettings.of(can);
            if (s.speedLevel() != lv || s.speedFactor() != speed[lv - 1]) throw new GameTestAssertException("Tempo Stufe " + lv + " = x" + speed[lv - 1] + ": " + s);
            if (s.bots() != count[lv - 1]) throw new GameTestAssertException("Anzahl Stufe " + lv + " = " + count[lv - 1] + " Bots: " + s);
            if (s.maxRadius() != Math.min(NanoSettings.HARD_MAX_RADIUS, cfg + reach[lv - 1])) throw new GameTestAssertException("Reichweite Stufe " + lv + " = +" + reach[lv - 1] + ": " + s);
            if (s.workSpeed() != s.speedFactor()) throw new GameTestAssertException("keine Teamarbeit: die Arbeitsgeschwindigkeit ist nur das Tempo-Upgrade");
        }
        var list = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        list.set(0, new ItemStack(up("speed_1")));
        list.set(1, new ItemStack(up("speed_5")));
        list.set(2, new ItemStack(up("count_2")));
        list.set(3, new ItemStack(up("reach_1")));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(list));
        NanoSettings s = NanoSettings.of(can);
        if (s.speedLevel() != 5 || s.speedFactor() != 7.0) throw new GameTestAssertException("Tempo: hoechste Stufe zaehlt: " + s);
        if (s.bots() != 4) throw new GameTestAssertException("Anzahl Stufe 2 = 4 Bots: " + s);
        can.set(NanobotRegistry.RADIUS.get(), 999);
        if (NanoSettings.of(can).radius() != s.maxRadius()) throw new GameTestAssertException("Radius nicht auf das Maximum gekappt");
        can.set(NanobotRegistry.RADIUS.get(), 1);
        if (NanoSettings.of(can).radius() != NanoSettings.MIN_RADIUS) throw new GameTestAssertException("Radius nicht auf das Minimum gehoben");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_upgrade_slots", timeoutTicks = 20)
    public static void upgradeSlotsAndMenuLine(GameTestHelper h) {
        ServerPlayer sp = player(h, "upslots", true);
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        NanoCanisterMenu m = new NanoCanisterMenu(1, sp.getInventory(), 8);
        net.minecraft.world.inventory.Slot s0 = m.slots.get(0), s1 = m.slots.get(1), s2 = m.slots.get(2), s3 = m.slots.get(3);
        s0.set(new ItemStack(up("reach_5")));
        if (s1.mayPlace(new ItemStack(up("reach_1"))) || s1.mayPlace(new ItemStack(up("reach_4")))) throw new GameTestAssertException("zweites Reichweite-Upgrade erlaubt");
        s1.set(new ItemStack(up("speed_5")));
        if (!s2.mayPlace(new ItemStack(up("count_5")))) throw new GameTestAssertException("Anzahl V nicht erlaubt");
        s2.set(new ItemStack(up("count_5")));
        if (s3.mayPlace(new ItemStack(up("speed_2")))) throw new GameTestAssertException("zweites Tempo-Upgrade erlaubt");
        m.broadcastChanges();
        int cfg = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_RADIUS);
        if (m.data(NanoCanisterMenu.D_SPEED) != 70 || m.data(NanoCanisterMenu.D_BOTS) != 10 || m.data(NanoCanisterMenu.D_MAXRADIUS) != Math.min(NanoSettings.HARD_MAX_RADIUS, cfg + 80))
            throw new GameTestAssertException("Menue-Zeile: Tempo " + m.data(NanoCanisterMenu.D_SPEED) + " (70), Bots " + m.data(NanoCanisterMenu.D_BOTS) + " (10), Max " + m.data(NanoCanisterMenu.D_MAXRADIUS));
        done(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_menu", timeoutTicks = 40)
    public static void menuValidatesActions(GameTestHelper h) {
        ServerPlayer sp = player(h, "menu", true);
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        NanoCanisterMenu m = new NanoCanisterMenu(1, sp.getInventory(), 8);
        if (!m.stillValid(sp)) throw new GameTestAssertException("Menue ungueltig trotz Kanister");
        m.handleAction(sp, NanoCanisterMenu.ACT_TASK, 99);
        if (NanoCanisterItem.task(can) != NanoTask.FELL_TREE) throw new GameTestAssertException("ungueltige Aufgabe wurde uebernommen");
        m.handleAction(sp, NanoCanisterMenu.ACT_TASK, NanoTask.MINE_STONE.ordinal());
        if (NanoCanisterItem.task(can) != NanoTask.MINE_STONE) throw new GameTestAssertException("Aufgabenwechsel per Menue geht nicht");
        m.handleAction(sp, NanoCanisterMenu.ACT_RADIUS, 999);
        if (can.get(NanobotRegistry.RADIUS.get()) != NanoSettings.of(can).maxRadius()) throw new GameTestAssertException("Radius 999 nicht gekappt");
        m.handleAction(sp, NanoCanisterMenu.ACT_RADIUS, 1);
        if (can.get(NanobotRegistry.RADIUS.get()) != NanoSettings.MIN_RADIUS) throw new GameTestAssertException("Radius 1 nicht gehoben");
        net.minecraft.world.inventory.Slot s0 = m.slots.get(0), s1 = m.slots.get(1);
        if (s0.mayPlace(new ItemStack(Items.DIRT))) throw new GameTestAssertException("Upgrade-Slot nimmt fremdes Item");
        s0.set(new ItemStack(up("speed_1")));
        if (NanoSettings.of(can).speedLevel() != 1) throw new GameTestAssertException("Upgrade im Slot wirkt nicht (Komponente am Kanister)");
        if (s1.mayPlace(new ItemStack(up("speed_2")))) throw new GameTestAssertException("zweites Tempo-Upgrade im Slot erlaubt");
        if (!s1.mayPlace(new ItemStack(up("count_1")))) throw new GameTestAssertException("Anzahl-Upgrade nicht erlaubt");
        m.handleAction(sp, NanoCanisterMenu.ACT_TOGGLE, 0);
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw == null || sw.task() != NanoTask.MINE_STONE) throw new GameTestAssertException("Start per Menue geht nicht");
        m.handleAction(sp, NanoCanisterMenu.ACT_TOGGLE, 0);
        if (sw.phase() != NanoSwarm.Phase.LEAVE) throw new GameTestAssertException("Rueckruf per Menue geht nicht: " + sw.phase());
        sp.getInventory().items.set(8, ItemStack.EMPTY);
        if (m.stillValid(sp)) throw new GameTestAssertException("Menue gueltig ohne Kanister");
        done(sp);
        h.succeed();
    }

    private static ItemStack gadget(GameTestHelper h, ServerPlayer sp, NanoTask task, BlockPos p1, BlockPos p2, int shape, int source) {
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), task.ordinal());
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, new com.sqc.nuclearsq.nanobot.GadgetData(shape, source,
                java.util.Optional.of(h.absolutePos(p1)), java.util.Optional.of(h.absolutePos(p2))));
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        return can;
    }

    private static void tickSwarm(GameTestHelper h, ServerPlayer sp) {
        h.onEachTick(() -> {
            NanoSwarm sw = NanoSwarms.get(sp);
            if (sw != null && !sw.isEnded()) sw.tick(sp);
        });
    }

    private static void gadgetDone(ServerPlayer sp) {
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        done(sp);
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_break", timeoutTicks = 4000)
    public static void gadgetBreaksArea(GameTestHelper h) {
        ServerPlayer sp = player(h, "gbreak", true);
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int x = 6; x <= 8; x++) for (int y = 4; y <= 5; y++) for (int z = 6; z <= 8; z++) cells.add(new BlockPos(x, y, z));
        for (BlockPos c : cells) h.setBlock(c, Blocks.STONE.defaultBlockState());
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(8, 5, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        java.util.List<String> notStone = new java.util.ArrayList<>();
        for (BlockPos c : cells) if (!h.getBlockState(c).is(Blocks.STONE)) notStone.add(c + "=" + h.getBlockState(c).getBlock().getDescriptionId());
        if (!notStone.isEmpty()) throw new GameTestAssertException("Aufbau: " + (18 - notStone.size()) + " von 18 Bloecken sind Stein, nicht Stein: " + notStone);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var order = com.sqc.nuclearsq.nanobot.NanoGadget.last(sp);
        if (order == null || order.initial() != 18) throw new GameTestAssertException("Bloecke im Bereich bei der Bestaetigung: " + (order == null ? "kein Auftrag" : order.initial()) + " statt 18");
        tickSwarm(h, sp);
        java.util.Map<BlockPos, net.minecraft.world.level.block.Block> last = new java.util.HashMap<>();
        for (BlockPos c : cells) last.put(h.absolutePos(c), Blocks.STONE);
        java.util.List<String> external = new java.util.ArrayList<>();
        int[] doneBefore = {0};
        h.onEachTick(() -> {
            int done = order.done();
            doneBefore[0] = done;
            for (var e : last.entrySet()) {
                var now = h.getLevel().getBlockState(e.getKey()).getBlock();
                if (now != e.getValue() && h.getLevel().getBlockState(e.getKey()).isAir() && !order.wasBrokenBySwarm(e.getKey())) {
                    var near = h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(e.getKey()).inflate(3), x -> true);
                    String msg = "Zelle " + e.getKey().subtract(h.absolutePos(BlockPos.ZERO)) + " war " + e.getValue().getDescriptionId() + ", ist Luft ohne Zutun des Schwarms (Welt-Tick "
                            + h.getLevel().getGameTime() + ", bearbeitet " + done + ", aktive Ziele " + java.util.Optional.ofNullable(NanoSwarms.get(sp)).map(NanoSwarm::activeTargets).orElse(java.util.List.of())
                            + ", Entities in 3 Bloecken: " + near.stream().map(x -> x.getType().getDescriptionId() + "@" + x.blockPosition().toShortString()).toList() + ")";
                    external.add(msg);
                    NuclearSQ.LOGGER.info("[nanobots-test] {}", msg);
                }
                e.setValue(now);
            }
        });
        h.succeedWhen(() -> {
            for (BlockPos c : cells) {
                if (!h.getBlockState(c).isAir()) throw new GameTestAssertException("Block steht noch bei " + c);
            }
            if (order.done() + order.gone() + order.skipped() != order.total()) throw new GameTestAssertException("Bilanz stimmt nicht: " + order.report());
            int inWorld = 0;
            for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(40))) {
                if (e.getItem().is(Items.COBBLESTONE)) inWorld += e.getItem().getCount();
            }
            if (count(sp, Items.COBBLESTONE) + inWorld != 18 || count(sp, Items.COBBLESTONE) != 18 || !external.isEmpty()) {
                throw new GameTestAssertException("Bruchstein im Inventar: " + count(sp, Items.COBBLESTONE) + ", in der Welt " + inWorld + ", statt 18 (" + order.report() + ")"
                        + (external.isEmpty() ? "" : "; von aussen entfernt: " + external));
            }
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_fill", timeoutTicks = 3000)
    public static void gadgetFillsAndUndoes(GameTestHelper h) {
        ServerPlayer sp = player(h, "gfill", true);
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 64));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(4, 4, 4), new BlockPos(6, 4, 6), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) {
                if (!h.getBlockState(new BlockPos(x, 4, z)).is(Blocks.STONE_BRICKS)) throw new GameTestAssertException("noch nicht gebaut bei " + x + " " + z);
            }
            if (count(sp, Items.STONE_BRICKS) != 55) throw new GameTestAssertException("Material im Inventar: " + count(sp, Items.STONE_BRICKS) + " statt 55");
            if (!com.sqc.nuclearsq.nanobot.NanoGadget.canUndo(sp)) throw new GameTestAssertException("Rueckgaengig nicht moeglich");
            com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, can);
            for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) {
                if (!h.getBlockState(new BlockPos(x, 4, z)).isAir()) throw new GameTestAssertException("Rueckgaengig liess einen Block stehen bei " + x + " " + z);
            }
            if (count(sp, Items.STONE_BRICKS) != 64) throw new GameTestAssertException("Erstattung: " + count(sp, Items.STONE_BRICKS) + " statt 64");
            if (com.sqc.nuclearsq.nanobot.NanoGadget.canUndo(sp)) throw new GameTestAssertException("Rueckgaengig bleibt moeglich");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_replace", timeoutTicks = 3500)
    public static void gadgetReplaces(GameTestHelper h) {
        ServerPlayer sp = player(h, "grepl", true);
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.DIRT.defaultBlockState());
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 16));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_REPLACE, new BlockPos(4, 4, 4), new BlockPos(6, 4, 6), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) {
                if (!h.getBlockState(new BlockPos(x, 4, z)).is(Blocks.STONE_BRICKS)) throw new GameTestAssertException("nicht ersetzt bei " + x + " " + z);
            }
            if (count(sp, Items.DIRT) != 9) throw new GameTestAssertException("Erde im Inventar: " + count(sp, Items.DIRT) + " statt 9");
            if (count(sp, Items.STONE_BRICKS) != 7) throw new GameTestAssertException("Material: " + count(sp, Items.STONE_BRICKS) + " statt 7");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_creative", timeoutTicks = 2000)
    public static void gadgetCreativeNeedsNoMaterial(GameTestHelper h) {
        ServerPlayer sp = player(h, "gcreative", true);
        boolean old = sp.getAbilities().instabuild;
        sp.getAbilities().instabuild = true;
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 1));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(4, 4, 4), new BlockPos(8, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.LINE, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 4; x <= 8; x++) {
                if (!h.getBlockState(new BlockPos(x, 4, 4)).is(Blocks.STONE_BRICKS)) throw new GameTestAssertException("Linie unvollstaendig bei x=" + x);
            }
            if (count(sp, Items.STONE_BRICKS) != 1) throw new GameTestAssertException("Kreativ hat Material verbraucht: " + count(sp, Items.STONE_BRICKS));
            sp.getAbilities().instabuild = old;
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_shapes", timeoutTicks = 20)
    public static void gadgetShapes(GameTestHelper h) {
        BlockPos a = new BlockPos(0, 0, 0), b = new BlockPos(3, 3, 3);
        int[] expect = {64, 56, 16, 4};
        for (int shape = 0; shape < 4; shape++) {
            int n = com.sqc.nuclearsq.nanobot.NanoGadget.cells(shape, a, b, 4096).size();
            if (n != expect[shape]) throw new GameTestAssertException("Form " + shape + ": " + n + " Bloecke statt " + expect[shape]);
        }
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.cells(0, a, new BlockPos(40, 40, 40), 4096).isEmpty()) throw new GameTestAssertException("zu grosser Bereich wurde angenommen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_validate", timeoutTicks = 40)
    public static void gadgetValidatesOrders(GameTestHelper h) {
        ServerPlayer sp = player(h, "gvalid", true);
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(4, 4, 4), new BlockPos(5, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("ohne Block in der Nebenhand bestaetigt");
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 4));
        can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(4, 4, 4), new BlockPos(14, 14, 14), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        can.set(NanobotRegistry.RADIUS.get(), 4);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Bereich ausserhalb des Radius 4 bestaetigt");
        can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(4, 4, 4), new BlockPos(5, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.PORT);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Quelle PORT ohne Port bestaetigt");
        h.setBlock(new BlockPos(4, 4, 4), Blocks.STONE_BRICKS.defaultBlockState());
        can = gadget(h, sp, NanoTask.GADGET_REPLACE, new BlockPos(4, 4, 4), new BlockPos(5, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Ersetzen durch denselben Block bestaetigt");
        can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(4, 4, 4), new BlockPos(4, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, com.sqc.nuclearsq.nanobot.NanoGadget.data(can).withPoints(java.util.Optional.of(h.absolutePos(new BlockPos(4, 4, 4))), java.util.Optional.empty()));
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("mit einem Punkt bestaetigt");
        gadgetDone(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_port", timeoutTicks = 3500)
    public static void gadgetTakesMaterialFromPort(GameTestHelper h) {
        ServerPlayer sp = player(h, "gport", true);
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2));
        for (var c : com.sqc.nuclearsq.machine.big.BigMachine.SERVER_RACK_SMALL.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity rack) || rack.rack() == null) throw new GameTestAssertException("kein Rack-Controller");
        BlockPos portCell = rack.machine().structure.world(ctrl, 0, 0, 0, 1);
        BlockPos behind = portCell.offset(portCell.subtract(ctrl));
        h.getLevel().setBlockAndUpdate(behind, com.sqc.nuclearsq.data.DataRegistry.CABLE.get().defaultBlockState());
        BlockPos port = behind.offset(behind.subtract(portCell));
        h.getLevel().setBlockAndUpdate(port, NanobotRegistry.DRONE_ITEM_PORT.get().defaultBlockState());
        rack.rack().drives().setStackInSlot(0, new ItemStack(com.sqc.nuclearsq.storage.StorageRegistry.HARD_DRIVE_1.get()));
        rack.energy().add(rack.energy().getMaxEnergyStored());
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 1));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(9, 4, 9), new BlockPos(11, 4, 11), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.PORT);
        var key = com.sqc.nuclearsq.storage.ItemKey.of(new ItemStack(Items.STONE_BRICKS));
        h.runAfterDelay(25, () -> {
            var access = com.sqc.nuclearsq.storage.StorageNet.access(h.getLevel(), port);
            if (access.onlineRacks() != 1) throw new GameTestAssertException("Rack nicht online (Port sieht " + access.onlineRacks() + ")");
            if (access.insert(key, 100, false) != 100) throw new GameTestAssertException("Einlagern von 100 Steinziegeln schlug fehl");
            com.sqc.nuclearsq.nanobot.NanoPort.couple(sp, can, port);
            if (com.sqc.nuclearsq.nanobot.NanoPort.access(sp, can) == null) throw new GameTestAssertException("gekoppelter Port liefert keinen Speicherzugriff");
            if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        });
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 9; x <= 11; x++) for (int z = 9; z <= 11; z++) {
                if (!h.getBlockState(new BlockPos(x, 4, z)).is(Blocks.STONE_BRICKS)) throw new GameTestAssertException("noch nicht gebaut bei " + x + " " + z);
            }
            long left = com.sqc.nuclearsq.storage.StorageNet.access(h.getLevel(), port).count(key);
            if (left != 91) throw new GameTestAssertException("Speicher enthaelt " + left + " statt 91 Steinziegel");
            if (count(sp, Items.STONE_BRICKS) != 1) throw new GameTestAssertException("Inventar wurde angetastet: " + count(sp, Items.STONE_BRICKS));
            gadgetDone(sp);
        });
    }

    private static ItemStack threeBotCanister(ServerPlayer sp, NanoTask task) {
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), task.ordinal());
        net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        list.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("count_2").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(list));
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        return can;
    }

    private static int[] watchClaims(GameTestHelper h, ServerPlayer sp, NanoSwarm sw) {
        int[] maxParallel = {0};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            var targets = sw.activeTargets();
            if (new java.util.HashSet<>(targets).size() != targets.size()) throw new GameTestAssertException("zwei Bots am selben Block: " + targets);
            maxParallel[0] = Math.max(maxParallel[0], targets.size());
        });
        return maxParallel;
    }

    @GameTest(template = FLOOR, batch = "nanobots_claims_tree", timeoutTicks = 5000)
    public static void claimsGiveEachBotItsOwnBlock(GameTestHelper h) {
        ServerPlayer sp = player(h, "claims", true);
        for (int[] t : new int[][] {{4, 4}, {10, 10}}) {
            h.setBlock(new BlockPos(t[0], 2, t[1]), Blocks.DIRT.defaultBlockState());
            for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(t[0], y, t[1]), Blocks.OAK_LOG.defaultBlockState());
            h.setBlock(new BlockPos(t[0], 9, t[1]), Blocks.OAK_LEAVES.defaultBlockState());
        }
        threeBotCanister(sp, NanoTask.FELL_TREE);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        if (sw.botCount() != 4) throw new GameTestAssertException("Anzahl-Upgrade ergibt " + sw.botCount() + " Bots statt 4");
        int[] maxParallel = watchClaims(h, sp, sw);
        h.succeedWhen(() -> {
            for (int y = 3; y <= 8; y++) {
                if (h.getBlockState(new BlockPos(4, y, 4)).is(Blocks.OAK_LOG) || h.getBlockState(new BlockPos(10, y, 10)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("Stamm bei y=" + y + " steht noch");
            }
            if (maxParallel[0] < 2) throw new GameTestAssertException("nie zwei Bots gleichzeitig (hoechstens " + maxParallel[0] + "): zwei Baeume, also zwei Bots erwartet");
            if (count(sp, Items.OAK_LOG) != 12) throw new GameTestAssertException("Baumstaemme im Inventar: " + count(sp, Items.OAK_LOG) + " statt 12");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_claims_recall", timeoutTicks = 1200)
    public static void claimsAreReleasedOnRecall(GameTestHelper h) {
        ServerPlayer sp = player(h, "claimrecall", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        h.setBlock(new BlockPos(6, 9, 6), Blocks.OAK_LEAVES.defaultBlockState());
        threeBotCanister(sp, NanoTask.FELL_TREE);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        watchClaims(h, sp, sw);
        boolean[] released = {false};
        h.onEachTick(() -> {
            if (released[0] || sw.isEnded() || sw.activeTargets().isEmpty()) return;
            sw.recall();
            if (!sw.activeTargets().isEmpty()) throw new GameTestAssertException("Claims nach dem Rueckruf nicht frei: " + sw.activeTargets());
            released[0] = true;
        });
        h.succeedWhen(() -> {
            if (!released[0]) throw new GameTestAssertException("kein Bot hat bis jetzt ein Ziel (Phase " + sw.phase() + ")");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_parallel", timeoutTicks = 3500)
    public static void gadgetWorksInParallelWithoutDoubles(GameTestHelper h) {
        ServerPlayer sp = player(h, "gpar", true);
        for (int x = 4; x <= 6; x++) for (int y = 4; y <= 5; y++) for (int z = 4; z <= 6; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        ItemStack can = threeBotCanister(sp, NanoTask.GADGET_BREAK);
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, new com.sqc.nuclearsq.nanobot.GadgetData(com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY,
                java.util.Optional.of(h.absolutePos(new BlockPos(4, 4, 4))), java.util.Optional.of(h.absolutePos(new BlockPos(6, 5, 6)))));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw == null || sw.botCount() != 4) throw new GameTestAssertException("kein Schwarm mit vier Bots");
        int[] maxParallel = watchClaims(h, sp, sw);
        h.succeedWhen(() -> {
            var o = com.sqc.nuclearsq.nanobot.NanoGadget.last(sp);
            for (int x = 4; x <= 6; x++) for (int y = 4; y <= 5; y++) for (int z = 4; z <= 6; z++) {
                if (!h.getBlockState(new BlockPos(x, y, z)).isAir()) throw new GameTestAssertException("Block steht noch bei " + x + " " + y + " " + z);
            }
            if (maxParallel[0] < 2) throw new GameTestAssertException("nie zwei Bots gleichzeitig (hoechstens " + maxParallel[0] + ")");
            if (o == null || o.done() != 18 || o.skipped() != 0 || o.gone() != 0) throw new GameTestAssertException("Bilanz: " + (o == null ? "kein Auftrag" : o.report()));
            if (count(sp, Items.COBBLESTONE) != 18) throw new GameTestAssertException("Bruchstein im Inventar: " + count(sp, Items.COBBLESTONE) + " statt 18 (" + o.report() + ")");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_smooth", timeoutTicks = 20)
    public static void smoothingHasNoSnapsOrOvershoot(GameTestHelper h) {
        double[] vel = {0};
        double cur = 0, prev = 0, maxStep = 0, prevStep = 0, maxAccel = 0;
        for (int t = 0; t < 80; t++) {
            prev = cur;
            cur = com.sqc.nuclearsq.nanobot.NanoSmooth.damp(cur, 10.0, vel, 5.0, 1.0);
            if (cur > 10.0 + 1e-9) throw new GameTestAssertException("Ueberschwingen bei Tick " + t + ": " + cur);
            if (cur < prev - 1e-9) throw new GameTestAssertException("rueckwaerts bei Tick " + t);
            double step = cur - prev;
            maxStep = Math.max(maxStep, step);
            if (t > 0) maxAccel = Math.max(maxAccel, Math.abs(step - prevStep));
            prevStep = step;
        }
        if (Math.abs(cur - 10.0) > 0.05) throw new GameTestAssertException("Ziel nach 80 Ticks nicht erreicht: " + cur);
        if (maxStep > 2.5) throw new GameTestAssertException("groesster Schritt je Tick " + maxStep + " (Sprung statt Gleiten)");
        if (maxAccel > 1.2) throw new GameTestAssertException("Geschwindigkeit nicht stetig: " + maxAccel);
        java.util.Random rnd = new java.util.Random(7);
        double server = 0, shown = 0, snap = 0, v2[] = {0}, smoothJerk = 0, snapJerk = 0, lastS = 0, lastP = 0, lastShownStep = 0, lastSnapStep = 0;
        int next = 0;
        for (int t = 0; t < 200; t++) {
            server += 0.1;
            if (t >= next) {
                snap = server;
                next = t + 2 + rnd.nextInt(3);
            }
            double target = snap;
            double before = shown;
            shown = com.sqc.nuclearsq.nanobot.NanoSmooth.damp(shown, target, v2, 5.0, 1.0);
            double stepS = shown - before, stepP = snap - lastP;
            smoothJerk = Math.max(smoothJerk, Math.abs(stepS - lastShownStep));
            snapJerk = Math.max(snapJerk, Math.abs(stepP - lastSnapStep));
            lastShownStep = stepS;
            lastSnapStep = stepP;
            lastP = snap;
        }
        if (smoothJerk >= snapJerk * 0.6) throw new GameTestAssertException("Glaettung ist nicht ruhiger als hartes Uebernehmen: " + smoothJerk + " gegen " + snapJerk);
        h.succeed();
    }

    private static void crown(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 8, 6), Blocks.OAK_LEAVES.defaultBlockState());
        h.setBlock(new BlockPos(8, 7, 6), Blocks.OAK_LEAVES.defaultBlockState());
    }

    private static int totalItems(ServerPlayer sp) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(com.sqc.nuclearsq.exo.ExoRegistry.CORE.get()) && !st.is(Items.IRON_PICKAXE) && !(st.getItem() instanceof NanoCanisterItem) && !st.is(Items.STONE_BRICKS)) n += st.getCount();
        }
        return n;
    }

    @GameTest(template = FLOOR, batch = "nanobots_chain_tree", timeoutTicks = 3000)
    public static void chainsBlocksWithoutSearchOrDeliveryBetween(GameTestHelper h) {
        ServerPlayer sp = player(h, "chain", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        crown(h);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        boolean[] sawWork = {false};
        int[] deliveries = {0}, lastLogs = {0};
        String[] bad = {null};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            boolean anyLog = false;
            for (int y = 3; y <= 8; y++) if (h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) anyLog = true;
            if (sw.phase() == NanoSwarm.Phase.WORK) sawWork[0] = true;
            else if (sawWork[0] && anyLog && bad[0] == null) bad[0] = "Phase " + sw.phase() + " zwischen zwei Bloecken (Staemme stehen noch)";
            int logs = count(sp, Items.OAK_LOG);
            if (logs > lastLogs[0]) deliveries[0]++;
            lastLogs[0] = logs;
        });
        h.succeedWhen(() -> {
            if (bad[0] != null) throw new GameTestAssertException(bad[0]);
            for (int y = 3; y <= 8; y++) if (h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("Stamm bei y=" + y + " steht noch");
            if (count(sp, Items.OAK_LOG) != 6) throw new GameTestAssertException("Baumstaemme im Inventar: " + count(sp, Items.OAK_LOG) + " statt 6 (Lieferungen: " + deliveries[0] + ")");
            if (deliveries[0] != 1) throw new GameTestAssertException("Lieferungen: " + deliveries[0] + " statt genau einer am Ende");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_chain_full", timeoutTicks = 3500)
    public static void deliversWhenCargoIsFullAndAtTheEnd(GameTestHelper h) {
        ServerPlayer sp = player(h, "chainfull", true);
        net.minecraft.world.level.block.Block[] kinds = {Blocks.STONE, Blocks.DIRT, Blocks.SAND, Blocks.OAK_PLANKS, Blocks.NETHERRACK, Blocks.TERRACOTTA, Blocks.BRICKS,
                Blocks.SANDSTONE, Blocks.END_STONE, Blocks.BLACKSTONE, Blocks.DEEPSLATE};
        for (int i = 0; i < kinds.length; i++) h.setBlock(new BlockPos(4 + i, 4, 4), kinds[i].defaultBlockState());
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(4, 4, 4), new BlockPos(14, 4, 4), com.sqc.nuclearsq.nanobot.GadgetData.LINE, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        NanoSwarm sw = NanoSwarms.get(sp);
        int[] deliveries = {0}, last = {0};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            int n = totalItems(sp);
            if (n > last[0]) deliveries[0]++;
            last[0] = n;
        });
        h.succeedWhen(() -> {
            for (int i = 0; i < kinds.length; i++) if (!h.getBlockState(new BlockPos(4 + i, 4, 4)).isAir()) throw new GameTestAssertException("Block " + i + " steht noch");
            if (totalItems(sp) != 11) throw new GameTestAssertException("Items im Inventar: " + totalItems(sp) + " statt 11 (Lieferungen: " + deliveries[0] + ", " + com.sqc.nuclearsq.nanobot.NanoGadget.last(sp).report() + ")");
            if (deliveries[0] != 2) throw new GameTestAssertException("Lieferungen: " + deliveries[0] + " statt 2 (volle Fracht bei 8 Stapeln, Rest am Ende)");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_chain_recall", timeoutTicks = 1500)
    public static void recallDeliversTheCargoOnBoard(GameTestHelper h) {
        ServerPlayer sp = player(h, "chainrecall", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        crown(h);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        boolean[] recalled = {false};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            if (!recalled[0] && sw.cargoStacks() > 0) {
                if (count(sp, Items.OAK_LOG) != 0) throw new GameTestAssertException("Fracht wurde schon vor dem Rueckruf geliefert");
                sw.recall();
                recalled[0] = true;
            }
        });
        h.succeedWhen(() -> {
            if (!recalled[0] || !sw.isEnded()) throw new GameTestAssertException("Schwarm noch nicht beendet (Phase " + sw.phase() + ")");
            int broken = 0;
            for (int y = 3; y <= 8; y++) if (!h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) broken++;
            if (broken < 1 || count(sp, Items.OAK_LOG) != broken) throw new GameTestAssertException("gebrochen " + broken + ", im Inventar " + count(sp, Items.OAK_LOG) + ": der Rueckruf hat die Fracht nicht vollstaendig geliefert");
            if (sw.cargoStacks() != 0) throw new GameTestAssertException("Fracht blieb im Schwarm: " + sw.cargoStacks());
            done(sp);
        });
    }

    private static com.sqc.nuclearsq.nanobot.GadgetData round(int shape, int radius, int axis, int height, boolean hollow) {
        return new com.sqc.nuclearsq.nanobot.GadgetData(shape, 0, java.util.Optional.of(BlockPos.ZERO), java.util.Optional.empty(), hollow, radius, axis, height, com.sqc.nuclearsq.nanobot.GadgetData.Paste.DEFAULT);
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_round", timeoutTicks = 20)
    public static void gadgetRoundShapes(GameTestHelper h) {
        int sphere1 = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 1, 1, 1, false), 4096).size();
        int sphere2 = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 2, 1, 1, false), 4096).size();
        if (sphere1 != 19 || sphere2 != 81) throw new GameTestAssertException("Kugel: Radius 1 = " + sphere1 + " (19), Radius 2 = " + sphere2 + " (81)");
        var full = new java.util.HashSet<>(com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 2, 1, 1, false), 4096));
        var shell = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 2, 1, 1, true), 4096);
        for (BlockPos c : full) {
            boolean edge = false;
            for (var d : net.minecraft.core.Direction.values()) if (!full.contains(c.relative(d))) edge = true;
            if (edge != shell.contains(c)) throw new GameTestAssertException("hohle Kugel: Zelle " + c + (edge ? " fehlt in der Schale" : " gehoert nicht zur Schale"));
        }
        var circle = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.CIRCLE, 2, 1, 1, false), 4096);
        if (circle.size() != 21 || circle.stream().anyMatch(c -> c.getY() != 0)) throw new GameTestAssertException("Kreis Achse Y: " + circle.size() + " Zellen (21), flach bei y=0");
        var circleX = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.CIRCLE, 2, 0, 1, false), 4096);
        if (circleX.size() != 21 || circleX.stream().anyMatch(c -> c.getX() != 0)) throw new GameTestAssertException("Kreis Achse X: flach bei x=0 erwartet");
        int cyl = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.CYLINDER, 2, 1, 3, false), 4096).size();
        int tube = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.CYLINDER, 2, 1, 3, true), 4096).size();
        if (cyl != 63 || tube != 36) throw new GameTestAssertException("Zylinder: " + cyl + " (63), Rohr " + tube + " (36)");
        var cylZ = com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.CYLINDER, 2, 2, 3, false), 4096);
        int zMin = cylZ.stream().mapToInt(BlockPos::getZ).min().orElse(9), zMax = cylZ.stream().mapToInt(BlockPos::getZ).max().orElse(-9);
        if (zMin != 0 || zMax != 2) throw new GameTestAssertException("Zylinder Achse Z: z von " + zMin + " bis " + zMax + " (0..2)");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.cells(round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 32, 1, 1, false), 4096).isEmpty()) throw new GameTestAssertException("zu grosse Kugel wurde angenommen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_oneclick", timeoutTicks = 40)
    public static void gadgetRoundNeedsOnlyOneClick(GameTestHelper h) {
        ServerPlayer sp = player(h, "goneclick", true);
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 64));
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), NanoTask.GADGET_BUILD.ordinal());
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, round(com.sqc.nuclearsq.nanobot.GadgetData.SPHERE, 1, 1, 1, false).withPoints(java.util.Optional.empty(), java.util.Optional.empty()));
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        BlockPos floor = h.absolutePos(new BlockPos(7, 3, 7));
        com.sqc.nuclearsq.nanobot.NanoGadget.click(sp, can, floor, net.minecraft.core.Direction.UP);
        var d = com.sqc.nuclearsq.nanobot.NanoGadget.data(can);
        if (d.p1().isEmpty() || !d.ready() || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("erster Klick: Mittelpunkt nicht gesetzt oder schon bestaetigt: " + d);
        if (!d.p1().get().equals(floor.above())) throw new GameTestAssertException("Mittelpunkt beim Bauen neben dem Block erwartet: " + floor.above() + ", ist " + d.p1().get() + " (angeklickt " + floor + ", Seite UP)");
        com.sqc.nuclearsq.nanobot.NanoGadget.click(sp, can, floor, net.minecraft.core.Direction.UP);
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        if (o == null || o.total() != 19) throw new GameTestAssertException("zweiter Klick: Auftrag " + (o == null ? "fehlt" : o.total() + " Bloecke statt 19"));
        gadgetDone(sp);
        h.succeed();
    }

    private static boolean allCells(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) if (!h.getBlockState(new BlockPos(x, 4, z)).is(block)) return false;
        return true;
    }

    private static boolean swarmIdle(ServerPlayer sp) {
        NanoSwarm sw = NanoSwarms.get(sp);
        return sw == null || sw.isEnded();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_undo_break", timeoutTicks = 4000)
    public static void gadgetUndoRestoresBrokenBlocksAndRedoBreaksAgain(GameTestHelper h) {
        ServerPlayer sp = player(h, "gundo", true);
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE.defaultBlockState());
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(8, 4, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        int[] stage = {0};
        h.onEachTick(() -> {
            NanoSwarm sw = NanoSwarms.get(sp);
            if (sw != null && !sw.isEnded()) sw.tick(sp);
            boolean gone = true;
            for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) if (!h.getBlockState(new BlockPos(x, 4, z)).isAir()) gone = false;
            if (stage[0] == 0 && gone && swarmIdle(sp)) {
                if (count(sp, Items.COBBLESTONE) != 9) throw new GameTestAssertException("vor Rueckgaengig: Bruchstein " + count(sp, Items.COBBLESTONE) + " statt 9");
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.canUndo(sp)) throw new GameTestAssertException("Rueckgaengig nicht moeglich");
                com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, can);
                if (!allCells(h, Blocks.STONE)) throw new GameTestAssertException("Rueckgaengig hat nicht alle 9 Steine wiederhergestellt");
                if (count(sp, Items.COBBLESTONE) != 0) throw new GameTestAssertException("Drops nicht wieder entnommen: Bruchstein " + count(sp, Items.COBBLESTONE) + " statt 0");
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.canRedo(sp) || com.sqc.nuclearsq.nanobot.NanoGadget.canUndo(sp)) throw new GameTestAssertException("Verlauf nach Rueckgaengig: Wiederholen ja, Rueckgaengig nein erwartet");
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.redo(sp, can)) throw new GameTestAssertException("Wiederholen nicht gestartet");
                stage[0] = 1;
            }
        });
        h.succeedWhen(() -> {
            if (stage[0] != 1) throw new GameTestAssertException("noch im ersten Durchlauf");
            for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) if (!h.getBlockState(new BlockPos(x, 4, z)).isAir()) throw new GameTestAssertException("nach Wiederholen steht noch Stein bei " + x + " " + z);
            if (!swarmIdle(sp)) throw new GameTestAssertException("Schwarm laeuft noch");
            if (count(sp, Items.COBBLESTONE) != 9) throw new GameTestAssertException("nach Wiederholen: Bruchstein " + count(sp, Items.COBBLESTONE) + " statt 9");
            if (com.sqc.nuclearsq.nanobot.NanoGadget.canRedo(sp) || !com.sqc.nuclearsq.nanobot.NanoGadget.canUndo(sp)) throw new GameTestAssertException("Verlauf nach Wiederholen: Rueckgaengig ja, Wiederholen nein erwartet");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_undo_build", timeoutTicks = 4000)
    public static void gadgetUndoRemovesBuiltBlocksAndRedoBuildsAgain(GameTestHelper h) {
        ServerPlayer sp = player(h, "gundob", true);
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 64));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(6, 4, 6), new BlockPos(8, 4, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        int[] stage = {0};
        h.onEachTick(() -> {
            NanoSwarm sw = NanoSwarms.get(sp);
            if (sw != null && !sw.isEnded()) sw.tick(sp);
            if (stage[0] == 0 && allCells(h, Blocks.STONE_BRICKS) && swarmIdle(sp)) {
                if (count(sp, Items.STONE_BRICKS) != 55) throw new GameTestAssertException("vor Rueckgaengig: Material " + count(sp, Items.STONE_BRICKS) + " statt 55");
                com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, can);
                if (!allCells(h, Blocks.AIR)) throw new GameTestAssertException("Rueckgaengig hat nicht alle gebauten Bloecke entfernt");
                if (count(sp, Items.STONE_BRICKS) != 64) throw new GameTestAssertException("Erstattung: " + count(sp, Items.STONE_BRICKS) + " statt 64");
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.redo(sp, can)) throw new GameTestAssertException("Wiederholen nicht gestartet");
                stage[0] = 1;
            }
        });
        h.succeedWhen(() -> {
            if (stage[0] != 1) throw new GameTestAssertException("noch im ersten Durchlauf");
            if (!allCells(h, Blocks.STONE_BRICKS)) throw new GameTestAssertException("nach Wiederholen nicht alles gebaut");
            if (!swarmIdle(sp)) throw new GameTestAssertException("Schwarm laeuft noch");
            if (count(sp, Items.STONE_BRICKS) != 55) throw new GameTestAssertException("nach Wiederholen: Material " + count(sp, Items.STONE_BRICKS) + " statt 55");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_history", timeoutTicks = 40)
    public static void gadgetHistoryKeepsTheLastOrders(GameTestHelper h) {
        ServerPlayer sp = player(h, "ghist", true);
        int old = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_HISTORY);
        try {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_HISTORY.set(2);
            ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(6, 4, 6), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
            com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, can);
            if (com.sqc.nuclearsq.nanobot.NanoGadget.redo(sp, can)) throw new GameTestAssertException("Wiederholen ohne Verlauf darf nichts starten");
            for (int i = 0; i < 3; i++) {
                com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, new com.sqc.nuclearsq.nanobot.GadgetData(0, 0, java.util.Optional.of(h.absolutePos(new BlockPos(6 + i, 4, 6))),
                        java.util.Optional.of(h.absolutePos(new BlockPos(6 + i, 4, 6)))));
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag " + i + " nicht bestaetigt");
            }
            if (com.sqc.nuclearsq.nanobot.NanoGadget.historySize(sp) != 2) throw new GameTestAssertException("Verlauf: " + com.sqc.nuclearsq.nanobot.NanoGadget.historySize(sp) + " statt 2");
        } finally {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_HISTORY.set(old);
        }
        gadgetDone(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_keys", timeoutTicks = 40)
    public static void gadgetKeysChangeTheSettings(GameTestHelper h) {
        ServerPlayer sp = player(h, "gkeys", true);
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), NanoTask.FELL_TREE.ordinal());
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, round(com.sqc.nuclearsq.nanobot.GadgetData.CYLINDER, 3, 1, 3, false));
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, com.sqc.nuclearsq.nanobot.NanoGadget.KEY_SIZE, 1);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.data(can).radius() != 3) throw new GameTestAssertException("Radius aenderte sich ausserhalb des Gadget-Modus");
        can.set(NanobotRegistry.TASK.get(), NanoTask.GADGET_BUILD.ordinal());
        com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, com.sqc.nuclearsq.nanobot.NanoGadget.KEY_SIZE, 1);
        com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, com.sqc.nuclearsq.nanobot.NanoGadget.KEY_HEIGHT, -1);
        var d = com.sqc.nuclearsq.nanobot.NanoGadget.data(can);
        if (d.radius() != 4 || d.height() != 2) throw new GameTestAssertException("Radius/Hoehe: " + d.radius() + "/" + d.height() + " statt 4/2");
        for (int i = 0; i < 60; i++) com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, com.sqc.nuclearsq.nanobot.NanoGadget.KEY_SIZE, 1);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.data(can).radius() != com.sqc.nuclearsq.nanobot.GadgetData.MAX_RADIUS) throw new GameTestAssertException("Radius nicht auf das Maximum geklemmt");
        com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, com.sqc.nuclearsq.nanobot.NanoGadget.KEY_SHAPE, 1);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.data(can).shape() != com.sqc.nuclearsq.nanobot.GadgetData.CYLINDER + 1 - com.sqc.nuclearsq.nanobot.GadgetData.SHAPES) throw new GameTestAssertException("naechste Form nach Zylinder ist wieder der Quader");
        gadgetDone(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_surface", timeoutTicks = 20)
    public static void surfaceFramesAreRotations(GameTestHelper h) {
        Vec3 probe = new Vec3(1.25, -2.5, 3.75);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            var f = com.sqc.nuclearsq.nanobot.client.Surface.of(d);
            Vec3 ex = Vec3.atLowerCornerOf(f.ex.getNormal()), n = Vec3.atLowerCornerOf(f.n.getNormal()), ez = Vec3.atLowerCornerOf(f.ez.getNormal());
            if (ex.cross(n).distanceTo(ez) > 1e-9) throw new GameTestAssertException("Rahmen nicht rechtshaendig: " + d);
            if (f.toWorld(0, 1, 0).distanceTo(n) > 1e-9) throw new GameTestAssertException("ly zeigt nicht entlang der Normalen: " + d);
            Vec3 l = f.toLocal(probe);
            if (f.toWorld(l.x, l.y, l.z).distanceTo(probe) > 1e-9) throw new GameTestAssertException("Umrechnung nicht umkehrbar: " + d);
            org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(f.quat);
            if (up.distance((float) n.x, (float) n.y, (float) n.z) > 1e-4) throw new GameTestAssertException("Ausrichtung kippt die Normale nicht: " + d);
            for (net.minecraft.core.Direction e : net.minecraft.core.Direction.values()) {
                var g = com.sqc.nuclearsq.nanobot.client.Surface.of(e);
                for (int k = 0; k <= 10; k++) {
                    org.joml.Quaternionf q = new org.joml.Quaternionf(f.quat).slerp(g.quat, k / 10f);
                    float len = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
                    if (!Float.isFinite(len) || Math.abs(len - 1f) > 1e-3f) throw new GameTestAssertException("Uebergang " + d + " -> " + e + " ungueltig bei " + k);
                    org.joml.Matrix3f m = new org.joml.Matrix3f().set(q);
                    if (Math.abs(m.determinant() - 1f) > 1e-3f) throw new GameTestAssertException("Matrix entartet " + d + " -> " + e + " bei " + k);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_payload", timeoutTicks = 20)
    public static void payloadRoundTrip(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        long[] targets = {123456789L, NanoSwarmPayload.NONE, -42L};
        int[] progress = {42, -1, 0};
        NanoSwarmPayload p = new NanoSwarmPayload(owner, new Vec3(1234567.25, 64.5, -7654321.75), 3, targets, progress, 15);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        NanoSwarmPayload.STREAM_CODEC.encode(buf, p);
        NanoSwarmPayload q = NanoSwarmPayload.STREAM_CODEC.decode(buf);
        if (!q.owner().equals(owner) || !q.center().equals(p.center()) || q.state() != 3 || !java.util.Arrays.equals(q.targets(), targets) || !java.util.Arrays.equals(q.progress(), progress)
                || q.count() != 3 || q.speed() != 15 || buf.isReadable()) {
            throw new GameTestAssertException("Paket nach dem Lesen anders");
        }
        if (NanoSwarmPayload.gone(owner).count() != 0) throw new GameTestAssertException("gone-Paket hat Bots");
        long[] t36 = new long[10];
        int[] p36 = new int[10];
        for (int i = 0; i < 10; i++) {
            t36[i] = i % 3 == 0 ? NanoSwarmPayload.NONE : 1000L + i;
            p36[i] = i % 3 == 0 ? -1 : i;
        }
        RegistryFriendlyByteBuf big = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        NanoSwarmPayload.STREAM_CODEC.encode(big, new NanoSwarmPayload(owner, new Vec3(1, 2, 3), 3, t36, p36, 70));
        NanoSwarmPayload r = NanoSwarmPayload.STREAM_CODEC.decode(big);
        if (r.count() != 10 || r.speed() != 70 || !java.util.Arrays.equals(r.targets(), t36) || !java.util.Arrays.equals(r.progress(), p36) || big.isReadable()) {
            throw new GameTestAssertException("Paket mit 10 Bots nach dem Lesen anders (" + r.count() + " Bots, Tempo " + r.speed() + ")");
        }
        h.succeed();
    }

    private static final com.sqc.nuclearsq.nanobot.GadgetData.Paste NO_PASTE = com.sqc.nuclearsq.nanobot.GadgetData.Paste.DEFAULT;

    private static void speedy(ItemStack can) {
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
    }

    private static ItemStack pasteCan(GameTestHelper h, ServerPlayer sp, BlockPos anchorRel, com.sqc.nuclearsq.nanobot.GadgetData.Paste ps) {
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), NanoTask.GADGET_PASTE.ordinal());
        com.sqc.nuclearsq.nanobot.NanoGadget.setData(can, new com.sqc.nuclearsq.nanobot.GadgetData(com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY,
                java.util.Optional.of(h.absolutePos(anchorRel)), java.util.Optional.empty(), false, 3, 1, 3, ps));
        speedy(can);
        sp.getInventory().items.set(8, can);
        sp.getInventory().selected = 8;
        return can;
    }

    private static void tickIfRunning(ServerPlayer sp) {
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw != null && !sw.isEnded()) sw.tick(sp);
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_paste", timeoutTicks = 4000)
    public static void gadgetCopyAndPasteRotated(GameTestHelper h) {
        ServerPlayer sp = player(h, "gpaste", true);
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        h.setBlock(new BlockPos(6, 4, 6), Blocks.STONE_BRICKS.defaultBlockState());
        h.setBlock(new BlockPos(7, 4, 6), Blocks.STONE_BRICKS.defaultBlockState());
        h.setBlock(new BlockPos(6, 4, 7), Blocks.OAK_PLANKS.defaultBlockState());
        h.setBlock(new BlockPos(6, 5, 6), stairs);
        ItemStack copyCan = gadget(h, sp, NanoTask.GADGET_COPY, new BlockPos(6, 4, 6), new BlockPos(7, 5, 7), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, copyCan)) throw new GameTestAssertException("Kopieren nicht bestaetigt");
        var t = com.sqc.nuclearsq.nanobot.NanoGadget.template(sp);
        if (t == null || t.entries().size() != 4) throw new GameTestAssertException("Vorlage: " + (t == null ? "fehlt" : t.entries().size() + " Bloecke statt 4"));
        if (com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Kopieren darf keinen Bot-Auftrag anlegen");
        if (t.entries().stream().noneMatch(e -> e.rel().equals(new BlockPos(0, 1, 0)) && e.state().is(Blocks.STONE_BRICK_STAIRS))) throw new GameTestAssertException("Treppe fehlt in der Vorlage bei (0,1,0)");
        sp.getInventory().add(new ItemStack(Items.STONE_BRICKS, 8));
        sp.getInventory().add(new ItemStack(Items.OAK_PLANKS, 8));
        sp.getInventory().add(new ItemStack(Items.STONE_BRICK_STAIRS, 8));
        ItemStack can = pasteCan(h, sp, new BlockPos(10, 4, 10), NO_PASTE.withRot(1));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Einfuegen nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        if (o == null || o.total() != 4) throw new GameTestAssertException("Auftrag: " + (o == null ? "fehlt" : o.total() + " Bloecke statt 4"));
        int[] stage = {0};
        h.onEachTick(() -> {
            tickIfRunning(sp);
            if (stage[0] == 0 && pasted(h) && swarmIdle(sp)) {
                if (count(sp, Items.STONE_BRICKS) != 6 || count(sp, Items.OAK_PLANKS) != 7 || count(sp, Items.STONE_BRICK_STAIRS) != 7)
                    throw new GameTestAssertException("Material nach dem Einfuegen: Ziegel " + count(sp, Items.STONE_BRICKS) + " (6), Bretter " + count(sp, Items.OAK_PLANKS) + " (7), Treppen " + count(sp, Items.STONE_BRICK_STAIRS) + " (7)");
                com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, can);
                for (BlockPos q : new BlockPos[] {new BlockPos(10, 4, 10), new BlockPos(10, 4, 11), new BlockPos(9, 4, 10), new BlockPos(10, 5, 10)}) {
                    if (!h.getBlockState(q).isAir()) throw new GameTestAssertException("Rueckgaengig hat " + q + " nicht entfernt");
                }
                if (count(sp, Items.STONE_BRICKS) != 8 || count(sp, Items.OAK_PLANKS) != 8 || count(sp, Items.STONE_BRICK_STAIRS) != 8) throw new GameTestAssertException("Erstattung unvollstaendig");
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.redo(sp, can)) throw new GameTestAssertException("Wiederholen nicht gestartet");
                stage[0] = 1;
            }
        });
        h.succeedWhen(() -> {
            if (stage[0] != 1) throw new GameTestAssertException("noch im ersten Durchlauf");
            if (!pasted(h) || !swarmIdle(sp)) throw new GameTestAssertException("nach Wiederholen nicht alles gebaut");
            gadgetDone(sp);
        });
    }

    private static boolean pasted(GameTestHelper h) {
        BlockState st = h.getBlockState(new BlockPos(10, 5, 10));
        return h.getBlockState(new BlockPos(10, 4, 10)).is(Blocks.STONE_BRICKS) && h.getBlockState(new BlockPos(10, 4, 11)).is(Blocks.STONE_BRICKS)
                && h.getBlockState(new BlockPos(9, 4, 10)).is(Blocks.OAK_PLANKS)
                && st.is(Blocks.STONE_BRICK_STAIRS) && st.getValue(net.minecraft.world.level.block.StairBlock.FACING) == net.minecraft.core.Direction.EAST;
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_paste_modes", timeoutTicks = 4000)
    public static void gadgetPasteMirrorOffsetAndModes(GameTestHelper h) {
        ServerPlayer sp = player(h, "gpmode", true);
        h.setBlock(new BlockPos(6, 4, 6), Blocks.STONE_BRICKS.defaultBlockState());
        h.setBlock(new BlockPos(7, 4, 6), Blocks.OAK_PLANKS.defaultBlockState());
        ItemStack copyCan = gadget(h, sp, NanoTask.GADGET_COPY, new BlockPos(7, 4, 6), new BlockPos(6, 4, 6), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, copyCan)) throw new GameTestAssertException("Kopieren nicht bestaetigt");
        var t = com.sqc.nuclearsq.nanobot.NanoGadget.template(sp);
        if (t == null || t.entries().size() != 2 || t.entries().stream().noneMatch(e -> e.rel().equals(new BlockPos(1, 0, 0)) && e.state().is(Blocks.OAK_PLANKS)))
            throw new GameTestAssertException("Vorlage: Anker muss die untere Ecke (kleinstes x) sein, Brett bei (1,0,0): " + t);
        h.setBlock(new BlockPos(9, 5, 10), Blocks.DIRT.defaultBlockState());
        sp.getInventory().add(new ItemStack(Items.STONE_BRICKS, 8));
        sp.getInventory().add(new ItemStack(Items.OAK_PLANKS, 8));
        ItemStack can = pasteCan(h, sp, new BlockPos(10, 4, 10), NO_PASTE.withMirror(true).withOffset(1, 1));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Einfuegen (Luft-Modus) nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        if (o == null || o.total() != 1) throw new GameTestAssertException("Luft-Modus: Auftrag " + (o == null ? "fehlt" : o.total() + " Bloecke statt 1 (Dreck bleibt)"));
        int[] stage = {0};
        h.onEachTick(() -> {
            tickIfRunning(sp);
            if (stage[0] == 0 && h.getBlockState(new BlockPos(10, 5, 10)).is(Blocks.STONE_BRICKS) && swarmIdle(sp)) {
                if (!h.getBlockState(new BlockPos(9, 5, 10)).is(Blocks.DIRT)) throw new GameTestAssertException("Luft-Modus hat den Dreck ersetzt");
                ItemStack can2 = pasteCan(h, sp, new BlockPos(10, 4, 10), NO_PASTE.withMirror(true).withOffset(1, 1).withReplace(true));
                if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can2)) throw new GameTestAssertException("Einfuegen (Ersetzen-Modus) nicht bestaetigt");
                var o2 = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
                if (o2 == null || o2.total() != 1) throw new GameTestAssertException("Ersetzen-Modus: Auftrag " + (o2 == null ? "fehlt" : o2.total() + " Bloecke statt 1 (Ziegel steht schon)"));
                stage[0] = 1;
            } else if (stage[0] == 1 && h.getBlockState(new BlockPos(9, 5, 10)).is(Blocks.OAK_PLANKS) && swarmIdle(sp)) {
                if (count(sp, Items.DIRT) != 1) throw new GameTestAssertException("abgebauter Dreck: " + count(sp, Items.DIRT) + " statt 1 im Inventar");
                com.sqc.nuclearsq.nanobot.NanoGadget.undo(sp, sp.getInventory().getItem(8));
                if (!h.getBlockState(new BlockPos(9, 5, 10)).is(Blocks.DIRT)) throw new GameTestAssertException("Rueckgaengig hat den Dreck nicht wiederhergestellt: " + h.getBlockState(new BlockPos(9, 5, 10)));
                if (count(sp, Items.DIRT) != 0) throw new GameTestAssertException("Drop wurde nicht zurueckgenommen (Dreck im Inventar " + count(sp, Items.DIRT) + ")");
                if (count(sp, Items.OAK_PLANKS) != 8) throw new GameTestAssertException("Brett nicht erstattet: " + count(sp, Items.OAK_PLANKS));
                stage[0] = 2;
            }
        });
        h.succeedWhen(() -> {
            if (stage[0] != 2) throw new GameTestAssertException("Stufe " + stage[0] + " von 2");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_paste_check", timeoutTicks = 60)
    public static void gadgetPasteValidation(GameTestHelper h) {
        ServerPlayer sp = player(h, "gpcheck", true);
        ItemStack can = pasteCan(h, sp, new BlockPos(10, 4, 10), NO_PASTE);
        com.sqc.nuclearsq.nanobot.NanoGadget.clearAll();
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can) || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Einfuegen ohne Vorlage wurde angenommen");
        ItemStack copyCan = gadget(h, sp, NanoTask.GADGET_COPY, new BlockPos(6, 8, 6), new BlockPos(7, 8, 7), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, copyCan) || com.sqc.nuclearsq.nanobot.NanoGadget.template(sp) != null) throw new GameTestAssertException("Kopieren von Luft legte eine Vorlage an");
        for (int x = 6; x <= 7; x++) for (int z = 6; z <= 7; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE.defaultBlockState());
        int old = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS);
        try {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS.set(3);
            ItemStack big = gadget(h, sp, NanoTask.GADGET_COPY, new BlockPos(6, 4, 6), new BlockPos(7, 4, 7), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
            if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, big) || com.sqc.nuclearsq.nanobot.NanoGadget.template(sp) != null) throw new GameTestAssertException("zu grosse Auswahl (4 > 3) wurde kopiert");
        } finally {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_GADGET_MAX_BLOCKS.set(old);
        }
        ItemStack ok = gadget(h, sp, NanoTask.GADGET_COPY, new BlockPos(6, 4, 6), new BlockPos(7, 4, 7), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, ok)) throw new GameTestAssertException("gueltiges Kopieren abgelehnt");
        ItemStack far = pasteCan(h, sp, new BlockPos(10, 4, 10).offset(200, 0, 0), NO_PASTE);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, far) || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Einfuegen ausserhalb der Reichweite wurde angenommen");
        BlockPos rel = new BlockPos(2, 1, 3);
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 1, false).equals(new BlockPos(-3, 1, 2))) throw new GameTestAssertException("Drehung 90: " + com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 1, false) + " statt (-3,1,2)");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 4, false).equals(rel) || !com.sqc.nuclearsq.nanobot.NanoGadget.transform(com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 0, true), 0, true).equals(rel))
            throw new GameTestAssertException("vier Drehungen / zweimal Spiegeln ergeben nicht die Identitaet");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 1, true).equals(new BlockPos(-3, 1, -2))) throw new GameTestAssertException("Spiegeln dann Drehen: " + com.sqc.nuclearsq.nanobot.NanoGadget.transform(rel, 1, true) + " statt (-3,1,-2)");
        BlockState north = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.transform(north, 1, false).getValue(net.minecraft.world.level.block.StairBlock.FACING) != net.minecraft.core.Direction.EAST) throw new GameTestAssertException("Treppe dreht nicht mit");
        gadgetDone(sp);
        h.succeed();
    }

    private static void fast(ItemStack can, String count) {
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_3").get()));
        if (count != null) ups.set(1, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get(count).get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
    }

    private static void sealedRegion(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        for (int x = 5; x <= 11; x++) for (int y = 4; y <= 9; y++) for (int z = 5; z <= 11; z++) h.setBlock(new BlockPos(x, y, z), block.defaultBlockState());
        for (int x = 7; x <= 9; x++) for (int y = 5; y <= 7; y++) for (int z = 7; z <= 9; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
    }

    private static void watchColumns(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1, boolean down) {
        h.onEachTick(() -> {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y < y1; y++) {
                        boolean lower = h.getBlockState(new BlockPos(x, y, z)).isAir(), upper = h.getBlockState(new BlockPos(x, y + 1, z)).isAir();
                        if (lower && !upper) {
                            throw new GameTestAssertException(down ? "Abbau: Block bei (" + x + "," + y + "," + z + ") ist weg, der hoehere darueber steht noch"
                                    : "Bau: Block bei (" + x + "," + (y + 1) + "," + z + ") steht, der Block darunter fehlt noch");
                        }
                    }
                }
            }
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_tunnel", timeoutTicks = 6000)
    public static void gadgetBreakDigsTunnelAndWorksTopDown(GameTestHelper h) {
        ServerPlayer sp = player(h, "gtunnel", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        sealedRegion(h, Blocks.STONE);
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(7, 5, 7), new BlockPos(9, 7, 9), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt (kein Tunnel geplant?)");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        if (o == null || o.total() != 27 || o.tunnelTotal() != 3) throw new GameTestAssertException("Auftrag " + (o == null ? "fehlt" : o.total() + " Bloecke, Tunnel " + o.tunnelTotal()) + " (27 Bloecke, 3 Tunnelbloecke)");
        var tunnel = o.tunnelCells();
        for (BlockPos t : tunnel) {
            if (t.getX() != tunnel.get(0).getX() || t.getZ() != tunnel.get(0).getZ()) throw new GameTestAssertException("Tunnel nicht senkrecht: " + tunnel);
        }
        if (tunnel.stream().mapToInt(BlockPos::getY).min().orElse(0) != h.absolutePos(new BlockPos(0, 7, 0)).getY()
                || tunnel.stream().mapToInt(BlockPos::getY).max().orElse(0) != h.absolutePos(new BlockPos(0, 9, 0)).getY())
            throw new GameTestAssertException("Tunnel soll von y=9 (Oberseite des Blocks) bis y=7 (oberste Schicht) reichen: " + tunnel);
        watchColumns(h, 7, 9, 5, 7, 7, 9, true);
        h.onEachTick(() -> {
            tickIfRunning(sp);
            boolean regionBroken = false;
            for (int x = 7; x <= 9; x++) for (int y = 5; y <= 7; y++) for (int z = 7; z <= 9; z++) if (h.getBlockState(new BlockPos(x, y, z)).isAir()) regionBroken = true;
            if (regionBroken) {
                for (BlockPos t : tunnel) if (!sp.level().getBlockState(t).isAir()) throw new GameTestAssertException("Bereich wird abgebaut, bevor der Tunnel fertig ist (" + t + ")");
            }
        });
        h.succeedWhen(() -> {
            for (int x = 7; x <= 9; x++) for (int y = 5; y <= 7; y++) for (int z = 7; z <= 9; z++) if (!h.getBlockState(new BlockPos(x, y, z)).isAir()) throw new GameTestAssertException("Bereich nicht ganz abgebaut: " + new BlockPos(x, y, z));
            if (!swarmIdle(sp)) throw new GameTestAssertException("Schwarm laeuft noch");
            for (BlockPos t : tunnel) if (!sp.level().getBlockState(t).isAir()) throw new GameTestAssertException("Tunnelblock wurde wieder verfuellt: " + t);
            var last = com.sqc.nuclearsq.nanobot.NanoGadget.last(sp);
            if (last.done() != 27 || last.tunnelDone() != 3) throw new GameTestAssertException("Zaehler: Auftrag " + last.done() + " (27), Tunnel " + last.tunnelDone() + " (3)");
            if (count(sp, Items.COBBLESTONE) != 30) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 30 (27 + 3 Tunnel)");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_topdown", timeoutTicks = 4000)
    public static void gadgetBreakOnOpenGroundNeedsNoTunnelAndGoesTopDown(GameTestHelper h) {
        ServerPlayer sp = player(h, "gtopdown", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        for (int x = 6; x <= 8; x++) for (int y = 4; y <= 6; y++) for (int z = 6; z <= 8; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(8, 6, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        if (o == null || o.tunnelTotal() != 0) throw new GameTestAssertException("freier Bereich darf keinen Tunnel brauchen: " + (o == null ? "kein Auftrag" : o.tunnelTotal() + " Tunnelbloecke"));
        BlockPos prev = null;
        for (BlockPos q : o.ops()) {
            if (prev != null && q.getY() > prev.getY()) throw new GameTestAssertException("Abbauliste nicht von oben nach unten: " + prev + " vor " + q);
            prev = q;
        }
        watchColumns(h, 6, 8, 4, 6, 6, 8, true);
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++) for (int y = 4; y <= 6; y++) for (int z = 6; z <= 8; z++) if (!h.getBlockState(new BlockPos(x, y, z)).isAir()) throw new GameTestAssertException("noch ein Block bei " + new BlockPos(x, y, z));
            if (!swarmIdle(sp)) throw new GameTestAssertException("Schwarm laeuft noch");
            if (count(sp, Items.COBBLESTONE) != 27) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 27");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_noway", timeoutTicks = 100)
    public static void gadgetBreakWithoutWayUpIsRefused(GameTestHelper h) {
        ServerPlayer sp = player(h, "gnoway", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sealedRegion(h, Blocks.STONE);
        int oldMax = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS);
        boolean oldDig = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_DIGGING);
        try {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS.set(1);
            ItemStack a = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(7, 5, 7), new BlockPos(9, 7, 9), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
            if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, a) || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Tunnel laenger als tunnelMaxBlocks wurde angenommen (Order " + com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) + ")");
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS.set(oldMax);
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_DIGGING.set(false);
            ItemStack b = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(7, 5, 7), new BlockPos(9, 7, 9), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
            if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, b) || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Auftrag ohne Weg wurde bei ausgeschaltetem Tunnelgraben angenommen");
        } finally {
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_MAX_BLOCKS.set(oldMax);
            com.sqc.nuclearsq.NuclearConfig.NANOBOT_TUNNEL_DIGGING.set(oldDig);
        }
        sealedRegion(h, Blocks.BEDROCK);
        ItemStack c = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(7, 5, 7), new BlockPos(9, 7, 9), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        if (com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, c) || com.sqc.nuclearsq.nanobot.NanoGadget.order(sp) != null) throw new GameTestAssertException("Bereich in Bedrock wurde angenommen (kein Weg moeglich)");
        for (int x = 7; x <= 9; x++) for (int y = 5; y <= 7; y++) for (int z = 7; z <= 9; z++) if (!h.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) throw new GameTestAssertException("bei abgelehntem Auftrag wurde abgebaut: " + new BlockPos(x, y, z));
        gadgetDone(sp);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_gadget_bottomup", timeoutTicks = 4000)
    public static void gadgetBuildGoesBottomUpInEveryColumn(GameTestHelper h) {
        ServerPlayer sp = player(h, "gbottomup", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.STONE_BRICKS, 64));
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BUILD, new BlockPos(6, 4, 6), new BlockPos(8, 6, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        BlockPos prev = null;
        for (BlockPos q : o.ops()) {
            if (prev != null && q.getY() < prev.getY()) throw new GameTestAssertException("Bauliste nicht von unten nach oben: " + prev + " vor " + q);
            prev = q;
        }
        watchColumns(h, 6, 8, 4, 6, 6, 8, false);
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++) for (int y = 4; y <= 6; y++) for (int z = 6; z <= 8; z++) if (!h.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE_BRICKS)) throw new GameTestAssertException("noch nicht gebaut: " + new BlockPos(x, y, z));
            if (!swarmIdle(sp)) throw new GameTestAssertException("Schwarm laeuft noch");
            gadgetDone(sp);
        });
    }

    private static void plainTree(GameTestHelper h, int x, int z) {
        h.setBlock(new BlockPos(x, 2, z), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG.defaultBlockState());
        h.setBlock(new BlockPos(x - 2, 8, z), Blocks.OAK_LEAVES.defaultBlockState());
        h.setBlock(new BlockPos(x + 2, 7, z), Blocks.OAK_LEAVES.defaultBlockState());
    }

    @GameTest(template = FLOOR, batch = "nanobots_tree_group", timeoutTicks = 5000)
    public static void oneBotPerTree(GameTestHelper h) {
        ServerPlayer sp = player(h, "treegroup", true);
        plainTree(h, 4, 4);
        plainTree(h, 10, 10);
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        fast(can, "count_2");
        sp.getInventory().add(can);
        NanoSwarm swarm = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        boolean[] both = {false};
        String[] bad = {null};
        h.onEachTick(() -> {
            if (swarm.isEnded()) return;
            swarm.tick(sp);
            int a = 0, b = 0;
            for (BlockPos t : swarm.activeTargets()) {
                BlockPos rel = t.subtract(h.absolutePos(BlockPos.ZERO));
                if (Math.abs(rel.getX() - 4) <= 3 && Math.abs(rel.getZ() - 4) <= 3) a++;
                if (Math.abs(rel.getX() - 10) <= 3 && Math.abs(rel.getZ() - 10) <= 3) b++;
            }
            if ((a > 1 || b > 1) && bad[0] == null) bad[0] = "zwei Bots am selben Baum (Baum 1: " + a + ", Baum 2: " + b + ")";
            if (a >= 1 && b >= 1) both[0] = true;
        });
        h.succeedWhen(() -> {
            if (bad[0] != null) throw new GameTestAssertException(bad[0]);
            for (int y = 3; y <= 8; y++) {
                if (h.getBlockState(new BlockPos(4, y, 4)).is(Blocks.OAK_LOG) || h.getBlockState(new BlockPos(10, y, 10)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("noch ein Stamm bei y=" + y);
            }
            if (!both[0]) throw new GameTestAssertException("die Bots haben nie an beiden Baeumen gleichzeitig gearbeitet (mehrere Bots, mehrere Baeume)");
            if (count(sp, Items.OAK_LOG) != 12) throw new GameTestAssertException("Staemme im Inventar: " + count(sp, Items.OAK_LOG) + " statt 12");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_leaf_chain", timeoutTicks = 4000)
    public static void leavesFallAsVerticalChains(GameTestHelper h) {
        ServerPlayer sp = player(h, "leafchain", true);
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 8; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG.defaultBlockState());
        java.util.List<BlockPos> adjacent = new java.util.ArrayList<>();
        for (int y = 4; y <= 8; y++) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos q = new BlockPos(6, y, 6).relative(d);
                h.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState());
                adjacent.add(q);
            }
        }
        h.setBlock(new BlockPos(6, 9, 6), Blocks.OAK_LEAVES.defaultBlockState());
        adjacent.add(new BlockPos(6, 9, 6));
        h.setBlock(new BlockPos(3, 6, 6), Blocks.OAK_LEAVES.defaultBlockState());
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        java.util.List<BlockPos> order = new java.util.ArrayList<>();
        java.util.Set<BlockPos> gone = new java.util.HashSet<>();
        h.onEachTick(() -> {
            if (!sw.isEnded()) sw.tick(sp);
            for (BlockPos q : adjacent) {
                if (!gone.contains(q) && !(h.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) {
                    gone.add(q);
                    order.add(q);
                }
            }
        });
        h.succeedWhen(() -> {
            for (int y = 3; y <= 8; y++) if (h.getBlockState(new BlockPos(6, y, 6)).is(Blocks.OAK_LOG)) throw new GameTestAssertException("noch ein Stamm bei y=" + y + " (Blaetter weg: " + gone.size() + " von " + adjacent.size() + ")");
            if (gone.size() < adjacent.size()) throw new GameTestAssertException("noch nicht fertig: " + gone.size() + " von " + adjacent.size() + " Blaettern weg");
            if (order.get(0).getY() != 4) throw new GameTestAssertException("das erste Blatt ist nicht das tiefste: " + order.get(0));
            int changes = 0;
            for (int i = 1; i < order.size(); i++) {
                BlockPos a = order.get(i - 1), b = order.get(i);
                boolean same = a.getX() == b.getX() && a.getZ() == b.getZ();
                if (same && b.getY() < a.getY()) throw new GameTestAssertException("in einer Saeule geht es abwaerts: " + a + " -> " + b);
                if (!same) changes++;
            }
            if (changes > 5) throw new GameTestAssertException("Blaetter fallen nicht als Ketten: " + changes + " Saeulenwechsel (hoechstens 5), Reihenfolge " + order);
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_bot_timing", timeoutTicks = 4000)
    public static void botsBreakIndependentlyOfEachOther(GameTestHelper h) {
        ServerPlayer sp = player(h, "gtiming", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        for (int x = 6; x <= 8; x++) for (int y = 4; y <= 5; y++) for (int z = 6; z <= 8; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(8, 5, 8), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        tickSwarm(h, sp);
        h.succeedWhen(() -> {
            if (o.done() != 18 || !swarmIdle(sp)) throw new GameTestAssertException("noch nicht fertig: " + o.done() + " von 18");
            java.util.Set<Long> ticks = new java.util.HashSet<>();
            for (int x = 6; x <= 8; x++) for (int y = 4; y <= 5; y++) for (int z = 6; z <= 8; z++) ticks.add(o.brokenAt(h.absolutePos(new BlockPos(x, y, z))));
            if (ticks.contains(-1L)) throw new GameTestAssertException("ein Block wurde nicht vom Schwarm gebrochen");
            if (ticks.size() < 12) throw new GameTestAssertException("Brueche liegen auf nur " + ticks.size() + " verschiedenen Ticks (18 Bloecke, 3 Bots; mindestens 12 erwartet, gleicher Takt waeren 6): " + ticks);
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_fast_start", timeoutTicks = 600)
    public static void swarmStartsWorkingWithinThreeSeconds(GameTestHelper h) {
        ServerPlayer sp = player(h, "faststart", true);
        h.setBlock(new BlockPos(10, 2, 2), Blocks.DIRT.defaultBlockState());
        for (int y = 3; y <= 6; y++) h.setBlock(new BlockPos(10, y, 2), Blocks.OAK_LOG.defaultBlockState());
        h.setBlock(new BlockPos(8, 6, 2), Blocks.OAK_LEAVES.defaultBlockState());
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        int[] work = {-1}, firstBreak = {-1};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            if (work[0] < 0 && sw.phase() == NanoSwarm.Phase.WORK) work[0] = sw.age();
            if (firstBreak[0] < 0) {
                for (int y = 3; y <= 6; y++) if (!h.getBlockState(new BlockPos(10, y, 2)).is(Blocks.OAK_LOG)) firstBreak[0] = sw.age();
            }
        });
        h.succeedWhen(() -> {
            if (work[0] < 0) throw new GameTestAssertException("noch keine Arbeitsphase (Phase " + sw.phase() + ", Tick " + sw.age() + ")");
            if (work[0] > 60) throw new GameTestAssertException("erste Arbeitsphase erst nach " + work[0] + " Ticks (hoechstens 60)");
            if (firstBreak[0] < 0) throw new GameTestAssertException("noch kein Stamm gebrochen (Tick " + sw.age() + ", Arbeitsphase ab " + work[0] + ")");
            if (firstBreak[0] > 180) throw new GameTestAssertException("erster Stamm erst nach " + firstBreak[0] + " Ticks gebrochen (hoechstens 180)");
            done(sp);
        });
    }

    private static int[] watchSpacing(GameTestHelper h, ServerPlayer sp) {
        int[] maxParallel = {0};
        h.onEachTick(() -> {
            NanoSwarm sw = NanoSwarms.get(sp);
            if (sw == null || sw.isEnded()) return;
            sw.tick(sp);
            var t = new java.util.ArrayList<>(sw.activeTargets());
            maxParallel[0] = Math.max(maxParallel[0], t.size());
            for (int i = 0; i < t.size(); i++) {
                for (int j = i + 1; j < t.size(); j++) {
                    BlockPos a = t.get(i), b = t.get(j);
                    int d = Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
                    if (d < 2) throw new GameTestAssertException("zwei Bots an benachbarten Bloecken (Abstand " + d + "): " + a + " und " + b);
                }
            }
        });
        return maxParallel;
    }

    @GameTest(template = FLOOR, batch = "nanobots_claim_spacing", timeoutTicks = 5000)
    public static void botsNeverShareASpot(GameTestHelper h) {
        ServerPlayer sp = player(h, "spacing", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE.defaultBlockState());
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(10, 4, 10), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        int[] par = watchSpacing(h, sp);
        h.succeedWhen(() -> {
            if (o.done() != 25 || !swarmIdle(sp)) throw new GameTestAssertException("noch nicht fertig: " + o.done() + " von 25");
            if (par[0] < 2) throw new GameTestAssertException("nie zwei Bots gleichzeitig (hoechstens " + par[0] + ")");
            if (o.logged() != 25) throw new GameTestAssertException("Protokoll: " + o.logged() + " Eintraege statt 25 (ein Block doppelt oder gar nicht gebrochen)");
            if (count(sp, Items.COBBLESTONE) != 25) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 25");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_claim_wait", timeoutTicks = 3000)
    public static void botWaitsWhenOnlyNeighboursAreLeft(GameTestHelper h) {
        ServerPlayer sp = player(h, "spacingwait", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        for (int x = 6; x <= 8; x++) h.setBlock(new BlockPos(x, 4, 6), Blocks.STONE.defaultBlockState());
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(6, 4, 6), new BlockPos(8, 4, 6), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        fast(can, "count_2");
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        int[] par = watchSpacing(h, sp);
        h.succeedWhen(() -> {
            if (o.done() != 3 || !swarmIdle(sp)) throw new GameTestAssertException("noch nicht fertig: " + o.done() + " von 3 (hoechstens gleichzeitig " + par[0] + ")");
            if (par[0] > 2) throw new GameTestAssertException("drei Bots gleichzeitig an drei Nachbarbloecken (" + par[0] + ")");
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_claim_stone", timeoutTicks = 6000)
    public static void stoneMassBotsKeepApart(GameTestHelper h) {
        ServerPlayer sp = player(h, "spacingstone", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        java.util.List<BlockPos> mass = new java.util.ArrayList<>();
        for (int x = 6; x <= 8; x++) for (int y = 4; y <= 6; y++) for (int z = 3; z <= 5; z++) mass.add(new BlockPos(x, y, z));
        for (BlockPos q : mass) h.setBlock(q, Blocks.STONE.defaultBlockState());
        threeBotCanister(sp, NanoTask.MINE_STONE);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.MINE_STONE);
        int[] par = {0};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            var t = new java.util.ArrayList<>(sw.activeTargets());
            par[0] = Math.max(par[0], t.size());
            for (int i = 0; i < t.size(); i++) {
                for (int j = i + 1; j < t.size(); j++) {
                    BlockPos a = t.get(i), b = t.get(j);
                    if (Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) < 2)
                        throw new GameTestAssertException("zwei Bots an benachbarten Bloecken: " + a + " und " + b);
                }
            }
        });
        h.succeedWhen(() -> {
            for (BlockPos q : mass) if (!h.getBlockState(q).isAir()) throw new GameTestAssertException("noch ein Stein bei " + q);
            if (par[0] < 2) throw new GameTestAssertException("nie zwei Bots gleichzeitig (hoechstens " + par[0] + ")");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_36_bots", timeoutTicks = 6000)
    public static void thirtySixBotsWorkEachBlockOnce(GameTestHelper h) {
        ServerPlayer sp = player(h, "bots36", true);
        sp.setPos(h.absoluteVec(new Vec3(2.5, 4, 2.5)));
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        for (int x = 5; x <= 14; x++) for (int z = 5; z <= 14; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE.defaultBlockState());
        ItemStack can = gadget(h, sp, NanoTask.GADGET_BREAK, new BlockPos(5, 4, 5), new BlockPos(14, 4, 14), com.sqc.nuclearsq.nanobot.GadgetData.CUBOID, com.sqc.nuclearsq.nanobot.GadgetData.INVENTORY);
        net.minecraft.core.NonNullList<ItemStack> ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("speed_5").get()));
        ups.set(1, new ItemStack(NanobotRegistry.UPGRADE_ITEMS.get("count_5").get()));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        if (!com.sqc.nuclearsq.nanobot.NanoGadget.confirm(sp, can)) throw new GameTestAssertException("Auftrag nicht bestaetigt");
        var o = com.sqc.nuclearsq.nanobot.NanoGadget.order(sp);
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw == null || sw.botCount() != 10) throw new GameTestAssertException("Schwarm mit " + (sw == null ? "keinem" : sw.botCount()) + " Bots statt 10");
        int budget = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_MAX_BREAKS_PER_TICK);
        int[] last = {0}, maxPerTick = {0}, par = {0};
        long[] maxNanos = {0};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            long t0 = System.nanoTime();
            sw.tick(sp);
            maxNanos[0] = Math.max(maxNanos[0], System.nanoTime() - t0);
            int now = o.logged();
            maxPerTick[0] = Math.max(maxPerTick[0], now - last[0]);
            last[0] = now;
            var t = new java.util.ArrayList<>(sw.activeTargets());
            par[0] = Math.max(par[0], t.size());
            if (new java.util.HashSet<>(t).size() != t.size()) throw new GameTestAssertException("zwei Bots am selben Block: " + t);
            for (int i = 0; i < t.size(); i++) {
                for (int j = i + 1; j < t.size(); j++) {
                    BlockPos a = t.get(i), b = t.get(j);
                    if (Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) < 2) throw new GameTestAssertException("Doppelanflug: " + a + " und " + b);
                }
            }
        });
        h.succeedWhen(() -> {
            if (o.done() != 100 || !swarmIdle(sp)) throw new GameTestAssertException("noch nicht fertig: " + o.done() + " von 100 (parallel hoechstens " + par[0] + ")");
            if (o.logged() != 100) throw new GameTestAssertException("Protokoll: " + o.logged() + " Eintraege statt 100 (Block doppelt oder gar nicht)");
            if (count(sp, Items.COBBLESTONE) != 100) throw new GameTestAssertException("Bruchstein: " + count(sp, Items.COBBLESTONE) + " statt 100");
            if (maxPerTick[0] > budget) throw new GameTestAssertException("Bruchbudget verletzt: " + maxPerTick[0] + " Bloecke in einem Tick (Budget " + budget + ")");
            if (par[0] < 6) throw new GameTestAssertException("mit 10 Bots nur " + par[0] + " gleichzeitig");
            if (maxNanos[0] > 250_000_000L) throw new GameTestAssertException("ein Schwarm-Tick dauerte " + maxNanos[0] / 1_000_000 + " ms (Grenze 250 ms)");
            com.sqc.nuclearsq.NuclearSQ.LOGGER.info("[nanobots] 10-Bots-Test: groesster Schwarm-Tick {} ms, hoechstens {} Bloecke je Tick (Budget {}), parallel hoechstens {}", maxNanos[0] / 1_000_000.0, maxPerTick[0], budget, par[0]);
            gadgetDone(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_bot_texture", timeoutTicks = 40)
    public static void botTextureLayoutIsClean(GameTestHelper h) {
        java.awt.image.BufferedImage[] atlas = new java.awt.image.BufferedImage[2];
        String[] looks = {"dark", "chrome"};
        for (int i = 0; i < 2; i++) {
            try (var in = NanoSwarm.class.getResourceAsStream("/assets/nuclearsq/textures/entity/nano_bot_" + looks[i] + ".png")) {
                if (in == null) throw new GameTestAssertException("Atlas nano_bot_" + looks[i] + ".png fehlt");
                atlas[i] = javax.imageio.ImageIO.read(in);
            } catch (java.io.IOException e) {
                throw new GameTestAssertException("Atlas nicht lesbar: " + e.getMessage());
            }
            if (atlas[i].getWidth() != com.sqc.nuclearsq.nanobot.client.BotUv.W || atlas[i].getHeight() != com.sqc.nuclearsq.nanobot.client.BotUv.H)
                throw new GameTestAssertException("Atlas " + looks[i] + " hat " + atlas[i].getWidth() + " x " + atlas[i].getHeight() + " statt " + com.sqc.nuclearsq.nanobot.client.BotUv.W + " x " + com.sqc.nuclearsq.nanobot.client.BotUv.H);
        }
        boolean[][] used = new boolean[com.sqc.nuclearsq.nanobot.client.BotUv.W][com.sqc.nuclearsq.nanobot.client.BotUv.H];
        for (var kind : com.sqc.nuclearsq.nanobot.client.BotUv.Kind.values()) {
            for (int f = 0; f < 6; f++) {
                float[] r = kind.rect(f);
                int x0 = Math.round(r[0] * com.sqc.nuclearsq.nanobot.client.BotUv.W), y0 = Math.round(r[1] * com.sqc.nuclearsq.nanobot.client.BotUv.H);
                int x1 = Math.round(r[2] * com.sqc.nuclearsq.nanobot.client.BotUv.W), y1 = Math.round(r[3] * com.sqc.nuclearsq.nanobot.client.BotUv.H);
                if (x0 < 0 || y0 < 0 || x1 > com.sqc.nuclearsq.nanobot.client.BotUv.W || y1 > com.sqc.nuclearsq.nanobot.client.BotUv.H || x1 <= x0 || y1 <= y0)
                    throw new GameTestAssertException(kind + " Flaeche " + f + " liegt ausserhalb des Atlas");
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        if (used[x][y]) throw new GameTestAssertException(kind + " Flaeche " + f + " ueberlappt bei " + x + "," + y);
                        used[x][y] = true;
                        for (int i = 0; i < 2; i++) {
                            if ((atlas[i].getRGB(x, y) >>> 24) != 255) throw new GameTestAssertException(kind + " Flaeche " + f + " (" + looks[i] + ") hat ein leeres Pixel bei " + x + "," + y);
                        }
                    }
                }
            }
        }
        float[] pose = new float[com.sqc.nuclearsq.nanobot.client.SpiderModel.P_SIZE];
        pose[com.sqc.nuclearsq.nanobot.client.SpiderModel.P_BETA] = 0.8f;
        float scale = com.sqc.nuclearsq.nanobot.client.SpiderModel.scale();
        int[] seen = {0};
        com.sqc.nuclearsq.nanobot.client.SpiderModel.parts(pose, 0, (idx, mat, cx, cy, cz, hx, hy, hz, yaw, pitch) -> {
            var k = com.sqc.nuclearsq.nanobot.client.SpiderModel.kindOf(idx);
            float[] edge = {2 * hx / scale, 2 * hy / scale, 2 * hz / scale};
            int[] texels = {k.dx, k.dy, k.dz};
            for (int a = 0; a < 3; a++) {
                float density = edge[a] / texels[a];
                if (density < 0.0014f || density > 0.0026f)
                    throw new GameTestAssertException("Teil " + idx + " (" + k + ") Achse " + a + ": Modellkante " + edge[a] + " bei " + texels[a] + " Texeln = " + density + " je Texel (erwartet etwa 0,002)");
            }
            seen[0]++;
        });
        if (seen[0] != com.sqc.nuclearsq.nanobot.client.SpiderModel.PARTS) throw new GameTestAssertException("Teile: " + seen[0] + " statt " + com.sqc.nuclearsq.nanobot.client.SpiderModel.PARTS);
        for (int i = 32; i < 40; i++) if (com.sqc.nuclearsq.nanobot.client.SpiderModel.matOf(i) != com.sqc.nuclearsq.nanobot.client.SpiderModel.Mat.GLOW) throw new GameTestAssertException("Knieringe leuchten nicht (Teil " + i + ")");
        h.succeed();
    }

    private static net.minecraft.nbt.Tag saveStack(GameTestHelper h, ItemStack st) {
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, h.getLevel().registryAccess());
        return ItemStack.CODEC.encodeStart(ops, st).getOrThrow(e -> new GameTestAssertException("Kanister nicht speicherbar: " + e));
    }

    private static ItemStack loadStack(GameTestHelper h, net.minecraft.nbt.Tag tag) {
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, h.getLevel().registryAccess());
        return ItemStack.CODEC.parse(ops, tag).getOrThrow(e -> new GameTestAssertException("Kanister nicht ladbar: " + e));
    }

    @GameTest(template = FLOOR, batch = "nanobots_codec", timeoutTicks = 40)
    public static void canisterSurvivesSaveAndLoadAtTheLimits(GameTestHelper h) {
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), NanoTask.values().length - 1);
        can.set(NanobotRegistry.RADIUS.get(), NanoSettings.HARD_MAX_RADIUS);
        var ups = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        ups.set(0, new ItemStack(up("speed_5")));
        ups.set(1, new ItemStack(up("count_5")));
        ups.set(2, new ItemStack(up("reach_5")));
        can.set(NanobotRegistry.UPGRADES.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(ups));
        var gd = new com.sqc.nuclearsq.nanobot.GadgetData(com.sqc.nuclearsq.nanobot.GadgetData.SHAPES - 1, 1, java.util.Optional.of(new BlockPos(-30000000, -64, 30000000)), java.util.Optional.of(BlockPos.ZERO), true,
                com.sqc.nuclearsq.nanobot.GadgetData.MAX_RADIUS, 2, com.sqc.nuclearsq.nanobot.GadgetData.MAX_HEIGHT,
                new com.sqc.nuclearsq.nanobot.GadgetData.Paste(3, true, true, 16, -16, 16));
        can.set(NanobotRegistry.GADGET.get(), gd);
        ItemStack back = loadStack(h, saveStack(h, can));
        if (back.get(NanobotRegistry.RADIUS.get()) != NanoSettings.HARD_MAX_RADIUS) throw new GameTestAssertException("Radius: " + back.get(NanobotRegistry.RADIUS.get()));
        if (!gd.equals(back.get(NanobotRegistry.GADGET.get()))) throw new GameTestAssertException("Gadget-Daten nach dem Laden anders: " + back.get(NanobotRegistry.GADGET.get()));
        NanoSettings s = NanoSettings.of(back);
        if (s.speedFactor() != 7.0 || s.bots() != 10) throw new GameTestAssertException("Tempo/Anzahl nach dem Laden: " + s);
        if (NanoCanisterItem.task(back) != NanoTask.values()[NanoTask.values().length - 1]) throw new GameTestAssertException("Aufgabe nach dem Laden anders");
        net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        com.sqc.nuclearsq.nanobot.GadgetData.STREAM_CODEC.encode(buf, gd);
        if (!gd.equals(com.sqc.nuclearsq.nanobot.GadgetData.STREAM_CODEC.decode(buf))) throw new GameTestAssertException("Gadget-StreamCodec liefert andere Daten");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_codec_clamp", timeoutTicks = 40)
    public static void tooBigValuesAreClampedWhenLoading(GameTestHelper h) {
        ItemStack can = new ItemStack(NanobotRegistry.CANISTER.get());
        can.set(NanobotRegistry.TASK.get(), 1);
        can.set(NanobotRegistry.RADIUS.get(), 24);
        can.set(NanobotRegistry.GADGET.get(), com.sqc.nuclearsq.nanobot.GadgetData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) saveStack(h, can);
        net.minecraft.nbt.CompoundTag comps = tag.getCompound("components");
        for (int value : new int[] {104, 999}) {
            comps.putInt("nuclearsq:nano_radius", value);
            ItemStack back = loadStack(h, tag);
            int expected = Math.min(value, NanoSettings.HARD_MAX_RADIUS);
            if (back.get(NanobotRegistry.RADIUS.get()) != expected) throw new GameTestAssertException("Radius " + value + " wurde zu " + back.get(NanobotRegistry.RADIUS.get()) + " statt " + expected);
        }
        comps.putInt("nuclearsq:nano_radius", -5);
        if (loadStack(h, tag).get(NanobotRegistry.RADIUS.get()) != 0) throw new GameTestAssertException("negativer Radius nicht auf 0 geklemmt");
        comps.putInt("nuclearsq:nano_task", 99);
        if (loadStack(h, tag).get(NanobotRegistry.TASK.get()) != NanoTask.values().length - 1) throw new GameTestAssertException("Aufgabe 99 nicht auf die letzte geklemmt");
        net.minecraft.nbt.CompoundTag g = comps.getCompound("nuclearsq:nano_gadget");
        g.putInt("shape", 99);
        g.putInt("source", 7);
        g.putInt("radius", 500);
        g.putInt("axis", -4);
        g.putInt("height", 0);
        net.minecraft.nbt.CompoundTag pst = new net.minecraft.nbt.CompoundTag();
        pst.putInt("rot", 9);
        pst.putInt("ox", 99);
        pst.putInt("oy", -99);
        g.put("paste", pst);
        comps.put("nuclearsq:nano_gadget", g);
        var gd = loadStack(h, tag).get(NanobotRegistry.GADGET.get());
        if (gd == null || gd.shape() != com.sqc.nuclearsq.nanobot.GadgetData.SHAPES - 1 || gd.source() != 1 || gd.radius() != com.sqc.nuclearsq.nanobot.GadgetData.MAX_RADIUS || gd.axis() != 0 || gd.height() != 1
                || gd.paste().rot() != 3 || gd.paste().ox() != com.sqc.nuclearsq.nanobot.GadgetData.MAX_OFFSET || gd.paste().oy() != -com.sqc.nuclearsq.nanobot.GadgetData.MAX_OFFSET)
            throw new GameTestAssertException("Gadget-Werte nicht geklemmt: " + gd);
        comps.putInt("nuclearsq:nano_radius", 104);
        if (NanoSettings.of(loadStack(h, tag)).radius() > NanoSettings.HARD_MAX_RADIUS) throw new GameTestAssertException("Radius ueber der Obergrenze");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "nanobots_return", timeoutTicks = 1500)
    public static void recallLetsBotsWalkInOneByOne(GameTestHelper h) {
        ServerPlayer sp = player(h, "return", true);
        threeBotCanister(sp, NanoTask.FELL_TREE);
        NanoSwarm sw = NanoSwarms.start(sp, NanoTask.FELL_TREE);
        if (sw.botCount() != 4) throw new GameTestAssertException("Schwarm mit " + sw.botCount() + " Bots statt 4");
        int stagger = com.sqc.nuclearsq.NuclearConfig.value(com.sqc.nuclearsq.NuclearConfig.NANOBOT_ENTRY_STAGGER);
        int[] sinceRecall = {-1};
        h.onEachTick(() -> {
            if (sw.isEnded()) return;
            sw.tick(sp);
            if (sinceRecall[0] < 0 && sw.age() >= 5) {
                sw.recall();
                sinceRecall[0] = 0;
            } else if (sinceRecall[0] >= 0) {
                sinceRecall[0]++;
            }
        });
        h.succeedWhen(() -> {
            if (!sw.isEnded()) {
                if (sinceRecall[0] >= 0 && sw.phase() != NanoSwarm.Phase.LEAVE) throw new GameTestAssertException("nach dem Rueckruf nicht in LEAVE: " + sw.phase());
                throw new GameTestAssertException("Schwarm noch nicht beendet (Tick seit Rueckruf " + sinceRecall[0] + ")");
            }
            int min = 20 + com.sqc.nuclearsq.nanobot.BotEntry.TOTAL + stagger * 4;
            if (sinceRecall[0] < min) throw new GameTestAssertException("Schwarm endete " + sinceRecall[0] + " Ticks nach dem Rueckruf, mindestens " + min + " erwartet (Einlaufen, gestaffelt)");
            var after = NanoSwarmPayload.goneAfterEntry(UUID.randomUUID());
            if (after.count() != 0 || after.state() != 5 || NanoSwarmPayload.gone(UUID.randomUUID()).state() != 0) throw new GameTestAssertException("Abschlusspakete: state " + after.state());
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            NanoSwarmPayload.STREAM_CODEC.encode(buf, after);
            if (NanoSwarmPayload.STREAM_CODEC.decode(buf).state() != 5) throw new GameTestAssertException("Abschlusspaket verliert state 5");
            NanoSwarm again = NanoSwarms.start(sp, NanoTask.FELL_TREE);
            NanoSwarms.stop(sp);
            if (!again.isEnded()) throw new GameTestAssertException("Stopp beendet den Schwarm nicht sofort");
            done(sp);
        });
    }

    @GameTest(template = FLOOR, batch = "nanobots_return_path", timeoutTicks = 20)
    public static void botEntryPathIsContinuous(GameTestHelper h) {
        Vec3 start = new Vec3(1.0, 0.0, 0.5), feet = new Vec3(0, 0, 0);
        double len = Math.sqrt(1.0 + 0.25);
        double dx = 1.0 / len, dz = 0.5 / len;
        Vec3 arm = com.sqc.nuclearsq.nanobot.BotEntry.armPoint(feet, 30f, true);
        Vec3 prev = com.sqc.nuclearsq.nanobot.BotEntry.position(0, start, feet, dx, dz, arm);
        if (prev.distanceTo(start) > 1e-6) throw new GameTestAssertException("Weg beginnt nicht am Startpunkt: " + prev);
        float lastScale = 1f;
        for (int t = 1; t <= com.sqc.nuclearsq.nanobot.BotEntry.TOTAL; t++) {
            Vec3 p = com.sqc.nuclearsq.nanobot.BotEntry.position(t, start, feet, dx, dz, arm);
            if (p.distanceTo(prev) > 0.2) throw new GameTestAssertException("Sprung im Tick " + t + ": " + p.distanceTo(prev) + " Block");
            if (t >= com.sqc.nuclearsq.nanobot.BotEntry.APPROACH && t <= com.sqc.nuclearsq.nanobot.BotEntry.CLIMB && Math.hypot(p.x - feet.x, p.z - feet.z) < 0.3)
                throw new GameTestAssertException("Bot im Tick " + t + " innerhalb der Spielerhuelle (Abstand " + Math.hypot(p.x - feet.x, p.z - feet.z) + ")");
            float sc = com.sqc.nuclearsq.nanobot.BotEntry.scale(t);
            if (sc > lastScale + 1e-6f) throw new GameTestAssertException("Groesse waechst im Tick " + t);
            lastScale = sc;
            prev = p;
        }
        if (prev.distanceTo(arm) > 1e-6) throw new GameTestAssertException("Weg endet nicht im Arm: " + prev + " statt " + arm);
        if (com.sqc.nuclearsq.nanobot.BotEntry.scale(com.sqc.nuclearsq.nanobot.BotEntry.CLIMB) != 1f || com.sqc.nuclearsq.nanobot.BotEntry.scale(com.sqc.nuclearsq.nanobot.BotEntry.TOTAL) != 0f)
            throw new GameTestAssertException("Schrumpfen: Groesse am Ende des Kletterns " + com.sqc.nuclearsq.nanobot.BotEntry.scale(com.sqc.nuclearsq.nanobot.BotEntry.CLIMB) + ", am Ende " + com.sqc.nuclearsq.nanobot.BotEntry.scale(com.sqc.nuclearsq.nanobot.BotEntry.TOTAL));
        h.succeed();
    }
}
