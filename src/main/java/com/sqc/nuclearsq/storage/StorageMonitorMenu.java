package com.sqc.nuclearsq.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class StorageMonitorMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public StorageMonitorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos());
    }

    public StorageMonitorMenu(int id, BlockPos pos) {
        super(StorageRegistry.MONITOR_MENU.get(), id);
        this.pos = pos;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        if (!(sp.level().getBlockEntity(pos) instanceof StorageMonitorBlockEntity)) return;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new StorageMonitorMenu(id, pos),
                Component.translatable("block.nuclearsq.storage_monitor_sqc")), buf -> buf.writeBlockPos(pos));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return player.level().getBlockEntity(pos) instanceof StorageMonitorBlockEntity be && be.apply(id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof StorageMonitorBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
