package com.sqc.nuclearsq.nanobot;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class NanoCanisterMenu extends AbstractContainerMenu {
    public static final int UP_SLOTS = 4;
    public static final int ACT_TASK = 0, ACT_TOGGLE = 1, ACT_RADIUS = 2, ACT_SHAPE = 3, ACT_SOURCE = 4, ACT_CONFIRM = 5, ACT_CANCEL = 6, ACT_UNDO = 7, ACT_UNPORT = 8, ACT_HOLLOW = 9, ACT_AXIS = 10, ACT_GRADIUS = 11, ACT_GHEIGHT = 12, ACT_REDO = 13, ACT_ROT = 14, ACT_MIRROR = 15, ACT_PMODE = 16, ACT_OFFX = 17, ACT_OFFY = 18, ACT_OFFZ = 19;
    public static final int D_RUNNING = 0, D_TASK = 1, D_PHASE = 2, D_PROGRESS = 3, D_BOTS = 4, D_SPEED = 5, D_RADIUS = 6, D_MAXRADIUS = 7, D_CARGO = 8, D_UNDO = 9, D_REDO = 10, D_TEMPLATE = 11, D_SIZE = 12;

    private final Player player;
    private final int invSlot;
    @Nullable private final ItemStack opened;
    private final SimpleContainer upgrades;
    private final SimpleContainerData data = new SimpleContainerData(D_SIZE);
    private boolean loading;

    public NanoCanisterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readInt());
    }

    public NanoCanisterMenu(int id, Inventory inv, int invSlot) {
        super(NanobotRegistry.MENU.get(), id);
        this.player = inv.player;
        this.invSlot = invSlot;
        this.opened = player instanceof ServerPlayer ? player.getInventory().getItem(invSlot) : null;
        this.upgrades = new SimpleContainer(UP_SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!loading) saveUpgrades();
            }
        };
        loading = true;
        NonNullList<ItemStack> list = NonNullList.withSize(UP_SLOTS, ItemStack.EMPTY);
        canister().getOrDefault(NanobotRegistry.UPGRADES.get(), ItemContainerContents.EMPTY).copyInto(list);
        for (int i = 0; i < UP_SLOTS; i++) upgrades.setItem(i, list.get(i));
        loading = false;
        for (int i = 0; i < UP_SLOTS; i++) addSlot(new UpgradeSlot(upgrades, i, 204 + 22 * i, 95));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(lockable(inv, 9 + r * 9 + c, 8 + c * 18, 168 + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(lockable(inv, c, 8 + c * 18, 226));
        addDataSlots(data);
    }

    private Slot lockable(Inventory inv, int index, int x, int y) {
        if (index != invSlot) return new Slot(inv, index, x, y);
        return new Slot(inv, index, x, y) {
            @Override
            public boolean mayPickup(Player p) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    private final class UpgradeSlot extends Slot {
        UpgradeSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof NanoUpgradeItem u)) return false;
            for (int k = 0; k < UP_SLOTS; k++) {
                if (k != getContainerSlot() && upgrades.getItem(k).getItem() instanceof NanoUpgradeItem o && o.type() == u.type()) return false;
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public ItemStack canister() {
        ItemStack s = player.getInventory().getItem(invSlot);
        return s.getItem() instanceof NanoCanisterItem && (opened == null || s == opened) ? s : ItemStack.EMPTY;
    }

    private void saveUpgrades() {
        ItemStack st = canister();
        if (st.isEmpty()) return;
        NonNullList<ItemStack> list = NonNullList.withSize(UP_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < UP_SLOTS; i++) list.set(i, upgrades.getItem(i).copy());
        st.set(NanobotRegistry.UPGRADES.get(), ItemContainerContents.fromItems(list));
        if (player instanceof ServerPlayer sp) NanoSwarms.refresh(sp);
    }

    public int data(int i) {
        return data.get(i);
    }

    public ItemStack upgradeStack(int i) {
        return upgrades.getItem(i);
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer sp) {
            ItemStack st = canister();
            NanoSettings s = NanoSettings.of(st);
            NanoSwarm sw = NanoSwarms.get(sp);
            boolean running = sw != null && !sw.isEnded();
            data.set(D_RUNNING, running ? 1 : 0);
            data.set(D_TASK, NanoCanisterItem.task(st).ordinal());
            data.set(D_PHASE, running ? sw.phase().ordinal() + 1 : 0);
            data.set(D_PROGRESS, running ? sw.workProgress() + 1 : 0);
            data.set(D_BOTS, running ? sw.botCount() : s.bots());
            data.set(D_SPEED, (int) Math.round(s.speedFactor() * 10));
            data.set(D_RADIUS, s.radius());
            data.set(D_MAXRADIUS, s.maxRadius());
            data.set(D_CARGO, running ? sw.cargoStacks() : 0);
            data.set(D_UNDO, NanoGadget.canUndo(sp) ? 1 : 0);
            data.set(D_REDO, NanoGadget.canRedo(sp) ? 1 : 0);
            NanoGadget.Template tpl = NanoGadget.template(sp);
            data.set(D_TEMPLATE, tpl == null ? 0 : Math.min(tpl.entries().size(), 32000));
        }
        super.broadcastChanges();
    }

    private static GadgetData withPaste(ItemStack st, java.util.function.UnaryOperator<GadgetData.Paste> f) {
        GadgetData d = NanoGadget.data(st);
        return d.withPaste(f.apply(d.paste()));
    }

    public void handleAction(ServerPlayer sp, int action, int value) {
        ItemStack st = canister();
        if (st.isEmpty()) return;
        switch (action) {
            case ACT_TASK -> {
                if (value < 0 || value >= NanoTask.values().length) return;
                NanoCanisterItem.applyTask(sp, st, NanoTask.byOrdinal(value));
            }
            case ACT_TOGGLE -> NanoCanisterItem.toggle(sp, st);
            case ACT_SHAPE -> NanoGadget.setData(st, NanoGadget.data(st).withShape(value));
            case ACT_SOURCE -> NanoGadget.setData(st, NanoGadget.data(st).withSource(value));
            case ACT_CONFIRM -> NanoGadget.confirm(sp, st);
            case ACT_CANCEL -> NanoGadget.cancel(sp, st);
            case ACT_UNDO -> NanoGadget.undo(sp, st);
            case ACT_REDO -> NanoGadget.redo(sp, st);
            case ACT_HOLLOW -> NanoGadget.setData(st, NanoGadget.data(st).withHollow(value != 0));
            case ACT_AXIS -> NanoGadget.setData(st, NanoGadget.data(st).withAxis(value));
            case ACT_GRADIUS -> NanoGadget.setData(st, NanoGadget.data(st).withRadius(value));
            case ACT_GHEIGHT -> NanoGadget.setData(st, NanoGadget.data(st).withHeight(value));
            case ACT_ROT -> NanoGadget.setData(st, withPaste(st, p -> p.withRot(value)));
            case ACT_MIRROR -> NanoGadget.setData(st, withPaste(st, p -> p.withMirror(value != 0)));
            case ACT_PMODE -> NanoGadget.setData(st, withPaste(st, p -> p.withReplace(value != 0)));
            case ACT_OFFX -> NanoGadget.setData(st, withPaste(st, p -> p.withOffset(0, value)));
            case ACT_OFFY -> NanoGadget.setData(st, withPaste(st, p -> p.withOffset(1, value)));
            case ACT_OFFZ -> NanoGadget.setData(st, withPaste(st, p -> p.withOffset(2, value)));
            case ACT_UNPORT -> NanoPort.uncouple(sp, st, true);
            case ACT_RADIUS -> {
                NanoSettings cur = NanoSettings.of(st);
                st.set(NanobotRegistry.RADIUS.get(), Mth.clamp(value, NanoSettings.MIN_RADIUS, cur.maxRadius()));
                NanoSwarms.refresh(sp);
            }
            default -> NuclearSQ.LOGGER.info("[nanobots] Menue: unbekannte Aktion {}", action);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (canister().isEmpty() || type == ClickType.SWAP && button == invSlot) return;
        super.clicked(slotId, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem();
        ItemStack copy = s.copy();
        if (index < UP_SLOTS) {
            if (!moveItemStackTo(s, UP_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (s.getItem() instanceof NanoUpgradeItem) {
            if (!moveItemStackTo(s, 0, UP_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return !canister().isEmpty();
    }
}
