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

public class PowerMonitorMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final List<PowerSample> fine = new ArrayList<>();
    public final List<PowerSample> coarse = new ArrayList<>();
    public PowerSummary summary = PowerSummary.EMPTY;
    @Nullable
    private final ServerPlayer player;
    private long sentFine, sentCoarse = -1;

    public PowerMonitorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), null);
    }

    public PowerMonitorMenu(int id, BlockPos pos, @Nullable ServerPlayer player) {
        super(DataRegistry.POWER_MENU.get(), id);
        this.pos = pos;
        this.player = player;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        if (!(sp.level().getBlockEntity(pos) instanceof PowerMonitorBlockEntity be)) return;
        be.scan();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new PowerMonitorMenu(id, pos, sp),
                Component.translatable("gui.nuclearsq.power.title")), buf -> buf.writeBlockPos(pos));
    }

    public void receive(PowerPayload p) {
        fine.addAll(p.fine());
        while (fine.size() > PowerMonitorBlockEntity.FINE_MAX) fine.remove(0);
        if (p.replaceCoarse()) {
            coarse.clear();
            coarse.addAll(p.coarse());
        }
        summary = p.summary();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player == null || !(player.level().getBlockEntity(pos) instanceof PowerMonitorBlockEntity be)) return;
        if (be.fineCount() == sentFine && be.coarseCount() == sentCoarse) return;
        boolean replace = be.coarseCount() != sentCoarse;
        List<PowerSample> fresh = be.fineSince(sentFine);
        sentFine = be.fineCount();
        sentCoarse = be.coarseCount();
        PacketDistributor.sendToPlayer(player, new PowerPayload(containerId, fresh, replace, replace ? new ArrayList<>(be.coarse()) : List.of(), be.summary()));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof PowerMonitorBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
