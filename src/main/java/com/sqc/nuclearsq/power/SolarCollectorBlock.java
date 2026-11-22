package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.registry.PowerRegistry;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SolarCollectorBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(5, 0, 0, 11, 4, 16),
            Block.box(0, 0, 5, 16, 4, 11), Block.box(7, 4, 7, 9, 10, 9), Block.box(0, 10, 0, 16, 12, 16));
    public static final IntegerProperty TILT = IntegerProperty.create("tilt", 0, 4);

    public SolarCollectorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(TILT, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TILT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarCollectorBlockEntity be) {
            int d = be.distance();
            Component route = d >= SolarCollectorBlockEntity.NO_ROUTE ? Component.translatable("message.nuclearsq.solar_collector.no_route")
                    : d == 0 ? Component.translatable("message.nuclearsq.solar_collector.direct")
                    : Component.translatable("message.nuclearsq.solar_collector.route", d);
            player.displayClientMessage(Component.translatable("message.nuclearsq.solar_collector.status", be.rate(), be.peak(), be.delivered(), route, be.stored(), be.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.nuclearsq.solar_collector_sqc.tip", SolarCollectorBlockEntity.peakPerTick())
                .withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarCollectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != PowerRegistry.SOLAR_COLLECTOR_BE.get()) return null;
        return (lvl, pos, st, be) -> ((SolarCollectorBlockEntity) be).serverTick();
    }
}
