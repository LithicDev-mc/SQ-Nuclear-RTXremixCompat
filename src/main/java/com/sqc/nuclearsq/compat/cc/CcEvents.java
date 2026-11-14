package com.sqc.nuclearsq.compat.cc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CcEvents {
    private CcEvents() {}

    public static final int PERIOD = 20;

    private record Key(IPeripheral peripheral, IComputerAccess computer) {}

    private static final Map<Key, Consumer<IComputerAccess>> WATCHERS = new ConcurrentHashMap<>();

    public static void watch(IPeripheral peripheral, IComputerAccess computer, Consumer<IComputerAccess> poll) {
        WATCHERS.put(new Key(peripheral, computer), poll);
    }

    public static void unwatch(IPeripheral peripheral, IComputerAccess computer) {
        WATCHERS.remove(new Key(peripheral, computer));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHERS.isEmpty() || event.getServer().getTickCount() % PERIOD != 0) return;
        for (Map.Entry<Key, Consumer<IComputerAccess>> e : WATCHERS.entrySet()) {
            try {
                e.getValue().accept(e.getKey().computer());
            } catch (RuntimeException ex) {
            }
        }
    }
}
