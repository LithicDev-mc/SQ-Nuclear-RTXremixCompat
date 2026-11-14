package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class ClusterMachine {
    private final MachineEnergy energy;
    private final ClusterThermal thermal = new ClusterThermal();

    public ClusterMachine(MachineEnergy energy) {
        this.energy = energy;
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.COMPUTE_CLUSTER;
    }

    public ClusterThermal thermal() {
        return thermal;
    }

    public int tick(BigMachineBlockEntity be, boolean formed, boolean enabled) {
        FluidTank water = be.tank("water_in");
        thermal.step(formed, enabled, ClusterValues.machine().fePerTick(), n -> {
            if (energy.getEnergyStored() < n) return false;
            energy.drain(n);
            return true;
        }, water);
        return thermal.state() == ClusterThermal.RUNNING ? 1 : 0;
    }

    public ServerRack.Compute supply() {
        if (thermal.state() != ClusterThermal.RUNNING) return ServerRack.Compute.NONE;
        ClusterValues.Machine m = ClusterValues.machine();
        return new ServerRack.Compute((int) Math.floor(m.cpu() * thermal.throttle()), m.ramMb(), 1);
    }

    public CompoundTag save(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        thermal.save(t);
        return t;
    }

    public void load(CompoundTag t, HolderLookup.Provider reg) {
        thermal.load(t);
    }
}
