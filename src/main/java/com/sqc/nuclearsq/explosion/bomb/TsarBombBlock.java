package com.sqc.nuclearsq.explosion.bomb;

import com.sqc.nuclearsq.explosion.NuclearExplosion;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class TsarBombBlock extends MultiPartBombBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 53);
    public static final int WIDTH = 3, HEIGHT = 6, PARTS = WIDTH * WIDTH * HEIGHT;

    private static final double[][] BOXES = {
            {18, 0, 18, 30, 4, 30},
            {12, 4, 12, 36, 12, 36},
            {6, 12, 6, 42, 22, 42},
            {3, 22, 3, 45, 70, 45},
            {6, 70, 6, 42, 76, 42},
            {10, 76, 10, 38, 86, 38},
            {14, 86, 14, 34, 96, 34},
            {23, 64, 0, 25, 96, 48},
            {0, 64, 23, 48, 96, 25},
    };
    private static final int[] MODEL_PARTS = {13, 40};

    public TsarBombBlock(Properties properties) {
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
        return NuclearExplosion.Blast.tsarBomb();
    }

    @Override
    protected String tooltipKey(int line) {
        return line == 1 ? "tooltip.nuclearsq.bomb_tsar.1" : super.tooltipKey(line);
    }
}
