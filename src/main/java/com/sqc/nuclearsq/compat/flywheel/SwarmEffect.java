package com.sqc.nuclearsq.compat.flywheel;

import com.sqc.nuclearsq.nanobot.client.ClientSwarm;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.LevelAccessor;

final class SwarmEffect implements Effect {
    private final LevelAccessor level;
    private final ClientSwarm swarm;

    SwarmEffect(LevelAccessor level, ClientSwarm swarm) {
        this.level = level;
        this.swarm = swarm;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new SwarmVisual(ctx, swarm);
    }
}
