package com.sqc.nuclearsq.transport;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class LargePowerMastBlock extends PowerMastBlock {
    public static final int HEIGHT = 24, SPAN = 4 * GridNode.MAX_SPAN;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, HEIGHT - 1);

    public LargePowerMastBlock(Properties props) {
        super(props);
    }

    @Override public IntegerProperty part() { return PART; }
    @Override public int height() { return HEIGHT; }
    @Override protected double scale() { return 2; }
    @Override public int span() { return SPAN; }
}
