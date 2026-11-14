package com.sqc.nuclearsq.machine;

import com.sqc.nuclearsq.registry.TestSourceRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CreativeSourceBlockEntity extends BlockEntity {
    public static final int FE_PER_TICK = 100_000, MB_PER_TICK = 16000;

    private Fluid fluid = Fluids.WATER;

    public CreativeSourceBlockEntity(BlockPos pos, BlockState state) {
        super(TestSourceRegistry.SOURCE_BE.get(), pos, state);
    }

    private boolean isEnergy() {
        return getBlockState().getBlock() instanceof CreativeSourceBlock b && b.energy();
    }

    public Fluid fluid() {
        return fluid;
    }

    public void setFluid(Fluid f) {
        fluid = f;
        setChanged();
    }

    void serverTick() {
        boolean e = isEnergy();
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            if (e) {
                IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite());
                if (s != null && s.canReceive()) s.receiveEnergy(FE_PER_TICK, false);
            } else {
                IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, n, d.getOpposite());
                if (h != null) h.fill(new FluidStack(fluid, MB_PER_TICK), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) { return max; }
        @Override public int getEnergyStored() { return Integer.MAX_VALUE; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public final IFluidHandler tank = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return new FluidStack(fluid, Integer.MAX_VALUE); }
        @Override public int getTankCapacity(int t) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
        @Override public int fill(FluidStack s, FluidAction a) { return 0; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) {
            return s.getFluid() == fluid ? s.copy() : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int max, FluidAction a) { return new FluidStack(fluid, max); }
    };

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fluid")) {
            Fluid f = BuiltInRegistries.FLUID.get(ResourceLocation.parse(tag.getString("fluid")));
            fluid = f == Fluids.EMPTY ? Fluids.WATER : f;
        }
    }
}
