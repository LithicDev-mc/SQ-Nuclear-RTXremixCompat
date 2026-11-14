package com.sqc.nuclearsq.compat.flywheel;

import java.util.IdentityHashMap;
import java.util.Map;

import com.sqc.nuclearsq.nanobot.client.ClientSwarm;
import com.sqc.nuclearsq.nanobot.client.NanoSwarmClient;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class FlywheelSwarmCompat {
    private FlywheelSwarmCompat() {}

    public static void register() {
        NanoSwarmClient.presenter = new Presenter();
    }

    private record Registered(SwarmEffect effect, VisualizationManager manager) {}

    private static final class Presenter implements NanoSwarmClient.Presenter {
        private final Map<ClientSwarm, Registered> effects = new IdentityHashMap<>();

        @Override
        public boolean add(ClientSwarm swarm) {
            ClientLevel level = Minecraft.getInstance().level;
            VisualizationManager manager = level == null ? null : VisualizationManager.get(level);
            if (manager == null) return false;
            SwarmEffect effect = new SwarmEffect(level, swarm);
            manager.effects().queueAdd(effect);
            effects.put(swarm, new Registered(effect, manager));
            return true;
        }

        @Override
        public void remove(ClientSwarm swarm) {
            Registered r = effects.remove(swarm);
            if (r != null) r.manager().effects().queueRemove(r.effect());
        }

        @Override
        public void tick(ClientSwarm swarm) {
            ClientLevel level = Minecraft.getInstance().level;
            VisualizationManager now = level == null ? null : VisualizationManager.get(level);
            Registered r = effects.get(swarm);
            if (r != null && now != r.manager()) {
                effects.remove(swarm);
                swarm.setExternal(add(swarm));
            } else if (r == null && now != null && !swarm.isExternal()) {
                swarm.setExternal(add(swarm));
            }
        }
    }
}
