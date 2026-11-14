package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.NuclearSQ;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class MachineSpawnGuard {
    private MachineSpawnGuard() {}

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (BigMachineBlockEntity.noMobsAt(event.getLevel(), event.getEntity().position())) event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
}
