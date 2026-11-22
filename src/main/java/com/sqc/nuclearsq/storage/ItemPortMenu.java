package com.sqc.nuclearsq.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemPortMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, FILTER_Y = 48, INV_Y = 90, DATA = 4;
    public static final int BTN_MODE = 0, BTN_ENABLE = 1, BTN_CLEAR = 2;
    public final BlockPos pos;
    private final Container filters;
    private final ContainerData data;

    public ItemPortMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainer(ItemPortBlockEntity.FILTER_SLOTS), new SimpleContainerData(DATA));
    }

    public ItemPortMenu(int id, Inventory inv, BlockPos pos, Container filters, ContainerData data) {
        super(StorageRegistry.ITEM_PORT_MENU.get(), id);
        this.pos = pos;
        this.filters = filters;
        this.data = data;
        int x0 = (WIDTH - ItemPortBlockEntity.FILTER_SLOTS * 18) / 2 + 1;
        for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new Slot(filters, i, x0 + i * 18, FILTER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        int px = (WIDTH - 162) / 2 + 1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public static void open(ServerPlayer sp, ItemPortBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ItemPortMenu(id, inv, pos, be.filters(), be.menuData()),
                Component.translatable("block.nuclearsq.item_port_sqc")), buf -> buf.writeBlockPos(pos));
    }

    public int height() {
        return INV_Y + 82;
    }

    public boolean exporting() { return data.get(0) == ItemPortBlockEntity.EXPORT; }
    public boolean enabled() { return data.get(1) != 0; }
    public int moved() { return data.get(2); }

    public String problem() {
        int i = data.get(3);
        return i >= 0 && i < ItemPortBlockEntity.PROBLEMS.length ? ItemPortBlockEntity.PROBLEMS[i] : "";
    }

    public boolean hasFilter() {
        for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) if (!filters.getItem(i).isEmpty()) return true;
        return false;
    }

    private void setFilter(int slot, ItemStack stack) {
        if (!stack.isEmpty()) {
            for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) {
                if (i != slot && ItemStack.isSameItemSameComponents(filters.getItem(i), stack)) return;
            }
        }
        filters.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < ItemPortBlockEntity.FILTER_SLOTS) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) setFilter(slotId, type == ClickType.QUICK_MOVE ? ItemStack.EMPTY : getCarried());
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != filters && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < ItemPortBlockEntity.FILTER_SLOTS) return ItemStack.EMPTY;
        ItemStack st = slots.get(index).getItem();
        if (st.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) if (ItemStack.isSameItemSameComponents(filters.getItem(i), st)) return ItemStack.EMPTY;
        for (int i = 0; i < ItemPortBlockEntity.FILTER_SLOTS; i++) {
            if (filters.getItem(i).isEmpty()) {
                setFilter(i, st);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof ItemPortBlockEntity be)) return false;
        switch (id) {
            case BTN_MODE -> be.cycleMode();
            case BTN_ENABLE -> be.setEnabled(!be.enabled());
            case BTN_CLEAR -> be.clearFilters();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof ItemPortBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
