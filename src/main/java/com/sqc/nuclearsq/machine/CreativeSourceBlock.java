package com.sqc.nuclearsq.machine;

import com.sqc.nuclearsq.registry.TestSourceRegistry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public class CreativeSourceBlock extends Block implements EntityBlock {
    private final boolean energy;

    public CreativeSourceBlock(Properties properties, boolean energy) {
        super(properties);
        this.energy = energy;
    }

    public boolean energy() {
        return energy;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeSourceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != TestSourceRegistry.SOURCE_BE.get()) return null;
        return (l, p, s, be) -> ((CreativeSourceBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (energy || stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        FluidStack fs = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        if (fs.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CreativeSourceBlockEntity be) {
            be.setFluid(fs.getFluid());
            player.sendSystemMessage(Component.translatable("message.nuclearsq.creative_source.fluid", fs.getHoverName()));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CreativeSourceBlockEntity be) {
            player.sendSystemMessage(energy
                    ? Component.translatable("message.nuclearsq.creative_source.energy")
                    : Component.translatable("message.nuclearsq.creative_source.fluid", new FluidStack(be.fluid(), 1).getHoverName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
