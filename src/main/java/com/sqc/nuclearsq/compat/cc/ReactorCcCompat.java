package com.sqc.nuclearsq.compat.cc;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ReactorCcCompat {
    private ReactorCcCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> be == null ? null : new ReactorPeripheral(be),
                BigMachineRegistry.CONTROLLERS.get(BigMachine.CONTROL_ROD_DRIVE).get());
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            if (!(be instanceof BigPortBlockEntity port)) return null;
            var c = port.findController();
            return c != null && c.machine() == BigMachine.CONTROL_ROD_DRIVE ? new ReactorPeripheral(be) : null;
        }, BigMachineRegistry.PORTS.get(BigPortKind.SIGNAL).get(), BigMachineRegistry.PORTS.get(BigPortKind.TERMINAL).get());
    }
}
