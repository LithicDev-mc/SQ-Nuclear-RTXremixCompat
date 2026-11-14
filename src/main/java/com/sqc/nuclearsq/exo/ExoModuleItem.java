package com.sqc.nuclearsq.exo;

public interface ExoModuleItem {
    int level();

    default boolean fits(net.minecraft.world.item.ItemStack piece) {
        return true;
    }
}
