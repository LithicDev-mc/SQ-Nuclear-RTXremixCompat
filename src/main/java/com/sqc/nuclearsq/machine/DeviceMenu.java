package com.sqc.nuclearsq.machine;

import com.sqc.nuclearsq.machine.client.DeviceScreen;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class DeviceMenu extends AbstractContainerMenu {
    public static final int ROD_DRIVE = 4, BIG = 5, BIG_BATTERY = 7, POWER_GATE = 8, DOOR = 9, AIRLOCK = 10;
    public static final int ROD_VESSEL = 12 + 4 * 9;

    public final BlockPos pos;
    public final int kind;
    private final ContainerData data;

    public DeviceMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readVarInt());
    }

    private DeviceMenu(int id, Inventory inv, BlockPos pos, int kind) {
        this(id, inv, pos, kind, new SimpleContainerData(count(kind)));
    }

    public DeviceMenu(int id, Inventory inv, BlockPos pos, int kind, ContainerData data) {
        super(MultiblockRegistry.DEVICE_MENU.get(), id);
        this.pos = pos;
        this.kind = kind;
        this.data = data;
        addDataSlots(data);
    }

    public static int count(int kind) {
        return switch (kind) {
            case DOOR -> com.sqc.nuclearsq.door.DoorBlockEntity.DATA;
            case AIRLOCK -> com.sqc.nuclearsq.door.AirlockBlockEntity.DATA;
            case POWER_GATE -> com.sqc.nuclearsq.transport.PowerGateBlockEntity.DATA;
            case BIG_BATTERY -> com.sqc.nuclearsq.machine.big.BigBattery.DATA;
            case ROD_DRIVE -> ROD_VESSEL + 8 + 8;
            case BIG -> com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.TERM_PORTS
                    + com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.PORT_VALUES * com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.MAX_TERM_PORTS;
            default -> 7;
        };
    }

    public static void open(ServerPlayer sp, BlockPos pos, int kind, ContainerData data, Component title) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new DeviceMenu(id, inv, pos, kind, data), title),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeVarInt(kind);
                });
    }

    public int u(int i) { return data.get(i) & 0xFFFF; }

    public int i32(int lo) { return u(lo) | (u(lo + 1) << 16); }

    public static int lo(int v) { return v & 0xFFFF; }
    public static int hi(int v) { return v >>> 16; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return com.sqc.nuclearsq.data.RemoteAccess.inReach(player, pos);
    }
}
