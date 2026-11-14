package com.sqc.nuclearsq.compat.cc;

import com.sqc.nuclearsq.economy.EconomyRegistry;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class EconomyCcCompat {
    private EconomyCcCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), EconomyRegistry.TERMINAL_BE.get(),
                (be, side) -> new BankPeripheral(be.getLevel(), be.getBlockPos()));
        event.registerBlockEntity(PeripheralCapability.get(), EconomyRegistry.MODEM_BE.get(),
                (be, side) -> new BankPeripheral(be.getLevel(), be.getBlockPos()));
    }
}
