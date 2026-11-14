package com.sqc.nuclearsq.compat.cc;

import com.sqc.nuclearsq.data.DataControlBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class ControlPeripheral implements IPeripheral {
    private final DataControlBlockEntity be;

    public ControlPeripheral(DataControlBlockEntity be) {
        this.be = be;
    }

    @Override
    public String getType() {
        return "sqc_control";
    }

    @Override
    public void attach(IComputerAccess computer) {
        be.listeners.put(computer, (kind, value, player) ->
                computer.queueEvent("sqc_control", computer.getAttachmentName(), kind, value, player));
    }

    @Override
    public void detach(IComputerAccess computer) {
        be.listeners.remove(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ControlPeripheral p && p.be == be;
    }

    @Override
    public Object getTarget() {
        return be;
    }

    @LuaFunction(mainThread = true)
    public final String getKind() throws LuaException {
        if (be.isRemoved()) throw new LuaException("not loaded");
        return be.kindName();
    }

    @LuaFunction(mainThread = true)
    public final double getValue() throws LuaException {
        if (be.isRemoved()) throw new LuaException("not loaded");
        return be.lastValue();
    }

    @LuaFunction(mainThread = true)
    public final boolean isBound() {
        return be.source() != null && !be.key().isEmpty();
    }
}
