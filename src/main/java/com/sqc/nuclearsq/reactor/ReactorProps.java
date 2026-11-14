package com.sqc.nuclearsq.reactor;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ReactorProps {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public static final BooleanProperty ROD_FORMED = BooleanProperty.create("rod_formed");

    public static final BooleanProperty DRIVEN = BooleanProperty.create("driven");

    public static final BooleanProperty GLOWING = BooleanProperty.create("glowing");

    private ReactorProps() {}
}
