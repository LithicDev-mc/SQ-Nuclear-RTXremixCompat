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

public class DataConsoleMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public static final int MAX_COMMANDS_PER_SECOND = 10;
    private long window = -1;
    private int commands;

    public boolean allowCommand(long gameTime) {
        long w = gameTime / 20;
        if (w != window) {
            window = w;
            commands = 0;
        }
        return ++commands <= MAX_COMMANDS_PER_SECOND;
    }

    public DataConsoleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos());
    }

    public DataConsoleMenu(int id, BlockPos pos) {
        super(DataRegistry.CONSOLE_MENU.get(), id);
        this.pos = pos;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "console");
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new DataConsoleMenu(id, pos),
                Component.translatable("gui.nuclearsq.data.console.title")), buf -> buf.writeBlockPos(pos));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return (player.level().getBlockState(pos).getBlock() instanceof DataConsoleBlock || ControlDesk.at(player.level(), pos) != null)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
