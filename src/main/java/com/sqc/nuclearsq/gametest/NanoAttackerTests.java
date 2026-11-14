package com.sqc.nuclearsq.gametest;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.NanoSwarm;
import com.sqc.nuclearsq.nanobot.NanoSwarms;
import com.sqc.nuclearsq.nanobot.NanoTask;
import com.sqc.nuclearsq.nanobot.NanobotRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class NanoAttackerTests {
    private NanoAttackerTests() {}

    private static final String FLOOR = "floor";

    private static ServerPlayer player(GameTestHelper h, String name) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("nanoatk_" + name).getBytes()), "na_" + name));
        NanoSwarms.stop(sp);
        sp.getInventory().clearContent();
        sp.setPos(h.absoluteVec(new Vec3(2.5, 2, 2.5)));
        ItemStack reactor = new ItemStack(com.sqc.nuclearsq.exo.ExoRegistry.CORE.get());
        reactor.set(com.sqc.nuclearsq.registry.ModDataComponents.STORED_ENERGY.get(), 100_000);
        sp.getInventory().add(reactor);
        return sp;
    }

    private static NanoSwarm run(GameTestHelper h, ServerPlayer sp, NanoTask task) {
        NanoSwarm sw = NanoSwarms.start(sp, task);
        h.onEachTick(() -> {
            if (!sw.isEnded()) sw.tick(sp);
        });
        return sw;
    }

    private static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    @GameTest(template = FLOOR, batch = "nanoatk_claim", timeoutTicks = 300)
    public static void claimsStopTheSwarm(GameTestHelper h) {
        ServerPlayer sp = player(h, "claim");
        sp.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        BlockPos ore = new BlockPos(8, 2, 4);
        h.setBlock(ore, Blocks.IRON_ORE.defaultBlockState());
        Consumer<BlockEvent.BreakEvent> claim = ev -> {
            if (ev.getPlayer() == sp) ev.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, claim);
        run(h, sp, NanoTask.MINE_ORE);
        h.runAfterDelay(200, () -> {
            NeoForge.EVENT_BUS.unregister(claim);
            boolean mined = !h.getBlockState(ore).is(Blocks.IRON_ORE);
            int got = count(sp, Items.RAW_IRON);
            NanoSwarms.stop(sp);
            sp.getInventory().clearContent();
            if (mined || got != 0) throw new GameTestAssertException("Schwarm baut im geschuetzten Gebiet ab (Erz weg: " + mined + ", Rohreisen: " + got + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "nanoatk_items", timeoutTicks = 300)
    public static void swarmDoesNotStealItems(GameTestHelper h) {
        ServerPlayer sp = player(h, "items");
        Vec3 a = h.absoluteVec(new Vec3(9.5, 2.2, 2.5)), b = h.absoluteVec(new Vec3(9.5, 2.2, 6.5));
        ItemEntity foreign = new ItemEntity(h.getLevel(), a.x, a.y, a.z, new ItemStack(Items.DIAMOND, 3));
        foreign.setTarget(UUID.nameUUIDFromBytes("other".getBytes()));
        foreign.setNoPickUpDelay();
        ItemEntity claimed = new ItemEntity(h.getLevel(), b.x, b.y, b.z, new ItemStack(Items.EMERALD, 4));
        claimed.setNoPickUpDelay();
        h.getLevel().addFreshEntity(foreign);
        h.getLevel().addFreshEntity(claimed);
        Consumer<ItemEntityPickupEvent.Pre> claim = ev -> {
            if (ev.getItemEntity() == claimed) ev.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, claim);
        run(h, sp, NanoTask.COLLECT_ITEMS);
        h.runAfterDelay(200, () -> {
            NeoForge.EVENT_BUS.unregister(claim);
            int dia = count(sp, Items.DIAMOND), em = count(sp, Items.EMERALD);
            boolean alive = foreign.isAlive() && claimed.isAlive();
            NanoSwarms.stop(sp);
            sp.getInventory().clearContent();
            foreign.discard();
            claimed.discard();
            if (dia != 0 || em != 0 || !alive) throw new GameTestAssertException("Schwarm hat fremde Items genommen (Diamanten " + dia + ", Smaragde " + em + ", liegen noch: " + alive + ")");
            h.succeed();
        });
    }
}
