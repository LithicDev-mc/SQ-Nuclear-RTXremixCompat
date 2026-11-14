package com.sqc.nuclearsq.data;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AreaSensorBlock extends AreaMonitorBlock {
    public static final MapCodec<AreaSensorBlock> CODEC = simpleCodec(AreaSensorBlock::new);

    public AreaSensorBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Component status(AreaMonitorBlockEntity be) {
        if (!(be instanceof AreaSensorBlockEntity s)) return super.status(be);
        return Component.translatable("message.nuclearsq.area_sensor.status", s.radius(), RadiationManager.fmtRate(s.rate()),
                RadiationManager.fmtRate(s.average()), RadiationManager.fmtRate(s.peak()), RadiationManager.fmtRate(s.threshold()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AreaSensorBlockEntity(pos, state);
    }
}
