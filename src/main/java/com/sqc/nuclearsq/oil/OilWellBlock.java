package com.sqc.nuclearsq.oil;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class OilWellBlock extends Block implements EntityBlock {
    public static final BooleanProperty CORE = BooleanProperty.create("core");

    public OilWellBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(CORE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CORE) ? new OilWellBlockEntity(pos, state) : null;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(CORE) && !player.getAbilities().instabuild ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(CORE)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof OilWellBlockEntity be) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.oil_well",
                    Component.translatable("oil_class.nuclearsq." + be.oilClass(level).id),
                    String.format("%,d", be.amount(level)), String.format("%,d", be.max(level))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
