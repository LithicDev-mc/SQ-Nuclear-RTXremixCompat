package com.sqc.nuclearsq.module;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public enum ModuleKind {
    FAN("fan_module_sqc", false, Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 8, 15))),
    OVERCLOCK("overclock_module_sqc", false, Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(2, 2, 2, 14, 14, 14))),
    ENERGY_SAVER("energy_saver_module_sqc", true, Block.box(1, 0, 3, 15, 16, 13));

    public static final int MAX = 2;
    public static final int SPEED_PCT = 25, OVERCLOCK_ENERGY_PCT = 40, SAVER_PCT = 20;

    public final String id;
    public final boolean tall;
    public final VoxelShape shape;

    ModuleKind(String id, boolean tall, VoxelShape shape) {
        this.id = id;
        this.tall = tall;
        this.shape = shape;
    }
}
