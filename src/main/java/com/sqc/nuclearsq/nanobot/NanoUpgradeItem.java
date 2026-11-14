package com.sqc.nuclearsq.nanobot;

import net.minecraft.world.item.Item;

public class NanoUpgradeItem extends Item {
    public enum Type {
        SPEED("speed", 5), COUNT("count", 5), REACH("reach", 5), POINT("point", 1);

        public final String id;
        public final int maxLevel;

        Type(String id, int maxLevel) {
            this.id = id;
            this.maxLevel = maxLevel;
        }
    }

    private final Type type;
    private final int level;

    public NanoUpgradeItem(Properties props, Type type, int level) {
        super(props);
        this.type = type;
        this.level = level;
    }

    public Type type() {
        return type;
    }

    public int level() {
        return level;
    }
}
