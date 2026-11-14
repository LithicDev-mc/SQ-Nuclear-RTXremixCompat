package com.sqc.nuclearsq.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class DataMultiMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public DataMultiMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos());
    }

    public DataMultiMenu(int id, BlockPos pos) {
        super(DataRegistry.MULTI_MENU.get(), id);
        this.pos = pos;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new DataMultiMenu(id, pos),
                Component.translatable("block.nuclearsq.data_multi_sqc")), buf -> buf.writeBlockPos(pos));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= 2 + 2 * DataMultiBlockEntity.ROWS
                || !(player.level().getBlockEntity(pos) instanceof DataMultiBlockEntity be)) return false;
        int dir = id % 2 == 0 ? -1 : 1;
        if (id < 2) be.cycleSource(dir);
        else be.cycleKey(id / 2 - 1, dir);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof DataMultiBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
