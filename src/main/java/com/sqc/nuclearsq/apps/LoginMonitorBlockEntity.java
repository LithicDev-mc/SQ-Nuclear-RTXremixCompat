package com.sqc.nuclearsq.apps;

import com.sqc.nuclearsq.economy.BankNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LoginMonitorBlockEntity extends BlockEntity {
    private static final int PERIOD = 20;

    private int link = BankNet.Link.NO_SERVER.ordinal();

    public LoginMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(AppRegistry.LOGIN_MONITOR_BE.get(), pos, state);
    }

    public BankNet.Link link() {
        BankNet.Link[] all = BankNet.Link.values();
        return all[Math.max(0, Math.min(all.length - 1, link))];
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        int now = BankNet.link(level, worldPosition).ordinal();
        if (now != link) {
            link = now;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("link", link);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("link", link);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        link = tag.getInt("link");
    }
}
