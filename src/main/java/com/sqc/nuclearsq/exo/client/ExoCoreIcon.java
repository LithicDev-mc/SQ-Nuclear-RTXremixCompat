package com.sqc.nuclearsq.exo.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoCoreItem;
import com.sqc.nuclearsq.exo.ExoRegistry;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ExoCoreIcon {
    private ExoCoreIcon() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ExoRegistry.CORE.get(), ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "tier"),
                (stack, level, entity, seed) -> ExoCoreItem.tier(stack)));
    }
}
