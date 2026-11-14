package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentLightTests {
    private ContainmentLightTests() {}

    @GameTest(template = "floor", batch = "containment_light", timeoutTicks = 800)
    public static void containmentBlocksSkylight(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.CONTAINMENT.id), h.absolutePos(new BlockPos(34, 140, 4)), Direction.SOUTH);
        for (BlockPos p : pl.footprint()) h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(blocked.size() + " Felder der Huelle belegt, z. B. " + blocked.get(0));
        MachineKits.place(h.getLevel(), pl);
        BlockPos outside = BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), 15, 34, 15);
        BlockPos[] inside = {
                BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), 15, 10, 15),
                BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), 15, 30, 15)};
        h.succeedWhen(() -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || !be.isFormed()) {
                throw new GameTestAssertException("Huelle nicht aufgebaut");
            }
            int sky = h.getLevel().getBrightness(LightLayer.SKY, outside);
            if (sky < 15) throw new GameTestAssertException("warte: Himmelslicht ausserhalb " + sky);
            for (BlockPos p : inside) {
                int in = h.getLevel().getBrightness(LightLayer.SKY, p);
                if (in != 0) throw new GameTestAssertException("Himmelslicht in der Huelle bei " + p + ": " + in + " (erwartet 0)");
            }
        });
    }
    @GameTest(template = "floor", batch = "containment_mobs", timeoutTicks = 800)
    public static void noMobsInsideContainment(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.CONTAINMENT.id), h.absolutePos(new BlockPos(34, 140, 4)), Direction.SOUTH);
        for (BlockPos p : pl.footprint()) h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
        MachineKits.place(h.getLevel(), pl);
        h.succeedWhen(() -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || !be.isFormed()) throw new GameTestAssertException("Huelle nicht aufgebaut");
            var probe = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
            probe.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), 15, 10, 15)));
            var ev = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(probe, h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, null));
            if (ev.getResult() != net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL) throw new GameTestAssertException("warte: Spawn in der Huelle erlaubt");
            probe.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(BigMachine.CONTAINMENT.structure.world(pl.anchor(), pl.rot(), 0, 0, 0)));
            ev = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(probe, h.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, null));
            if (ev.getResult() != net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.DEFAULT) throw new GameTestAssertException("Spawn in der Ecke ausserhalb der Huelle gesperrt");
        });
    }
}
