package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.turbine.TurbinePartBlock;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BigPortBlock extends TurbinePartBlock implements EntityBlock {
    private final BigPortKind kind;

    public BigPortBlock(Properties properties, BigPortKind kind) {
        super(properties);
        this.kind = kind;
    }

    public BigPortKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigPortBlockEntity(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == BigPortKind.SIGNAL;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == BigPortKind.SIGNAL && level.getBlockEntity(pos) instanceof BigPortBlockEntity be ? be.signal() : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return kind == BigPortKind.TERMINAL;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BigMachineBlockEntity c = level.getBlockEntity(pos) instanceof BigPortBlockEntity be ? be.findController() : null;
        return c == null ? 0 : c.comparator();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigPortBlockEntity be) {
            BigMachineBlockEntity c = kind == BigPortKind.TERMINAL ? be.findController() : null;
            if (c != null && player instanceof net.minecraft.server.level.ServerPlayer sp) c.use(sp);
            else be.sendInfo(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (kind == BigPortKind.ITEM && com.sqc.nuclearsq.spent.SpentFuel.isWaste(stack) && level.getBlockEntity(pos) instanceof BigPortBlockEntity pb) {
            BigMachineBlockEntity c = pb.findController();
            if (c != null && (c.machine() == BigMachine.WASTE_REPOSITORY
                    || c.machine() == BigMachine.SPENT_FUEL_RACK && com.sqc.nuclearsq.spent.SpentFuel.isSpentRod(stack))) {
                if (!level.isClientSide) c.acceptHeld(player, hand);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (kind.isFluid() && net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack).isPresent() && level.getBlockEntity(pos) instanceof BigPortBlockEntity pb) {
            BigMachineBlockEntity c = pb.findController();
            if (c != null && c.machine() == BigMachine.FLUID_BUFFER) {
                if (!level.isClientSide) c.acceptHeld(player, hand);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
}
