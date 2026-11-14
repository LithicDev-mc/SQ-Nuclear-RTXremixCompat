package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LogbookMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final List<LogEntry> entries;
    @Nullable
    private final ServerPlayer player;
    private static final int CHUNK = 40;
    private long sentUpTo;

    public LogbookMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), new ArrayList<>(), null, 0);
    }

    public LogbookMenu(int id, BlockPos pos, List<LogEntry> entries, @Nullable ServerPlayer player, long sentUpTo) {
        super(DataRegistry.LOGBOOK_MENU.get(), id);
        this.pos = pos;
        this.entries = entries;
        this.player = player;
        this.sentUpTo = sentUpTo;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        if (!(sp.level().getBlockEntity(pos) instanceof LogbookBlockEntity be)) return;
        be.scan();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new LogbookMenu(id, pos, new ArrayList<>(), sp, 0),
                Component.translatable("gui.nuclearsq.logbook.title")), buf -> buf.writeBlockPos(pos));
    }

    public void append(List<LogEntry> fresh) {
        for (LogEntry e : fresh) {
            if (!entries.isEmpty() && e.seq() <= entries.get(entries.size() - 1).seq()) continue;
            entries.add(e);
        }
        while (entries.size() > LogbookBlockEntity.MAX_ENTRIES) entries.remove(0);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player == null || !(player.level().getBlockEntity(pos) instanceof LogbookBlockEntity be)) return;
        if (be.nextSeq() <= sentUpTo) return;
        List<LogEntry> fresh = be.since(sentUpTo);
        if (fresh.size() > CHUNK) fresh = new ArrayList<>(fresh.subList(0, CHUNK));
        if (fresh.isEmpty()) {
            sentUpTo = be.nextSeq();
            return;
        }
        sentUpTo = fresh.get(fresh.size() - 1).seq() + 1;
        PacketDistributor.sendToPlayer(player, new LogbookPayload(containerId, fresh));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof LogbookBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
