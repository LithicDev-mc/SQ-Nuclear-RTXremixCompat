package com.sqc.nuclearsq.crate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StorageCrateMenu extends AbstractContainerMenu {
    public static final int DATA = 7, BTN_EJECT = 12, PANEL = 62;
    public final int capacity, cols, rows;
    private final Container container;
    private final ContainerData data;
    private final Player player;

    public StorageCrateMenu(int id, Inventory inv, Container container, Container upgrade, ContainerData data, int capacity) {
        super(CrateRegistry.MENU.get(), id);
        checkContainerSize(container, capacity);
        checkContainerDataCount(data, DATA);
        this.container = container;
        this.data = data;
        this.player = inv.player;
        this.capacity = capacity;
        this.cols = capacity > 54 ? 18 : 9;
        this.rows = capacity / cols;
        container.startOpen(player);
        for (int i = 0; i < capacity; i++) {
            addSlot(new Slot(container, i, 8 + (i % cols) * 18, 18 + (i / cols) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(getContainerSlot(), stack);
                }
            });
        }
        addSlot(new Slot(upgrade, 0, panelX(), 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CrateUpgradeItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPickup(Player p) {
                return needed() <= StorageCrateBlockEntity.capacityFor(getCarried());
            }
        });
        int py = invY(), px = 8 + Math.max(0, (cols * 18 - 162) / 2);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, py + 58));
        addDataSlots(data);
    }

    public int panelX() {
        return 8 + Math.max(cols * 18, 162) + 8;
    }

    public int invY() {
        return 18 + Math.max(rows * 18, 92) + 14;
    }

    public int width() {
        return panelX() + PANEL + 8;
    }

    public int height() {
        return invY() + 58 + 18 + 7;
    }

    public int mode(int rel) {
        return data.get(rel);
    }

    public boolean eject() {
        return data.get(StorageCrateBlockEntity.SIDES) != 0;
    }

    int needed() {
        if (container instanceof StorageCrateBlockEntity be) return be.needed();
        for (int i = capacity - 1; i >= 0; i--) if (!container.getItem(i).isEmpty()) return i + 1;
        return 0;
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id >= 0 && id < 12) {
            int rel = id % 6;
            data.set(rel, Math.floorMod(data.get(rel) + (id < 6 ? 1 : -1), 4));
            return true;
        }
        if (id == BTN_EJECT) {
            data.set(StorageCrateBlockEntity.SIDES, eject() ? 0 : 1);
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (container instanceof StorageCrateBlockEntity be && be.capacity() != capacity && getCarried().isEmpty() && player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> buf.writeVarInt(be.capacity()));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int upEnd = capacity + 1;
            if (index < upEnd) {
                if (!slot.mayPickup(p) || !moveItemStackTo(stack, upEnd, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!(stack.getItem() instanceof CrateUpgradeItem && moveItemStackTo(stack, capacity, upEnd, false))
                    && !moveItemStackTo(stack, 0, capacity, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player p) {
        return container.stillValid(p);
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        container.stopOpen(p);
    }
}
