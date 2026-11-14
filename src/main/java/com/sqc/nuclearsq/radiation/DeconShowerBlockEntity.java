package com.sqc.nuclearsq.radiation;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DeconShowerBlockEntity extends BlockEntity {
    public static final int CAPACITY = 4000;
    public static final int USE = 20;
    public static final int REACH = 3;

    private final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.is(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final IFluidHandler input = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluidInTank(t);
        }

        @Override
        public int getTankCapacity(int t) {
            return tank.getTankCapacity(t);
        }

        @Override
        public boolean isFluidValid(int t, FluidStack fs) {
            return tank.isFluidValid(t, fs);
        }

        @Override
        public int fill(FluidStack fs, FluidAction action) {
            return tank.fill(fs, action);
        }

        @Override
        public FluidStack drain(FluidStack fs, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int max, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private boolean enabled = true;

    public DeconShowerBlockEntity(BlockPos pos, BlockState state) {
        super(ProtectionRegistry.SHOWER_BE.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    public IFluidHandler input() {
        return input;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        setChanged();
    }

    public AABB area() {
        return new AABB(worldPosition.getX() + 0.1, worldPosition.getY() - REACH, worldPosition.getZ() + 0.1,
                worldPosition.getX() + 0.9, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.9);
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % 20 != Math.floorMod(worldPosition.asLong(), 20)) return;
        if (level.getFluidState(worldPosition.above()).isSourceOfType(Fluids.WATER)) {
            tank.fill(new FluidStack(Fluids.WATER, CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        }
        boolean active = enabled && wash(com.sqc.nuclearsq.compat.sable.SableCompat.entitiesIn(level, Player.class, area(), p -> !p.isSpectator()));
        if (active) {
            level.playSound(null, worldPosition.below(), SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.4f, 1.4f);
            if (level.random.nextInt(3) == 0) {
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.08f, 0.5f + level.random.nextFloat() * 0.1f);
            }
        }
        BlockState st = getBlockState();
        if (st.getValue(DeconShowerBlock.ACTIVE) != active) level.setBlock(worldPosition, st.setValue(DeconShowerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
    }

    public boolean wash(List<Player> under) {
        boolean any = false;
        for (Player p : under) {
            if (tank.getFluidAmount() < USE) break;
            tank.drain(USE, IFluidHandler.FluidAction.EXECUTE);
            Contamination.wash(p);
            if (p.isOnFire()) p.clearFire();
            any = true;
        }
        return any;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (!enabled) tag.putBoolean("disabled", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("tank"));
        enabled = !tag.getBoolean("disabled");
    }
}
