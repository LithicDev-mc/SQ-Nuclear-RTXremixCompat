package com.sqc.nuclearsq.futurechest.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.futurechest.FutureChestRegistry;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class FutureChestClient {
    private FutureChestClient() {}

    public static final ModelResourceLocation BODY = model("future_chest_sqc_body"), LID = model("future_chest_sqc_lid");

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + name));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FutureChestRegistry.BLOCK_ENTITY.get(), FutureChestRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(BODY);
        event.register(LID);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(FutureChestRegistry.MENU.get(), FutureChestScreen::new);
    }
}
