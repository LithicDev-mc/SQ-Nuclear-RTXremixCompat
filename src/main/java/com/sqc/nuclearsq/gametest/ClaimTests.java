package com.sqc.nuclearsq.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ClaimTests {
    private ClaimTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "claim_kit", timeoutTicks = 80)
    public static void foreignKitNeedsRights(GameTestHelper h) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("claim_test_kit".getBytes()), "claim_kit"));
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("battery_medium"), h.absolutePos(new BlockPos(4, 2, 3)), Direction.SOUTH);
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(25, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Kein Controller");
            be.setOwner(UUID.nameUUIDFromBytes("claim_fremder".getBytes()));
            if (MachineKits.mayDismantle(h.getLevel(), pl, sp, pl.anchor())) throw new GameTestAssertException("Schraubenschluessel zerlegt fremde Anlage ohne Rechte");
            BlockPos part = pl.cells().get(0).pos();
            BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(h.getLevel(), part, h.getLevel().getBlockState(part), sp);
            NeoForge.EVENT_BUS.post(ev);
            List<ItemEntity> kits = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(8),
                    e -> e.getItem().is(KitRegistry.KITS.get("battery_medium").get()));
            if (!kits.isEmpty() || !(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity))
                throw new GameTestAssertException("Abbau ohne Rechte zerlegte die fremde Anlage (Kits: " + kits.size() + ")");
            if (!ev.isCanceled()) throw new GameTestAssertException("Abbau ohne Rechte nicht abgebrochen");
            be.setOwner(sp.getUUID());
            if (!MachineKits.mayDismantle(h.getLevel(), pl, sp, pl.anchor())) throw new GameTestAssertException("Besitzer darf seine Anlage nicht zerlegen");
            MachineKits.dismantle(h.getLevel(), pl, false);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "claim_nuke", timeoutTicks = 200)
    public static void nukeSparesClaimedChunk(GameTestHelper h) {
        BlockPos mid = new BlockPos(7, 3, 7), probe = new BlockPos(7, 2, 7);
        BlockPos center = h.absolutePos(mid);
        h.setBlock(probe, Blocks.STONE);
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        boolean[] seen = {false};
        Consumer<ExplosionEvent.Detonate> claim = e -> {
            if (e.getLevel() != h.getLevel()) return;
            e.getAffectedBlocks().removeIf(p -> {
                boolean in = (p.getX() >> 4) == ccx && (p.getZ() >> 4) == ccz;
                seen[0] |= in;
                return in;
            });
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Detonate.class, claim);
        h.runAfterDelay(195, () -> NeoForge.EVENT_BUS.unregister(claim));
        NuclearExplosion.detonate(h.getLevel(), Vec3.atCenterOf(center), new NuclearExplosion.Blast(3, 0.01f, 5f, 8.0, 0, false));
        h.succeedWhen(() -> {
            if (NuclearExplosion.changingBlocks()) throw new GameTestAssertException("Krater laeuft noch");
            NeoForge.EVENT_BUS.unregister(claim);
            if (!seen[0]) throw new GameTestAssertException("Kein ExplosionEvent.Detonate fuer den Chunk der Mitte");
            if (!h.getBlockState(probe).is(Blocks.STONE)) throw new GameTestAssertException("Krater im geschuetzten Chunk: " + h.getBlockState(probe));
        });
    }
}
