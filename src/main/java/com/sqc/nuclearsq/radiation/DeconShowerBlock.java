package com.sqc.nuclearsq.radiation;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class DeconShowerBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(3, 6.5, 3, 13, 16, 13);

    public DeconShowerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.WATER_BUCKET)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof DeconShowerBlockEntity be)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            int n = be.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE);
            if (n < 1000) return ItemInteractionResult.FAIL;
            be.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DeconShowerBlockEntity be) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.shower.tank", be.tank().getFluidAmount(),
                    be.tank().getCapacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static final int FOG_PER_TICK = 6;

    public static int fogParticles(boolean active, RandomSource r) {
        return active ? 1 + r.nextInt(FOG_PER_TICK) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        int n = fogParticles(state.getValue(ACTIVE), r);
        for (int i = 0; i < n; i++) {
            if (i < 2) {
                level.addParticle(ParticleTypes.FALLING_WATER, pos.getX() + 0.25 + r.nextDouble() * 0.5, pos.getY() + 0.38,
                        pos.getZ() + 0.25 + r.nextDouble() * 0.5, 0, 0, 0);
                continue;
            }
            double x = pos.getX() - 0.2 + r.nextDouble() * 1.4;
            double z = pos.getZ() - 0.2 + r.nextDouble() * 1.4;
            double y = pos.getY() - DeconShowerBlockEntity.REACH + 0.1 + r.nextDouble() * (DeconShowerBlockEntity.REACH - 0.2);
            double vx = (x - pos.getX() - 0.5) * 0.012;
            double vz = (z - pos.getZ() - 0.5) * 0.012;
            double vy = -0.004 - r.nextDouble() * 0.01;
            int k = r.nextInt(10);
            if (k < 5) {
                level.addParticle(ParticleTypes.CLOUD, x, y, z, vx, vy, vz);
            } else if (k < 8) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, vx, vy * 0.5, vz);
            } else if (k < 9) {
                level.addParticle(ParticleTypes.POOF, x, y, z, vx * 0.5, vy, vz * 0.5);
            } else {
                level.addParticle(ParticleTypes.SPLASH, x, pos.getY() - DeconShowerBlockEntity.REACH + 0.05, z, 0, 0, 0);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeconShowerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ProtectionRegistry.SHOWER_BE.get()) return null;
        return (lvl, pos, st, be) -> ((DeconShowerBlockEntity) be).serverTick();
    }
}
