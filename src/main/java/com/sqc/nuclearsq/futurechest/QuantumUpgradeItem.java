package com.sqc.nuclearsq.futurechest;

import net.minecraft.world.item.Item;

public class QuantumUpgradeItem extends Item {
    public enum Kind {
        CAPACITY("quantum_capacity_upgrade_sqc", 2), MAGNET("quantum_magnet_upgrade_sqc", 1), SORTER("quantum_sorter_upgrade_sqc", 1);

        public final String id;
        public final int stack;

        Kind(String id, int stack) {
            this.id = id;
            this.stack = stack;
        }
    }

    public final Kind kind;

    public QuantumUpgradeItem(Kind kind) {
        super(new Item.Properties().stacksTo(kind.stack));
        this.kind = kind;
    }
}
