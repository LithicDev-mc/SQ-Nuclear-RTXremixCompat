package com.sqc.nuclearsq.explosion.bomb;

import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.compat.sable.SableCompat;
import com.sqc.nuclearsq.explosion.NuclearExplosion;
import com.sqc.nuclearsq.registry.BombRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NuclearBombBlock extends Block implements EntityBlock {
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 3, 11),
            Block.box(4, 3, 4, 12, 12, 12),
            Block.box(5, 12, 5, 11, 16, 11));

    public NuclearBombBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ARMED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    public NuclearExplosion.Blast blast() {
        return NuclearExplosion.Blast.bomb();
    }

    public int width() {
        return 1;
    }

    public BlockPos core(BlockPos pos, BlockState state) {
        return pos;
    }

    public List<BlockPos> parts(BlockPos core) {
        return List.of(core);
    }

    public List<BlockPos> bottom(BlockPos core) {
        return List.of(core);
    }

    public BlockState partState(BlockState like, int index) {
        return like;
    }

    public boolean isCore(BlockState state) {
        return true;
    }

    public Vec3 bottomCenter(BlockPos core) {
        double half = width() / 2.0;
        return new Vec3(core.getX() + half, core.getY(), core.getZ() + half);
    }

    public BlockPos coreAt(Vec3 bottomCenter) {
        double half = width() / 2.0;
        return BlockPos.containing(bottomCenter.x - half + 0.5, bottomCenter.y + 0.5, bottomCenter.z - half + 0.5);
    }

    public boolean fits(Level level, BlockPos core) {
        for (BlockPos p : parts(core)) {
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) return false;
        }
        return true;
    }

    public void placeParts(Level level, BlockPos core, BlockState coreState, int flags) {
        List<BlockPos> parts = parts(core);
        for (int i = 0; i < parts.size(); i++) level.setBlock(parts.get(i), partState(coreState, i), flags);
    }

    public void removeParts(Level level, BlockPos core) {
        for (BlockPos p : parts(core)) {
            if (level.getBlockState(p).is(this)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
        }
    }

    @Nullable
    public static NuclearBombBlock of(BlockState state) {
        return state.getBlock() instanceof NuclearBombBlock b ? b : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockPos core = core(pos, state);
            BlockState coreState = level.getBlockState(core);
            if (coreState.is(this)) setArmed(level, core, coreState, !coreState.getValue(ARMED), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        if (level.isClientSide) return;
        boolean power = level.hasNeighborSignal(pos);
        if (power != state.getValue(POWERED)) {
            state = state.setValue(POWERED, power);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            BlockPos core = core(pos, state);
            BlockState coreState = level.getBlockState(core);
            if (power && coreState.is(this) && !coreState.getValue(ARMED)) setArmed(level, core, coreState, true, null);
        }
        level.scheduleTick(core(pos, state), this, 2);
    }

    public void setArmed(Level level, BlockPos core, BlockState coreState, boolean armed, @Nullable Player player) {
        if (armed && !NuclearConfig.BOMBS_ENABLED.get()) {
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.bombs_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        for (BlockPos p : parts(core)) {
            BlockState st = level.getBlockState(p);
            if (st.is(this)) level.setBlock(p, st.setValue(ARMED, armed), Block.UPDATE_ALL);
        }
        level.playSound(null, core, armed ? SoundEvents.TNT_PRIMED : SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, armed ? 0.6f : 1.0f);
        if (level.getBlockEntity(core) instanceof NuclearBombBlockEntity be) be.resetFuse();
        if (player != null) {
            Component msg = armed
                    ? Component.translatable("message.nuclearsq.bomb_armed", NuclearConfig.fuseSeconds())
                    : Component.translatable("message.nuclearsq.bomb_disarmed");
            player.displayClientMessage(msg.copy().withStyle(armed ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        }
        if (armed) level.scheduleTick(core, this, 2);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (state.getValue(ARMED) && isCore(state)) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && state.getValue(ARMED)) level.scheduleTick(core(pos, state), this, 2);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isCore(state)) tryRelease(level, pos, state);
    }

    public static boolean tryRelease(ServerLevel level, BlockPos pos, BlockState state) {
        NuclearBombBlock bomb = of(state);
        if (bomb == null) return false;
        BlockPos core = bomb.core(pos, state);
        BlockState coreState = level.getBlockState(core);
        if (!coreState.is(bomb) || !coreState.getValue(ARMED) || core.getY() <= level.getMinBuildHeight()) return false;
        if (!(level.getBlockEntity(core) instanceof NuclearBombBlockEntity be) || !be.fuseDone() || be.isOwnBody()) return false;
        boolean onShip = SableCompat.isOnSubLevel(level, core);
        boolean free = true;
        for (BlockPos p : bomb.bottom(core)) free &= FallingBlock.isFree(level.getBlockState(p.below()));
        if (SableCompat.hasPhysics() && (free || !onShip) && SableCompat.makePhysical(level, core, bomb.parts(core), Vec3.ZERO)) return true;
        if (!free) return false;
        Vec3 at = onShip ? SableCompat.toWorld(level, bomb.bottomCenter(core)) : bomb.bottomCenter(core);
        Vec3 motion = onShip ? be.worldMotion() : Vec3.ZERO;
        bomb.removeParts(level, core);
        FallingBombEntity.drop(level, at, coreState, motion);
        return true;
    }

    public static void launch(ServerLevel level, Vec3 bottomCenter, BlockState state, Vec3 motion) {
        NuclearBombBlock bomb = of(state);
        if (bomb == null) return;
        BlockState armed = state.setValue(ARMED, true);
        BlockPos core = bomb.coreAt(bottomCenter);
        if (SableCompat.hasPhysics() && bomb.fits(level, core)) {
            bomb.placeParts(level, core, armed, Block.UPDATE_ALL);
            if (level.getBlockEntity(core) instanceof NuclearBombBlockEntity be) be.finishFuse();
            if (SableCompat.makePhysical(level, core, bomb.parts(core), motion)) return;
            bomb.removeParts(level, core);
        }
        FallingBombEntity.drop(level, bottomCenter, armed, motion);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new NuclearBombBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BombRegistry.BOMB_BE.get()) return null;
        return (lvl, pos, st, be) -> ((NuclearBombBlockEntity) be).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(tooltipKey(1)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.bomb.2").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.bomb.3", NuclearConfig.fuseSeconds()).withStyle(ChatFormatting.DARK_RED));
    }

    protected String tooltipKey(int line) {
        return "tooltip.nuclearsq.bomb." + line;
    }
}
