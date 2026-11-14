package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class RtgCapsuleEvents {
    private RtgCapsuleEvents() {}

    private static boolean capsule(ItemStack s) {
        return s.getItem() instanceof RtgCapsuleItem;
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        if (!event.getRight().isEmpty() && (capsule(event.getLeft()) || capsule(event.getRight()))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        if (!event.getTopItem().isEmpty() && !event.getBottomItem().isEmpty() && (capsule(event.getTopItem()) || capsule(event.getBottomItem()))) {
            event.setCanceled(true);
        }
    }
}
