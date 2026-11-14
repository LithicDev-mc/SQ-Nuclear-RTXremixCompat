package com.sqc.nuclearsq.nanobot;

import net.minecraft.network.chat.Component;

public enum NanoTask {
    FELL_TREE("fell_tree"),
    COLLECT_ITEMS("collect_items"),
    MINE_ORE("mine_ore"),
    PLANT_SAPLINGS("plant_saplings"),
    HARVEST("harvest"),
    TILL("till"),
    MINE_STONE("mine_stone"),
    DECONTAMINATE("decontaminate"),
    GADGET_BREAK("gadget_break"),
    GADGET_BUILD("gadget_build"),
    GADGET_REPLACE("gadget_replace"),
    GADGET_COPY("gadget_copy"),
    GADGET_PASTE("gadget_paste");

    public final String id;

    NanoTask(String id) {
        this.id = id;
    }

    public boolean near() {
        return this == HARVEST || this == TILL || this == MINE_STONE || this == DECONTAMINATE;
    }

    public boolean gadget() {
        return this == GADGET_BREAK || this == GADGET_BUILD || this == GADGET_REPLACE || this == GADGET_COPY || this == GADGET_PASTE;
    }

    public static int autoCount() {
        return GADGET_BREAK.ordinal();
    }

    public NanoTask next() {
        NanoTask[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static NanoTask byOrdinal(int i) {
        NanoTask[] all = values();
        return all[Math.floorMod(i, all.length)];
    }

    public Component label() {
        return Component.translatable("message.nuclearsq.nanobots.task." + id);
    }
}
