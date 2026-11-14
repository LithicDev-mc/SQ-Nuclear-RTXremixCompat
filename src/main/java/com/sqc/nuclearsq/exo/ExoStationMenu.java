package com.sqc.nuclearsq.exo;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ExoStationMenu extends AbstractContainerMenu {
    public static final int PIECE = 0, CORE = 1, MODULE_FIRST = 2, INV_FIRST = MODULE_FIRST + ExoChamber.MODULE_MAX;
    public static final int WIDTH = 176, HEIGHT = 226, PIECE_X = 8, PIECE_Y = 80, CORE_X = 34, MOD_X = 8, MOD_Y = 116, INV_Y = 150;
    public static final int INV_X = (WIDTH - 162) / 2;

    private final boolean server;
    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    private static final class S extends Slot {
        private final Predicate<ItemStack> place;
        private final BooleanSupplier take, active;

        S(Container c, int i, int x, int y, Predicate<ItemStack> place, BooleanSupplier take, BooleanSupplier active) {
            super(c, i, x, y);
            this.place = place;
            this.take = take;
            this.active = active;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return place.test(stack);
        }

        @Override
        public boolean mayPickup(Player p) {
            return take.getAsBoolean();
        }

        @Override
        public boolean isActive() {
            return active.getAsBoolean();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public ExoStationMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, false, new SimpleContainer(1), new SimpleContainer(1), new SimpleContainer(ExoChamber.MODULE_MAX),
                new SimpleContainerData(ExoChamber.DATA_SIZE), pos);
    }

    public ExoStationMenu(int id, Inventory inv, BlockPos pos, ExoChamber st) {
        this(id, inv, true, st.piece(), st.core(), st.modules(), st.data(), pos);
    }

    private ExoStationMenu(int id, Inventory inv, boolean server, Container piece, Container core, Container modules, ContainerData data, BlockPos pos) {
        super(ExoRegistry.STATION_MENU.get(), id);
        this.server = server;
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new S(piece, 0, PIECE_X, PIECE_Y, ExoRegistry::isPiece, () -> true, () -> true));
        addSlot(new S(core, 0, CORE_X, PIECE_Y, s -> s.is(ExoRegistry.CORE.get()), modules::isEmpty, () -> true));
        for (int i = 0; i < ExoChamber.MODULE_MAX; i++) {
            int n = i;
            addSlot(new S(modules, i, MOD_X + i * 18, MOD_Y, s -> s.getItem() instanceof ExoModuleItem m && m.fits(piece.getItem(0)) && n < data.get(7), () -> true, () -> n < data.get(7)));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int data(int i) {
        return data.get(i);
    }

    public int data32(int hi) {
        return (data.get(hi) & 0xFFFF) << 16 | (data.get(hi + 1) & 0xFFFF);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = st.copy();
        if (!(index < INV_FIRST ? moveItemStackTo(st, INV_FIRST, slots.size(), true) : moveItemStackTo(st, 0, INV_FIRST, false))) return ItemStack.EMPTY;
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !server || stillValid(access, player, player.level().getBlockState(pos).getBlock());
    }
}
