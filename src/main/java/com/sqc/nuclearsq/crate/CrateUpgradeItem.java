package com.sqc.nuclearsq.crate;

import net.minecraft.world.item.Item;

public class CrateUpgradeItem extends Item {
    public final int tier;

    public CrateUpgradeItem(int tier) {
        super(new Item.Properties().stacksTo(16));
        this.tier = tier;
    }
}
