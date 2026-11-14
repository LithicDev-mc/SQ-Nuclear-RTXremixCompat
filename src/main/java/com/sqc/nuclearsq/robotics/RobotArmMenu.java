package com.sqc.nuclearsq.robotics;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RobotArmMenu extends AbstractContainerMenu {
    public static final int BTN_TOGGLE = 0, BTN_CLEAR_SOURCE = 1, BTN_CLEAR_TARGET = 2, BTN_CLEAR_FILTER = 3;
    public static final int FILTER_X = 8, FILTER_Y = 127, UPGRADE_X = 8, UPGRADE_Y = 159, INV_Y = 206, WIDTH = 212, HEIGHT = 288;
    public static final int INV_X = (WIDTH - 162) / 2;

    @Nullable private final RobotArmBlockEntity arm;
    private final Container filters;
    private final Container upgrades;
    private final ContainerData data;
    private final net.minecraft.world.inventory.ContainerLevelAccess access;

    public RobotArmMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, null, new net.minecraft.world.SimpleContainer(RobotArmBlockEntity.FILTER_SLOTS),
                new net.minecraft.world.SimpleContainer(RobotArmBlockEntity.UPGRADE_SLOTS),
                new net.minecraft.world.inventory.SimpleContainerData(RobotArmBlockEntity.DATA_SIZE), pos);
    }

    public RobotArmMenu(int id, Inventory inv, RobotArmBlockEntity arm, Container filters, Container upgrades, ContainerData data) {
        this(id, inv, arm, filters, upgrades, data, arm.getBlockPos());
    }

    private RobotArmMenu(int id, Inventory inv, @Nullable RobotArmBlockEntity arm, Container filters, Container upgrades, ContainerData data, BlockPos pos) {
        super(RoboticsRegistry.ARM_MENU.get(), id);
        this.arm = arm;
        this.filters = filters;
        this.upgrades = upgrades;
        this.data = data;
        this.access = arm != null && arm.getLevel() != null ? net.minecraft.world.inventory.ContainerLevelAccess.create(arm.getLevel(), pos)
                : net.minecraft.world.inventory.ContainerLevelAccess.create(inv.player.level(), pos);
        for (int i = 0; i < RobotArmBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new Slot(filters, i, FILTER_X + i * 18, FILTER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player p) {
                    return false;
                }
            });
        }
        for (int i = 0; i < RobotArmBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(upgrades, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(RoboticsRegistry.ARM_UPGRADE.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int data(int i) {
        return data.get(i);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < RobotArmBlockEntity.FILTER_SLOTS) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_CRAFT) {
                ItemStack carried = getCarried();
                filters.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int upFirst = RobotArmBlockEntity.FILTER_SLOTS, invFirst = upFirst + RobotArmBlockEntity.UPGRADE_SLOTS;
        if (index >= upFirst && index < invFirst) {
            Slot slot = slots.get(index);
            ItemStack st = slot.getItem();
            if (st.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = st.copy();
            if (!moveItemStackTo(st, invFirst, slots.size(), true)) return ItemStack.EMPTY;
            slot.setChanged();
            return copy;
        }
        if (index >= invFirst && slots.get(index).getItem().is(RoboticsRegistry.ARM_UPGRADE.get())) {
            Slot slot = slots.get(index);
            ItemStack st = slot.getItem();
            ItemStack copy = st.copy();
            if (!moveItemStackTo(st, upFirst, invFirst, false)) return ItemStack.EMPTY;
            slot.setChanged();
            return copy;
        }
        if (index >= invFirst) {
            ItemStack st = slots.get(index).getItem();
            if (!st.isEmpty()) {
                int free = -1;
                for (int i = 0; i < RobotArmBlockEntity.FILTER_SLOTS; i++) {
                    if (ItemStack.isSameItem(filters.getItem(i), st)) return ItemStack.EMPTY;
                    if (filters.getItem(i).isEmpty() && free < 0) free = i;
                }
                if (free >= 0) filters.setItem(free, st.copyWithCount(1));
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (arm == null) return false;
        switch (id) {
            case BTN_TOGGLE -> arm.setEnabled(!arm.enabled());
            case BTN_CLEAR_SOURCE -> arm.clearSource();
            case BTN_CLEAR_TARGET -> arm.clearTarget();
            case BTN_CLEAR_FILTER -> arm.clearFilters();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return arm == null || stillValid(access, player, arm.getBlockState().getBlock());
    }
}
