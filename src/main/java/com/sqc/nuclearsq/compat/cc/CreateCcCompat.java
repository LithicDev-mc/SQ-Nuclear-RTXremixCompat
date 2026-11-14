package com.sqc.nuclearsq.compat.cc;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.sqc.nuclearsq.compat.create.CreateShaftOutputBlockEntity;
import com.sqc.nuclearsq.compat.create.CreateShaftRegistry;

public final class CreateCcCompat {
    private CreateCcCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), CreateShaftRegistry.OUTPUT_BE.get(), (be, side) -> new Shaft(be));
    }

    public static final class Shaft implements IPeripheral {
        private final CreateShaftOutputBlockEntity be;

        Shaft(CreateShaftOutputBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "sqc_shaft";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Shaft s && s.be == be;
        }

        @Override
        public Object getTarget() {
            return be;
        }

        @LuaFunction(mainThread = true)
        public final int getRpm() throws LuaException {
            if (be.isRemoved()) throw new LuaException("not loaded");
            return be.rpm();
        }

        @LuaFunction(mainThread = true)
        public final int getStress() throws LuaException {
            if (be.isRemoved()) throw new LuaException("not loaded");
            return be.su();
        }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getInfo() throws LuaException {
            Map<String, Object> m = new HashMap<>();
            m.put("rpm", getRpm());
            m.put("su", getStress());
            return m;
        }
    }
}
