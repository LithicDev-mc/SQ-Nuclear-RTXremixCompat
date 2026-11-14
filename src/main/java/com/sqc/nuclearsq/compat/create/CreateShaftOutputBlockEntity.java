package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sqc.nuclearsq.turbine.ShaftConsumer;
import com.sqc.nuclearsq.turbine.ShaftValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CreateShaftOutputBlockEntity extends GeneratingKineticBlockEntity implements ShaftConsumer {
    private static final int WINDOW = 20;

    private int feSum, ticks;
    private float lastSpeed;
    private int rpm, su;

    public CreateShaftOutputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateShaftRegistry.OUTPUT_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    private Direction facing() {
        return getBlockState().getValue(DirectionalKineticBlock.FACING);
    }

    @Override
    public boolean direct() {
        return true;
    }

    @Override
    public int acceptShaft(BlockPos coupling, int fe, float speed, boolean simulate) {
        if (fe <= 0 || !coupling.equals(worldPosition.relative(facing().getOpposite()))) return 0;
        if (!simulate) {
            feSum += fe;
            lastSpeed = Math.max(lastSpeed, speed);
        }
        return fe;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (++ticks < WINDOW) return;
        int avgFe = feSum / WINDOW;
        int step = ShaftValues.createRpmStep();
        int newRpm = avgFe <= 0 ? 0
                : Math.max(step, Math.round(lastSpeed * ShaftValues.createMaxRpm() / step) * step);
        newRpm = Math.min(newRpm, ShaftValues.createMaxRpm());
        int newSu = newRpm == 0 ? 0 : avgFe * ShaftValues.createSuPerFe();
        feSum = 0;
        ticks = 0;
        lastSpeed = 0;
        if (newRpm != rpm || newSu != su) {
            rpm = newRpm;
            su = newSu;
            updateGeneratedRotation();
            setChanged();
        }
    }

    public int rpm() { return rpm; }

    public int su() { return su; }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(rpm, facing());
    }

    @Override
    public float calculateAddedStressCapacity() {
        float cap = rpm == 0 ? 0 : su / (float) rpm;
        lastCapacityProvided = cap;
        return cap;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("ShaftRpm", rpm);
        tag.putInt("ShaftSu", su);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rpm = tag.getInt("ShaftRpm");
        su = tag.getInt("ShaftSu");
    }
}
