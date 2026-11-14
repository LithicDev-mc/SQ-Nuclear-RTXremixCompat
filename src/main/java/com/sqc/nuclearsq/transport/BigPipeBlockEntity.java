package com.sqc.nuclearsq.transport;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BigPipeBlockEntity extends BlockEntity {
    public static final int RATE = 640, CAPACITY = 2000;

    private final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int recv, recvPrev;

    public BigPipeBlockEntity(BlockPos pos, BlockState state) {
        super(TransportRegistry.BIG_PIPE_BE.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    int receive(Direction from, FluidStack stack) {
        int n = tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        if (n > 0) recv |= 1 << from.ordinal();
        return n;
    }

    void serverTick() {
        if (level == null) return;
        BlockState state = getBlockState();
        Direction.Axis axis = state.getValue(BigPipeBlock.AXIS);
        int quad = state.getValue(BigPipeBlock.QUAD);
        Direction[] ends = {Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE),
                Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)};
        int blocked = recv | recvPrev;
        for (Direction d : ends) {
            if (tank.getSpace() <= 0) break;
            IFluidHandler h = portAt(worldPosition.relative(d));
            if (h == null) continue;
            FluidStack got = h.drain(Math.min(RATE, tank.getSpace()), IFluidHandler.FluidAction.EXECUTE);
            if (!got.isEmpty()) {
                tank.fill(got, IFluidHandler.FluidAction.EXECUTE);
                recv |= 1 << d.ordinal();
                blocked |= 1 << d.ordinal();
            }
        }
        for (Direction d : ends) {
            if (tank.isEmpty()) break;
            if ((blocked & 1 << d.ordinal()) != 0) continue;
            BlockPos n = worldPosition.relative(d);
            FluidStack offer = tank.drain(Math.min(RATE, tank.getFluidAmount()), IFluidHandler.FluidAction.SIMULATE);
            if (offer.isEmpty()) break;
            int moved = 0;
            IFluidHandler h = portAt(n);
            if (h != null) {
                moved = h.fill(offer, IFluidHandler.FluidAction.EXECUTE);
            } else if (level.getBlockEntity(n) instanceof BigPipeBlockEntity p) {
                BlockState ps = p.getBlockState();
                if (ps.getValue(BigPipeBlock.AXIS) == axis && ps.getValue(BigPipeBlock.QUAD) == quad) moved = p.receive(d.getOpposite(), offer);
            }
            if (moved > 0) tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
        }
        recvPrev = recv;
        recv = 0;
    }

    private IFluidHandler portAt(BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BigPortBlockEntity pe) || pe.kind() != BigPortKind.BIG_WATER) return null;
        BigMachineBlockEntity c = pe.findController();
        return c == null ? null : c.fluidsAt(pe.portName());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("tank"));
    }
}
