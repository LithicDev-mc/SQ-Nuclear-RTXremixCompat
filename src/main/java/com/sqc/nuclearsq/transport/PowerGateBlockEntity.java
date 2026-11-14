package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.DeviceMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class PowerGateBlockEntity extends BlockEntity {
    public static final int MAX_RATE = TransportTier.HARDENED.cableRate, CAPACITY = MAX_RATE * 4, STEP = 5;
    public static final int DATA = 10, FLAG_CHARGING = 1, FLAG_RELEASING = 2;

    private int stored, ratePct = 100, lastMoved;
    private final int[] thresh = {100, 100, 0, 0};
    private boolean charging = true, releasing = true;
    private int inTick;
    private final IEnergyStorage input = new Side(true), output = new Side(false);

    public PowerGateBlockEntity(BlockPos pos, BlockState state) {
        super(TransportRegistry.POWER_GATE_BE.get(), pos, state);
    }

    public int stored() { return stored; }
    public int ratePct() { return ratePct; }
    public int rate() { return Math.max(1, (int) ((long) MAX_RATE * ratePct / 100)); }
    public int threshold(int i) { return thresh[Math.floorMod(i, 4)]; }
    public boolean charging() { return charging; }
    public boolean releasing() { return releasing; }
    public int lastMoved() { return lastMoved; }

    public void setRatePct(int pct) {
        ratePct = Math.max(STEP, Math.min(100, pct));
        setChanged();
    }

    public void setThreshold(int i, int percent) {
        if (i < 0 || i > 3) return;
        int v = Math.max(0, Math.min(100, percent));
        thresh[i] = v;
        if (i == 0 && thresh[1] < v) thresh[1] = v;
        if (i == 1 && thresh[0] > v) thresh[0] = v;
        if (i == 2 && thresh[3] < v) thresh[3] = v;
        if (i == 3 && thresh[2] > v) thresh[2] = v;
        evaluate();
        setChanged();
    }

    private void evaluate() {
        long e = stored * 100L, cap = CAPACITY;
        if (thresh[1] < 100 && e >= cap * thresh[1]) charging = false;
        else if (e <= cap * thresh[0]) charging = true;
        if (thresh[2] > 0 && e <= cap * thresh[2]) releasing = false;
        else if (e >= cap * thresh[3]) releasing = true;
    }

    private int room() {
        return (int) Math.max(0, Math.min(CAPACITY - stored, (long) CAPACITY * thresh[1] / 100 - stored));
    }

    private int stock() {
        return (int) Math.max(0, stored - (long) CAPACITY * thresh[2] / 100);
    }

    @Nullable
    public IEnergyStorage energySide(@Nullable Direction side) {
        Direction face = getBlockState().getValue(PowerGateBlock.FACING);
        if (side == face) return output;
        if (side == face.getOpposite()) return input;
        return null;
    }

    private final class Side implements IEnergyStorage {
        private final boolean in;

        Side(boolean in) { this.in = in; }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            if (!in || !charging) return 0;
            int n = Math.max(0, Math.min(max, Math.min(room(), rate() - inTick)));
            if (!simulate && n > 0) {
                stored += n;
                inTick += n;
                setChanged();
            }
            return n;
        }

        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return in && charging; }
    }

    public void serverTick() {
        evaluate();
        int moved = 0;
        if (releasing && stored > 0) {
            Direction face = getBlockState().getValue(PowerGateBlock.FACING);
            BlockPos n = worldPosition.relative(face);
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, face.getOpposite());
            int budget = Math.min(rate(), stock());
            if (target != null && target.canReceive() && budget > 0) {
                moved = target.receiveEnergy(budget, false);
                stored -= moved;
                setChanged();
            }
        }
        lastMoved = moved;
        inTick = 0;
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> ratePct;
                    case 1, 2, 3, 4 -> thresh[i - 1];
                    case 5 -> DeviceMenu.lo(stored);
                    case 6 -> DeviceMenu.hi(stored);
                    case 7 -> (charging ? FLAG_CHARGING : 0) | (releasing ? FLAG_RELEASING : 0);
                    case 8 -> DeviceMenu.lo(lastMoved);
                    default -> DeviceMenu.hi(lastMoved);
                };
            }

            @Override public void set(int i, int v) { }
            @Override public int getCount() { return DATA; }
        };
    }

    public void openTerminal(ServerPlayer sp) {
        DeviceMenu.open(sp, worldPosition, DeviceMenu.POWER_GATE, menuData(), Component.translatable("block.nuclearsq.power_gate_sqc"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Stored", stored);
        tag.putInt("RatePct", ratePct);
        tag.putIntArray("Thresh", thresh.clone());
        tag.putBoolean("Charging", charging);
        tag.putBoolean("Releasing", releasing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        stored = Math.max(0, Math.min(CAPACITY, tag.getInt("Stored")));
        ratePct = tag.contains("RatePct") ? Math.max(STEP, Math.min(100, tag.getInt("RatePct"))) : 100;
        int[] th = tag.getIntArray("Thresh");
        if (th.length == 4) for (int k = 0; k < 4; k++) thresh[k] = Math.max(0, Math.min(100, th[k]));
        charging = !tag.contains("Charging") || tag.getBoolean("Charging");
        releasing = !tag.contains("Releasing") || tag.getBoolean("Releasing");
    }
}
