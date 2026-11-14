package com.sqc.nuclearsq.explosion;

import com.sqc.nuclearsq.NuclearSQ;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class DetonatorEvents {
    private DetonatorEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isSecondaryUseActive()) return;
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ReactorDetonatorBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ReactorDetonatorBlock.tickCountdowns(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        ReactorDetonatorBlock.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        ReactorDetonatorBlock.clearCountdowns();
        com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.clearDrives(false);
    }
}
