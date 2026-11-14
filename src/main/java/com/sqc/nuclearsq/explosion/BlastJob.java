package com.sqc.nuclearsq.explosion;

import com.mojang.serialization.MapCodec;

import net.minecraft.server.level.ServerLevel;

public interface BlastJob {
    record Type<T extends BlastJob>(String id, MapCodec<T> codec) {}

    Type<?> type();

    boolean tick(ServerLevel level, BlastJobs.Budget budget);

    default boolean changesBlocks() {
        return true;
    }

    default void onStopping(ServerLevel level) {}
}
