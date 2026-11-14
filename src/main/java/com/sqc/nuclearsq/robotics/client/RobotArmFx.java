package com.sqc.nuclearsq.robotics.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class RobotArmFx {
    private RobotArmFx() {}

    private static final String[] BLOCKS = {"robot_arm_sqc", "heavy_robot_arm_sqc"};
    private static final String[] PARTS = {"head", "jaw_l", "jaw_r"};

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RoboticsRegistry.ARM_BE.get(), RobotArmRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        for (String b : BLOCKS) for (String p : PARTS) event.register(RobotArmRenderer.model(b, p));
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        RobotArmRenderer.clearCache();
    }
}
