package com.sqc.nuclearsq.compat.cc;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.sqc.nuclearsq.compat.powergrid.GridTerminalBlockEntity;
import com.sqc.nuclearsq.compat.powergrid.PowerGridRegistry;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlock;

public final class PowerGridCcCompat {
    private PowerGridCcCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), PowerGridRegistry.TERMINAL_BE.get(), (be, side) -> new Grid(be));
    }

    public static final class Grid implements IPeripheral {
        private final GridTerminalBlockEntity be;

        Grid(GridTerminalBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "sqc_grid_terminal";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Grid g && g.be == be;
        }

        @Override
        public Object getTarget() {
            return be;
        }

        private IEnergyStorage buffer() throws LuaException {
            if (be.isRemoved()) throw new LuaException("not loaded");
            IEnergyStorage buf = be.getEnergyStorage(be.getBlockState().getValue(FEInverterBlock.FACING));
            if (buf == null) throw new LuaException("no buffer");
            return buf;
        }

        @LuaFunction(mainThread = true)
        public final int getEnergy() throws LuaException {
            return buffer().getEnergyStored();
        }

        @LuaFunction(mainThread = true)
        public final int getCapacity() throws LuaException {
            return buffer().getMaxEnergyStored();
        }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getInfo() throws LuaException {
            Map<String, Object> m = new HashMap<>();
            m.put("energy", getEnergy());
            m.put("capacity", getCapacity());
            return m;
        }
    }
}
