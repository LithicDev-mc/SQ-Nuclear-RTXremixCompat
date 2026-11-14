package com.sqc.nuclearsq.explosion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class NuclearDetonationEvent extends Event implements ICancellableEvent {
    private final ServerLevel level;
    private final Vec3 center;
    private final NuclearExplosion.Blast blast;

    public NuclearDetonationEvent(ServerLevel level, Vec3 center, NuclearExplosion.Blast blast) {
        this.level = level;
        this.center = center;
        this.blast = blast;
    }

    public ServerLevel getLevel() { return level; }

    public Vec3 getCenter() { return center; }

    public NuclearExplosion.Blast getBlast() { return blast; }

    public int getRadius() { return blast.radius(); }
}
