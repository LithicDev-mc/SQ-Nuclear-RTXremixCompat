package com.sqc.nuclearsq.transport;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GridNodeBlockEntity extends BlockEntity implements GridNode {
    private final List<BlockPos> links = new ArrayList<>();

    public GridNodeBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.NODE_BE.get(), pos, state);
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return links;
    }

    @Override
    public List<Vec3> attachPoints() {
        BlockState st = getBlockState();
        return st.getBlock() instanceof PowerMastBlock m ? m.attach(worldPosition, st) : List.of();
    }

    @Nullable
    @Override
    public Vec3 earthPoint() {
        BlockState st = getBlockState();
        return st.getBlock() instanceof PowerMastBlock m ? m.earth(worldPosition) : null;
    }

    @Override
    public int maxSpan() {
        return getBlockState().getBlock() instanceof PowerMastBlock m ? m.span() : MAX_SPAN;
    }

    public int unlinkAll() {
        return level == null ? 0 : GridNode.unlinkAll(level, this);
    }

    @Override
    public boolean addLink(BlockPos p) {
        if (links.contains(p)) return false;
        links.add(p.immutable());
        changed();
        return true;
    }

    @Override
    public boolean removeLink(BlockPos p) {
        if (!links.remove(p)) return false;
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        links.clear();
        for (long l : tag.getLongArray("Links")) links.add(BlockPos.of(l));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
