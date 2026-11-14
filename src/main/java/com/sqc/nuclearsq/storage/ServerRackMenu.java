package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ServerRackMenu extends AbstractContainerMenu {
    public static final int WIDTH = 196, DATA = 4;
    public final BlockPos pos;
    public final int driveSlots, cols, rows;
    public final boolean cards;
    private final ContainerData data;

    public ServerRackMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readVarInt(), buf.readBoolean());
    }

    private ServerRackMenu(int id, Inventory inv, BlockPos pos, int slots, boolean cards) {
        this(id, inv, pos, new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return cards ? PatternCardItem.pattern(stack) != null : stack.getItem() instanceof DriveItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        }, new SimpleContainerData(DATA), cards);
    }

    public ServerRackMenu(int id, Inventory inv, BlockPos pos, IItemHandler drives, ContainerData data) {
        this(id, inv, pos, drives, data, false);
    }

    public ServerRackMenu(int id, Inventory inv, BlockPos pos, IItemHandler drives, ContainerData data, boolean cards) {
        super(StorageRegistry.RACK_MENU.get(), id);
        this.cards = cards;
        this.pos = pos;
        this.data = data;
        driveSlots = drives.getSlots();
        cols = driveSlots <= 12 ? 6 : 10;
        rows = (driveSlots + cols - 1) / cols;
        int x0 = (WIDTH - cols * 18) / 2 + 1;
        for (int i = 0; i < driveSlots; i++) addSlot(new SlotItemHandler(drives, i, x0 + (i % cols) * 18, 20 + (i / cols) * 18));
        int py = invY(), px = (WIDTH - 162) / 2 + 1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, py + 58));
        addDataSlots(data);
    }

    public int invY() {
        return 20 + rows * 18 + 56;
    }

    public int height() {
        return invY() + 82;
    }

    public int priority() { return data.get(0); }
    public boolean online() { return data.get(1) != 0; }
    public int powerUse() { return data.get(2); }
    public int driveCount() { return data.get(3); }

    public ServerRack.Space space() {
        return ServerRack.space(driveSlots, i -> slots.get(i).getItem());
    }

    public ServerRack.Compute compute() {
        return ServerRack.compute(driveSlots, i -> slots.get(i).getItem());
    }

    public static ContainerData data(BigMachineBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                ServerRack r = be.rack();
                if (r == null) return 0;
                return switch (i) {
                    case 0 -> r.priority();
                    case 1 -> r.online() ? 1 : 0;
                    case 2 -> r.powerUse();
                    case 3 -> {
                        int n = 0;
                        for (int s = 0; s < r.slots(); s++) if (!r.drives().getStackInSlot(s).isEmpty()) n++;
                        yield n;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() { return DATA; }
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be) || be.rack() == null) return false;
        ServerRack r = be.rack();
        if (id == 0) r.setPriority(r.priority() - 1);
        else if (id == 1) r.setPriority(r.priority() + 1);
        else return false;
        be.setChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < driveSlots) {
            if (!moveItemStackTo(stack, driveSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!(cards ? PatternCardItem.pattern(stack) != null : stack.getItem() instanceof DriveItem)
                || !moveItemStackTo(stack, 0, driveSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be && be.rack() != null
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 24 * 24;
    }
}
