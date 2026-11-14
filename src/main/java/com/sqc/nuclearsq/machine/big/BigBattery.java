package com.sqc.nuclearsq.machine.big;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.MachineEnergy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class BigBattery {
    public static final int MODES = 3, MAX_PORTS = 4;
    public static final int DATA = 13 + MAX_PORTS + 5;
    public static final int PORT_ON = 1, PORT_OUT = 2;
    public static final int THRESH_FIRST = 13 + MAX_PORTS, THRESH_FLAGS = THRESH_FIRST + 4, FLAG_CHARGING = 1, FLAG_RELEASING = 2;
    public static final int STEP = 5;

    private final MachineEnergy store;
    private final List<BigMachine.Port> ports = new ArrayList<>();
    private final IEnergyStorage[] access;
    private final boolean[] on = new boolean[MAX_PORTS];
    private final boolean[] out = new boolean[MAX_PORTS];
    private final int[] used = new int[MAX_PORTS];
    private int mode, inTick, outTick, lastIn, lastOut;
    private float inAvg, outAvg;
    private boolean signal;
    private final int[] thresh = {100, 100, 0, 0};
    private boolean charging = true, releasing = true;
    private final Runnable changed;

    public BigBattery(BigMachine machine, MachineEnergy store, Runnable changed) {
        this.store = store;
        this.changed = changed;
        for (BigMachine.Port p : machine.ports) if (p.kind() == BigPortKind.ENERGY && ports.size() < MAX_PORTS) ports.add(p);
        access = new IEnergyStorage[ports.size()];
        for (int i = 0; i < ports.size(); i++) {
            on[i] = true;
            out[i] = !ports.get(i).in();
            access[i] = access(i);
        }
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.BATTERY_SMALL || m == BigMachine.BATTERY_MEDIUM || m == BigMachine.BATTERY_LARGE;
    }

    public MachineEnergy store() { return store; }
    public int mode() { return mode; }
    public boolean portOn(int i) { return i >= 0 && i < ports.size() && on[i]; }
    public boolean portOut(int i) { return i >= 0 && i < ports.size() && out[i]; }
    public boolean isOutput(BigMachine.Port p) { return portOut(ports.indexOf(p)); }
    public List<BigMachine.Port> ports() { return ports; }

    public boolean held() {
        return mode == 1 && !signal || mode == 2 && signal;
    }

    public int threshold(int i) { return thresh[Math.floorMod(i, 4)]; }
    public boolean charging() { return charging; }
    public boolean releasing() { return releasing; }

    public void setThreshold(int i, int percent) {
        if (i < 0 || i > 3) return;
        int v = Math.max(0, Math.min(100, percent));
        thresh[i] = v;
        if (i == 0 && thresh[1] < v) thresh[1] = v;
        if (i == 1 && thresh[0] > v) thresh[0] = v;
        if (i == 2 && thresh[3] < v) thresh[3] = v;
        if (i == 3 && thresh[2] > v) thresh[2] = v;
        evaluate();
        changed.run();
    }

    private void evaluate() {
        long e = store.getEnergyStored() * 100L, cap = store.getMaxEnergyStored();
        if (thresh[1] < 100 && e >= cap * thresh[1]) charging = false;
        else if (e <= cap * thresh[0]) charging = true;
        if (thresh[2] > 0 && e <= cap * thresh[2]) releasing = false;
        else if (e >= cap * thresh[3]) releasing = true;
    }

    private int room() {
        long cap = store.getMaxEnergyStored();
        return (int) Math.max(0, Math.min(cap - store.getEnergyStored(), cap * thresh[1] / 100 - store.getEnergyStored()));
    }

    private int stock() {
        return (int) Math.max(0, store.getEnergyStored() - (long) store.getMaxEnergyStored() * thresh[2] / 100);
    }

    private boolean blocked() { return held() || !releasing; }

    public int rate() {
        int r = 0;
        for (BigMachine.Port p : ports) r = Math.max(r, p.rate());
        return r;
    }

    public int fill() { return (int) (store.getEnergyStored() * 100L / Math.max(1, store.getMaxEnergyStored())); }

    public int level() {
        int e = store.getEnergyStored(), cap = Math.max(1, store.getMaxEnergyStored());
        return e <= 0 ? 0 : Math.min(8, (int) ((e * 8L + cap - 1) / cap));
    }

    public void setPortOn(int i, boolean value) {
        if (i < 0 || i >= ports.size()) return;
        on[i] = value;
        changed.run();
    }

    public void setPortOut(int i, boolean value) {
        if (i < 0 || i >= ports.size()) return;
        out[i] = value;
        changed.run();
    }

    public void setMode(int value) {
        mode = Math.floorMod(value, MODES);
        changed.run();
    }

    public IEnergyStorage at(String name) {
        for (int i = 0; i < ports.size(); i++) if (ports.get(i).name().equals(name)) return access[i];
        return null;
    }

    private IEnergyStorage access(int i) {
        BigMachine.Port p = ports.get(i);
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int max, boolean simulate) {
                if (out[i] || !on[i] || !charging) return 0;
                int n = Math.max(0, Math.min(max, Math.min(room(), p.rate() - used[i])));
                if (!simulate && n > 0) {
                    store.add(n);
                    used[i] += n;
                    inTick += n;
                }
                return n;
            }

            @Override
            public int extractEnergy(int max, boolean simulate) {
                if (!out[i] || !on[i] || blocked()) return 0;
                int n = Math.max(0, Math.min(max, Math.min(stock(), p.rate() - used[i])));
                if (!simulate && n > 0) {
                    store.drain(n);
                    used[i] += n;
                    outTick += n;
                }
                return n;
            }

            @Override public int getEnergyStored() { return store.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return store.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return out[i] && on[i] && !blocked(); }
            @Override public boolean canReceive() { return !out[i] && on[i] && charging; }
        };
    }

    public int budget(BigMachine.Port p) {
        int i = ports.indexOf(p);
        if (i < 0 || !out[i] || !on[i] || blocked()) return 0;
        return Math.max(0, Math.min(stock(), p.rate() - used[i]));
    }

    public void took(BigMachine.Port p, int n) {
        int i = ports.indexOf(p);
        if (i < 0 || n <= 0) return;
        store.drain(n);
        used[i] += n;
        outTick += n;
    }

    public int tick(boolean redstone) {
        signal = redstone;
        evaluate();
        lastIn = inTick;
        lastOut = outTick;
        inAvg += (inTick - inAvg) * 0.1f;
        outAvg += (outTick - outAvg) * 0.1f;
        inTick = outTick = 0;
        java.util.Arrays.fill(used, 0);
        return lastIn + lastOut;
    }

    public int lastIn() { return lastIn; }
    public int lastOut() { return lastOut; }

    public ContainerData menuData(BigMachine machine) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> DeviceMenu.lo(store.getEnergyStored());
                    case 1 -> DeviceMenu.hi(store.getEnergyStored());
                    case 2 -> DeviceMenu.lo(store.getMaxEnergyStored());
                    case 3 -> DeviceMenu.hi(store.getMaxEnergyStored());
                    case 4 -> DeviceMenu.lo(Math.round(inAvg));
                    case 5 -> DeviceMenu.hi(Math.round(inAvg));
                    case 6 -> DeviceMenu.lo(Math.round(outAvg));
                    case 7 -> DeviceMenu.hi(Math.round(outAvg));
                    case 8 -> DeviceMenu.lo(rate());
                    case 9 -> DeviceMenu.hi(rate());
                    case 10 -> held() ? 1 : 0;
                    case 11 -> machine.ordinal();
                    case 12 -> mode;
                    case THRESH_FIRST, THRESH_FIRST + 1, THRESH_FIRST + 2, THRESH_FIRST + 3 -> thresh[i - THRESH_FIRST];
                    case THRESH_FLAGS -> (charging ? FLAG_CHARGING : 0) | (releasing ? FLAG_RELEASING : 0);
                    default -> (portOn(i - 13) ? PORT_ON : 0) | (portOut(i - 13) ? PORT_OUT : 0);
                };
            }

            @Override public void set(int i, int v) { }
            @Override public int getCount() { return DATA; }
        };
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("mode", mode);
        t.putIntArray("thresh", thresh.clone());
        t.putBoolean("charging", charging);
        t.putBoolean("releasing", releasing);
        for (int i = 0; i < ports.size(); i++) {
            t.putBoolean("on" + i, on[i]);
            t.putBoolean("out" + i, out[i]);
        }
        return t;
    }

    public void load(CompoundTag t) {
        mode = Math.floorMod(t.getInt("mode"), MODES);
        int[] th = t.getIntArray("thresh");
        if (th.length == 4) for (int k = 0; k < 4; k++) thresh[k] = Math.max(0, Math.min(100, th[k]));
        charging = !t.contains("charging") || t.getBoolean("charging");
        releasing = !t.contains("releasing") || t.getBoolean("releasing");
        for (int i = 0; i < ports.size(); i++) {
            on[i] = !t.contains("on" + i) || t.getBoolean("on" + i);
            out[i] = t.contains("out" + i) ? t.getBoolean("out" + i) : !ports.get(i).in();
        }
    }
}
