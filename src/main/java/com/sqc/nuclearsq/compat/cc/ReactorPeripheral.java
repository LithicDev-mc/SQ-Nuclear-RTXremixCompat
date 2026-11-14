package com.sqc.nuclearsq.compat.cc;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.DriveCore;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ReactorPeripheral implements IPeripheral {
    private final BlockEntity be;

    public ReactorPeripheral(BlockEntity be) {
        this.be = be;
    }

    @Override
    public String getType() {
        return "sqc_reactor";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ReactorPeripheral p && p.be == be;
    }

    @Override
    public Object getTarget() {
        return be;
    }

    @Override
    public void attach(IComputerAccess computer) {
        boolean[] last = {false};
        boolean[] known = {false};
        CcEvents.watch(this, computer, c -> {
            BigMachineBlockEntity d = driveOrNull();
            DriveCore core = d == null ? null : d.core();
            if (core == null) return;
            boolean hot = core.overheated();
            if (known[0] && hot != last[0]) c.queueEvent("sqc_overheat", c.getAttachmentName(), hot);
            last[0] = hot;
            known[0] = true;
        });
    }

    @Override
    public void detach(IComputerAccess computer) {
        CcEvents.unwatch(this, computer);
    }

    private BigMachineBlockEntity driveOrNull() {
        BigMachineBlockEntity d = be instanceof BigMachineBlockEntity self ? self
                : be instanceof BigPortBlockEntity port ? port.findController() : null;
        return d != null && d.machine() == BigMachine.CONTROL_ROD_DRIVE && d.isFormed() ? d : null;
    }

    private BigMachineBlockEntity drive() throws LuaException {
        BigMachineBlockEntity d = driveOrNull();
        if (d == null) throw new LuaException("control rod drive not assembled");
        return d;
    }

    private DriveCore core() throws LuaException {
        DriveCore c = drive().core();
        if (c == null) throw new LuaException("no reactor core");
        return c;
    }

    @LuaFunction(mainThread = true)
    public final boolean isFormed() {
        return driveOrNull() != null;
    }

    @LuaFunction(mainThread = true)
    public final String getStatus() {
        return driveOrNull() != null ? "ok" : "not assembled";
    }

    @LuaFunction(mainThread = true)
    public final int getDepth() throws LuaException {
        return drive().rod();
    }

    @LuaFunction(mainThread = true)
    public final void setDepth(int value) throws LuaException {
        if (value < 0 || value > BigMachineBlockEntity.ROD_MAX) throw new LuaException("depth must be 0-15");
        drive().setRodTarget(value);
    }

    @LuaFunction(mainThread = true)
    public final void scram() throws LuaException {
        drive().scram();
    }

    @LuaFunction(mainThread = true)
    public final int getPower() throws LuaException {
        return drive().reactorPower();
    }

    @LuaFunction(mainThread = true)
    public final boolean hasRod() throws LuaException {
        return drive().rodCount() > 0;
    }

    @LuaFunction(mainThread = true)
    public final int getFuel() throws LuaException {
        return drive().fuelPercent();
    }

    @LuaFunction(mainThread = true)
    public final int getHeat() throws LuaException {
        return core().heatPercent();
    }

    @LuaFunction(mainThread = true)
    public final boolean isOverheated() throws LuaException {
        return core().overheated();
    }

    @LuaFunction(mainThread = true)
    public final double getCooling() throws LuaException {
        return core().cooling();
    }

    @LuaFunction(mainThread = true)
    public final int getPrimaryFlow() throws LuaException {
        return drive().vesselMb();
    }

    @LuaFunction(mainThread = true)
    public final int getHotPrimaryStored() throws LuaException {
        var t = drive().tank("primary_hot_out");
        return t == null ? 0 : t.getFluidAmount();
    }

    @LuaFunction(mainThread = true)
    public final int getMaxPower() {
        return DriveCore.MAX_POWER;
    }
}
