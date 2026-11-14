package com.sqc.nuclearsq.data;

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

public class DataTerminalMenu extends AbstractContainerMenu {
    public static final int REFRESH_TICKS = 20;

    public final BlockPos pos;
    public List<DataEntry> entries;
    public Summary summary;
    @Nullable
    private final ServerPlayer player;
    private int age;

    public record Summary(int cpu, int cpuUsed, int ram, int ramUsed) {
        public static Summary of(ComputeNet.Status s) {
            return new Summary(s.cpu(), s.cpuUsed(), s.ram(), s.ramUsed());
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(cpu);
            buf.writeVarInt(cpuUsed);
            buf.writeVarInt(ram);
            buf.writeVarInt(ramUsed);
        }

        static Summary read(RegistryFriendlyByteBuf buf) {
            return new Summary(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public DataTerminalMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), DataEntry.LIST_CODEC.decode(buf), Summary.read(buf), null);
    }

    public DataTerminalMenu(int id, BlockPos pos, List<DataEntry> entries, Summary summary, @Nullable ServerPlayer player) {
        super(DataRegistry.TERMINAL_MENU.get(), id);
        this.pos = pos;
        this.entries = entries;
        this.summary = summary;
        this.player = player;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        List<DataEntry> entries = DataTree.scan(sp.level(), pos);
        Summary summary = Summary.of(ComputeNet.status(sp.level(), pos));
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "data_terminal");
        if (!entries.isEmpty()) com.sqc.nuclearsq.progression.GuideProgress.award(sp, "data_terminal_net");
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new DataTerminalMenu(id, pos, entries, summary, sp),
                Component.translatable("gui.nuclearsq.data.terminal.title")), buf -> {
            buf.writeBlockPos(pos);
            DataEntry.LIST_CODEC.encode(buf, entries);
            summary.write(buf);
        });
    }

    public void refresh() {
        if (player == null) return;
        age = 0;
        entries = DataTree.scan(player.level(), pos);
        summary = Summary.of(ComputeNet.status(player.level(), pos));
        PacketDistributor.sendToPlayer(player, new DataPayloads.Tree(containerId, entries, summary));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player != null && ++age >= REFRESH_TICKS) refresh();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof DataDevice
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
