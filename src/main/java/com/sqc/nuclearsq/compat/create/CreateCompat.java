package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.sqc.nuclearsq.registry.BombRegistry;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

public final class CreateCompat {
    private CreateCompat() {}

    public static void register() {
        BombMovementBehaviour movement = new BombMovementBehaviour();
        BombMovingInteraction interaction = new BombMovingInteraction();
        MovementBehaviour.REGISTRY.register(BombRegistry.BOMB.get(), movement);
        MovementBehaviour.REGISTRY.register(BombRegistry.LARGE_BOMB.get(), movement);
        MovementBehaviour.REGISTRY.register(BombRegistry.TSAR_BOMB.get(), movement);
        MovingInteractionBehaviour.REGISTRY.register(BombRegistry.BOMB.get(), interaction);
        MovingInteractionBehaviour.REGISTRY.register(BombRegistry.LARGE_BOMB.get(), interaction);
        MovingInteractionBehaviour.REGISTRY.register(BombRegistry.TSAR_BOMB.get(), interaction);
        MovementBehaviour.REGISTRY.register(RoboticsRegistry.ROD_GRIPPER.get(), new RodGripperMovementBehaviour());
    }
}
