package com.sqc.nuclearsq.futurechest;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FutureChestMenu extends AbstractContainerMenu {
    public final int capacity, cols, rows;
    private final Container container, upgrades;

    public FutureChestMenu(int id, Inventory inv, Container container, Container upgrades, int capacity) {
        super(FutureChestRegistry.MENU.get(), id);
        checkContainerSize(container, capacity);
        checkContainerSize(upgrades, FutureChestBlockEntity.UPGRADE_SLOTS);
        this.container = container;
        this.upgrades = upgrades;
        this.capacity = capacity;
        this.cols = capacity >= 128 ? 16 : 8;
        this.rows = capacity / cols;
        container.startOpen(inv.player);
        for (int i = 0; i < capacity; i++) {
            addSlot(new Slot(container, i, 8 + (i % cols) * 18, 18 + (i / cols) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(getContainerSlot(), stack);
                }
            });
        }
        int ux = 8 + Math.max(cols * 18, 162) + 8;
        for (int i = 0; i < FutureChestBlockEntity.UPGRADE_SLOTS; i++) {
            final int kind = i;
            addSlot(new Slot(upgrades, i, ux, 18 + i * 22) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof QuantumUpgradeItem u && u.kind.ordinal() == kind;
                }

                @Override
                public int getMaxStackSize() {
                    return QuantumUpgradeItem.Kind.values()[kind].stack;
                }

                @Override
                public boolean mayPickup(Player p) {
                    return kind != FutureChestBlockEntity.SLOT_CAPACITY || !(FutureChestMenu.this.container instanceof FutureChestBlockEntity be)
                            || be.isEmptyFrom(FutureChestBlockEntity.BASE_SIZE);
                }
            });
        }
        int py = 18 + rows * 18 + 14, px = 8 + Math.max(0, (cols * 18 - 162) / 2);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, py + 58));
    }

    public int height() {
        return 18 + rows * 18 + 14 + 58 + 18 + 7;
    }

    public int width() {
        return 8 + Math.max(cols * 18, 162) + 8 + 18 + 8;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int upEnd = capacity + FutureChestBlockEntity.UPGRADE_SLOTS;
            if (index < upEnd) {
                if (!slot.mayPickup(player) || !moveItemStackTo(stack, upEnd, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, capacity, upEnd, false) && !moveItemStackTo(stack, 0, capacity, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
