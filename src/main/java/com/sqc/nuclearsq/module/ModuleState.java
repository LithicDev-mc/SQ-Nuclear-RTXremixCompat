package com.sqc.nuclearsq.module;

import net.minecraft.util.StringRepresentable;

public enum ModuleState implements StringRepresentable {
    IDLE("idle"), ACTIVE("active"), WARN("warn");

    private final String name;

    ModuleState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
