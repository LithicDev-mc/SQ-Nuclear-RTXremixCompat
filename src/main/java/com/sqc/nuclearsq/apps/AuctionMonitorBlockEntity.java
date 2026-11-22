package com.sqc.nuclearsq.apps;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.economy.Auctions;
import com.sqc.nuclearsq.economy.BankNet;
import com.sqc.nuclearsq.economy.EconomyData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AuctionMonitorBlockEntity extends BlockEntity {
    private static final int PERIOD = 40, ROWS = 3;

    public record Row(ItemStack stack, long price, long left) {}

    private int link = BankNet.Link.NO_SERVER.ordinal();
    private int count;
    private List<Row> rows = new ArrayList<>();

    public AuctionMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(AppRegistry.AUCTION_MONITOR_BE.get(), pos, state);
    }

    public BankNet.Link link() {
        BankNet.Link[] all = BankNet.Link.values();
        return all[Math.max(0, Math.min(all.length - 1, link))];
    }

    public int count() {
        return count;
    }

    public List<Row> rows() {
        return rows;
    }

    public void serverTick() {
        if (level == null || level.getServer() == null || level.getGameTime() % PERIOD != Math.floorMod(worldPosition.asLong(), PERIOD)) return;
        int l = BankNet.link(level, worldPosition).ordinal();
        Auctions au = EconomyData.get(level.getServer()).auctions();
        long now = level.getServer().overworld().getGameTime();
        List<Row> r = new ArrayList<>();
        for (Auctions.Auction a : au.list()) {
            if (r.size() >= ROWS) break;
            r.add(new Row(a.stack.copyWithCount(1), a.bidder == null ? a.start : a.bid, Math.max(0, a.end - now)));
        }
        boolean same = l == link && au.count() == count && r.size() == rows.size();
        for (int i = 0; same && i < r.size(); i++) {
            same = r.get(i).price() == rows.get(i).price() && ItemStack.isSameItemSameComponents(r.get(i).stack(), rows.get(i).stack())
                    && r.get(i).left() / 1200 == rows.get(i).left() / 1200;
        }
        if (same) return;
        link = l;
        count = au.count();
        rows = r;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("link", link);
        tag.putInt("count", count);
        ListTag list = new ListTag();
        for (Row r : rows) {
            CompoundTag t = new CompoundTag();
            t.put("s", r.stack().save(registries));
            t.putLong("p", r.price());
            t.putLong("l", r.left());
            list.add(t);
        }
        tag.put("rows", list);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries);
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
        count = tag.getInt("count");
        List<Row> r = new ArrayList<>();
        for (Tag e : tag.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ItemStack s = ItemStack.parse(registries, t.get("s")).orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) r.add(new Row(s, t.getLong("p"), t.getLong("l")));
        }
        rows = r;
    }
}
