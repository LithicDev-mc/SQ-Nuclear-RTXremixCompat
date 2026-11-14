package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class KitDismantleTests {
    private static final String FLOOR = "floor";

    private KitDismantleTests() {}

    @GameTest(template = FLOOR, batch = "kit_far_part", timeoutTicks = 120)
    public static void farPartOfKitTowerDismantlesWholeMachine(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.COOLING_TOWER_LARGE.id),
                h.absolutePos(new BlockPos(11, 4, 1)), Direction.SOUTH);
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(60, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || !be.isFormed()) {
                throw new GameTestAssertException("Kuehlturm aus dem Kit ist nicht aufgebaut");
            }
            BlockPos far = null;
            int best = 0;
            for (MultiblockStructure.Cell c : pl.cells()) {
                BlockPos d = c.pos().subtract(pl.anchor());
                int dist = Math.max(Math.abs(d.getX()), Math.max(Math.abs(d.getY()), Math.abs(d.getZ())));
                if (dist > best) {
                    best = dist;
                    far = c.pos();
                }
            }
            if (best <= 20) throw new GameTestAssertException("Testannahme falsch: kein Teil weiter als 20 Bloecke (" + best + ")");
            if (MachineKits.owner(h.getLevel(), far) == null) throw new GameTestAssertException("Fernes Teil gehoert zu keiner Maschine");
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(far);
            if (!h.getLevel().getBlockState(pl.anchor()).isAir()) throw new GameTestAssertException("Controller steht noch");
            AABB area = new AABB(pl.anchor()).inflate(64);
            boolean kit = !h.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(KitRegistry.KITS.get(BigMachine.COOLING_TOWER_LARGE.id).get())).isEmpty();
            if (!kit) throw new GameTestAssertException("Kit faellt nicht heraus");
            long parts = h.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                    e -> !e.getItem().is(KitRegistry.KITS.get(BigMachine.COOLING_TOWER_LARGE.id).get())).size();
            if (parts > 0) throw new GameTestAssertException(parts + " Einzelteile fallen heraus");
            h.succeed();
        });
    }
}
