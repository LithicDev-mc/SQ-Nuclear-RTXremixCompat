package com.sqc.nuclearsq.explosion.bomb;

import com.sqc.nuclearsq.explosion.NuclearExplosion;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class LargeNuclearBombBlock extends MultiPartBombBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 15);
    public static final int PARTS = 16, WIDTH = 2, HEIGHT = 4;

    private static final double[][] BOXES = {
            {11, 0, 11, 21, 4, 21},
            {7, 4, 7, 25, 10, 25},
            {4, 10, 4, 28, 16, 28},
            {2, 16, 2, 30, 44, 30},
            {4, 44, 4, 28, 50, 28},
            {7, 50, 7, 25, 58, 25},
            {15, 44, 3, 17, 64, 29},
            {3, 44, 15, 29, 64, 17},
            {2, 55, 2, 30, 64, 30},
    };
    private static final int[] MODEL_PARTS = {0, 8};

    public LargeNuclearBombBlock(Properties properties) {
        super(properties, WIDTH, HEIGHT, BOXES);
    }

    @Override
    public IntegerProperty partProperty() {
        return PART;
    }

    @Override
    public int[] modelParts() {
        return MODEL_PARTS;
    }

    @Override
    public NuclearExplosion.Blast blast() {
        return NuclearExplosion.Blast.largeBomb();
    }

    @Override
    protected String tooltipKey(int line) {
        return line == 1 ? "tooltip.nuclearsq.bomb_large.1" : super.tooltipKey(line);
    }
}
