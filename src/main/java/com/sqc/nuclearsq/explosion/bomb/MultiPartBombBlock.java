package com.sqc.nuclearsq.explosion.bomb;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;


import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class MultiPartBombBlock extends NuclearBombBlock {
    private final int width, height, partCount;
    private final VoxelShape[] shapes;

    protected MultiPartBombBlock(Properties properties, int width, int height, double[][] boxes) {
        super(properties);
        this.width = width;
        this.height = height;
        this.partCount = width * width * height;
        this.shapes = new VoxelShape[partCount];
        for (int i = 0; i < partCount; i++) {
            double ox = dx(i) * 16, oy = dy(i) * 16, oz = dz(i) * 16;
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double x0 = Math.max(b[0], ox), y0 = Math.max(b[1], oy), z0 = Math.max(b[2], oz);
                double x1 = Math.min(b[3], ox + 16), y1 = Math.min(b[4], oy + 16), z1 = Math.min(b[5], oz + 16);
                if (x0 < x1 && y0 < y1 && z0 < z1) shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz));
            }
            shapes[i] = shape.optimize();
        }
        registerDefaultState(defaultBlockState().setValue(partProperty(), 0));
    }

    public abstract IntegerProperty partProperty();

    public abstract int[] modelParts();

    public int partCount() { return partCount; }
    public int height() { return height; }
    public int dx(int part) { return part % width; }
    public int dz(int part) { return (part / width) % width; }
    public int dy(int part) { return part / (width * width); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(partProperty());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[state.getValue(partProperty())];
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public BlockPos core(BlockPos pos, BlockState state) {
        int part = state.getValue(partProperty());
        return pos.offset(-dx(part), -dy(part), -dz(part));
    }

    @Override
    public List<BlockPos> parts(BlockPos core) {
        List<BlockPos> list = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) list.add(core.offset(dx(i), dy(i), dz(i)));
        return list;
    }

    @Override
    public List<BlockPos> bottom(BlockPos core) {
        return parts(core).subList(0, width * width);
    }

    @Override
    public BlockState partState(BlockState like, int index) {
        return like.setValue(partProperty(), index);
    }

    @Override
    public boolean isCore(BlockState state) {
        return state.getValue(partProperty()) == 0;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos core = ctx.getClickedPos();
        for (BlockPos p : parts(core)) {
            if (p.equals(core)) continue;
            if (ctx.getLevel().isOutsideBuildHeight(p) || !ctx.getLevel().getBlockState(p).canBeReplaced(ctx)) return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        List<BlockPos> parts = parts(pos);
        for (int i = 1; i < partCount; i++) level.setBlock(parts.get(i), partState(state, i), Block.UPDATE_ALL);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (moved || level.isClientSide || newState.is(this)) return;
        BlockPos core = core(pos, state);
        List<BlockPos> parts = parts(core);
        for (int i = 0; i < partCount; i++) {
            BlockPos p = parts.get(i);
            BlockState st = level.getBlockState(p);
            if (!p.equals(pos) && st.is(this) && st.getValue(partProperty()) == i) level.removeBlock(p, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && !isCore(state)) popResource(level, pos, new ItemStack(this));
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion,
                                  java.util.function.BiConsumer<ItemStack, BlockPos> drops) {
        if (!isCore(state) && level instanceof net.minecraft.server.level.ServerLevel sl && state.canDropFromExplosion(level, pos, explosion)
                && explosion.getBlockInteraction() != net.minecraft.world.level.Explosion.BlockInteraction.TRIGGER_BLOCK) {
            BlockPos core = core(pos, state);
            BlockState cs = level.getBlockState(core);
            if (cs.is(this) && isCore(cs)) {
                var b = new net.minecraft.world.level.storage.loot.LootParams.Builder(sl)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(core))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, level.getBlockEntity(core))
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
                if (explosion.getBlockInteraction() == net.minecraft.world.level.Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
                    b.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, explosion.radius());
                }
                cs.getDrops(b).forEach(s -> drops.accept(s, core));
            }
        }
        super.onExplosionHit(state, level, pos, explosion, drops);
    }

}
