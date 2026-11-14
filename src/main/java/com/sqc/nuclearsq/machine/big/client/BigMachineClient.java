package com.sqc.nuclearsq.machine.big.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class BigMachineClient {
    private BigMachineClient() {}

    public static final ModelResourceLocation CLAMP_FRAME = model("rod_clamp_frame_sqc"), CLAMP_JAW = model("rod_clamp_jaw_sqc");
    public static final ModelResourceLocation CLAMP_JAW_RACK = model("rod_clamp_jaw_rack_sqc");

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + name));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BigMachineRegistry.CONTROLLER_BE.get(), BigMachineRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(CLAMP_FRAME);
        event.register(CLAMP_JAW);
        event.register(CLAMP_JAW_RACK);
    }
}
