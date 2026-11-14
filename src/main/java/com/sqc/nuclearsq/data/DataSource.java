package com.sqc.nuclearsq.data;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public interface DataSource {
    int OK = 0, WARN = 1, ALARM = 2, OFF = 3;

    BlockPos pos();

    Component name();

    String type();

    int state();

    Component stateText();

    List<DataValue> values();

    default List<String> settable() {
        return List.of();
    }

    @Nullable
    default Component set(String key, double value) {
        return Component.translatable("data.nuclearsq.err.readonly", key);
    }

    default boolean mayControl(ServerPlayer player) {
        return true;
    }

    void open(ServerPlayer player);

    default List<String> priority() {
        return List.of();
    }

    default List<DataValue> display(int max) {
        return DataDisplay.pick(this, max);
    }

    @Nullable
    default PowerFlow power() {
        return null;
    }

    @Nullable
    default DataValue value(String key) {
        for (DataValue v : values()) if (v.key().equals(key)) return v;
        return null;
    }
}
