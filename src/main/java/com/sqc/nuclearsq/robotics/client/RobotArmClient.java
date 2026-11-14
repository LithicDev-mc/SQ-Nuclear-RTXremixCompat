package com.sqc.nuclearsq.robotics.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class RobotArmClient {
    private RobotArmClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(RoboticsRegistry.ARM_MENU.get(), RobotArmScreen::new);
        event.register(RoboticsRegistry.GRIPPER_MENU.get(), RodGripperScreen::new);
    }
}
