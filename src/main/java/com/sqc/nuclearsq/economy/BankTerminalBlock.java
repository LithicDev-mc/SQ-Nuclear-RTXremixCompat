package com.sqc.nuclearsq.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class BankTerminalBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BankTerminalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankTerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof BankTerminalBlockEntity t && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!player.isShiftKeyDown() && t.confirmPending(sp)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.bank_terminal.confirmed"), true);
                return InteractionResult.CONSUME;
            }
            if (player.isShiftKeyDown()) {
                t.logout();
                sp.displayClientMessage(Component.translatable("message.nuclearsq.bank_terminal.logout"), true);
            } else {
                if (!t.login(sp)) {
                    sp.displayClientMessage(Component.translatable("message.nuclearsq.bank_terminal.busy"), true);
                    return InteractionResult.CONSUME;
                }
                sp.openMenu(t);
                BankNet.Link link = BankNet.link(level, pos);
                sp.displayClientMessage(Component.translatable(link == BankNet.Link.ONLINE ? "message.nuclearsq.bank_terminal.online"
                        : "message.nuclearsq.bank_terminal.offline." + link.id()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BankTerminalBlockEntity t) {
            Containers.dropContents(level, pos, t);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
