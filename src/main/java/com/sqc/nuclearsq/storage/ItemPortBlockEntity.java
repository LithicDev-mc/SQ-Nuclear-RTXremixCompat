package com.sqc.nuclearsq.storage;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.ChunkGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class ItemPortBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 5;
    public static final int AMOUNT = 64, PERIOD = 20;
    public static final int IMPORT = 0, EXPORT = 1;
    public static final String[] PROBLEMS = {"", "paused", "no_inventory", "no_storage", "no_filter", "idle"};

    private final SimpleContainer filters = new SimpleContainer(FILTER_SLOTS);
    private int mode = IMPORT;
    private boolean enabled = true;
    private int cooldown;
    private int lastMoved;
    private String lastProblem = "idle";
    private final int[] shown = new int[FILTER_SLOTS];
    private final Handler handler = new Handler();

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.ITEM_PORT_BE.get(), pos, state);
        filters.addListener(c -> setChanged());
    }

    public IItemHandler handler() {
        return handler;
    }

    public int mode() {
        return mode;
    }

    public boolean exporting() {
        return mode == EXPORT;
    }

    public void setMode(int m) {
        mode = m == EXPORT ? EXPORT : IMPORT;
        setChanged();
    }

    public void cycleMode() {
        setMode(mode == IMPORT ? EXPORT : IMPORT);
        cooldown = 0;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setChanged();
    }

    public int lastMoved() {
        return lastMoved;
    }

    public String problem() {
        return lastProblem;
    }

    public SimpleContainer filters() {
        return filters;
    }

    public int filterCount() {
        int n = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) if (!filters.getItem(i).isEmpty()) n++;
        return n;
    }

    public boolean toggleFilter(ItemStack st) {
        int free = -1;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (ItemStack.isSameItemSameComponents(f, st)) {
                filters.setItem(i, ItemStack.EMPTY);
                return false;
            }
            if (f.isEmpty() && free < 0) free = i;
        }
        filters.setItem(free >= 0 ? free : 0, st.copyWithCount(1));
        return true;
    }

    public void clearFilters() {
        filters.clearContent();
    }

    public boolean matches(ItemStack st) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItemSameComponents(f, st)) return true;
        }
        return !any;
    }

    public Component status() {
        Component f = filterCount() == 0 ? Component.translatable("message.nuclearsq.item_port.all") : filterNames();
        Component state = lastProblem.isEmpty() ? Component.translatable("message.nuclearsq.item_port.ok", lastMoved)
                : Component.translatable("message.nuclearsq.item_port.problem." + lastProblem);
        return Component.translatable("message.nuclearsq.item_port.status",
                Component.translatable(mode == EXPORT ? "message.nuclearsq.item_port.mode_export" : "message.nuclearsq.item_port.mode_import"), f, state);
    }

    private Component filterNames() {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean first = true;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) continue;
            if (!first) out.append(", ");
            out.append(f.getHoverName());
            first = false;
        }
        return out;
    }

    @Nullable
    private IItemHandler inventory() {
        if (!(level instanceof ServerLevel sl) || !(getBlockState().getBlock() instanceof ItemPortBlock)) return null;
        Direction d = getBlockState().getValue(ItemPortBlock.FACING);
        BlockPos p = worldPosition.relative(d);
        if (!ChunkGuard.loaded(sl, p)) return null;
        return sl.getCapability(Capabilities.ItemHandler.BLOCK, p, d.getOpposite());
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl) || !enabled) return;
        if (--cooldown > 0) return;
        cooldown = PERIOD;
        if (sl.hasNeighborSignal(worldPosition)) {
            lastProblem = "paused";
            return;
        }
        IItemHandler inv = inventory();
        if (inv == null) {
            lastProblem = "no_inventory";
            return;
        }
        if (mode == EXPORT && filterCount() == 0) {
            lastProblem = "no_filter";
            return;
        }
        int moved = mode == EXPORT ? exportTo(inv, AMOUNT) : importFrom(inv, AMOUNT);
        if (moved >= 0) {
            lastMoved = moved;
            lastProblem = moved > 0 ? "" : "idle";
        }
        refreshShown();
        setChanged();
    }

    int importFrom(IItemHandler inv, int limit) {
        StorageAccess net = null;
        int total = 0;
        for (int slot = 0; slot < inv.getSlots() && total < limit; slot++) {
            ItemStack peek = inv.extractItem(slot, limit - total, true);
            if (peek.isEmpty() || !matches(peek)) continue;
            if (net == null) {
                net = access();
                if (net == null || net.drives() == 0) {
                    lastProblem = "no_storage";
                    return -1;
                }
            }
            long fits = peek.getCount() - net.insert(peek, true).getCount();
            if (fits <= 0) continue;
            ItemStack taken = inv.extractItem(slot, (int) fits, false);
            if (taken.isEmpty()) continue;
            ItemStack left = net.insert(taken, false);
            if (!left.isEmpty()) left = inv.insertItem(slot, left, false);
            if (!left.isEmpty()) net.insert(left, false);
            total += taken.getCount() - left.getCount();
        }
        return total;
    }

    int exportTo(IItemHandler inv, int limit) {
        StorageAccess net = access();
        if (net == null || net.drives() == 0) {
            lastProblem = "no_storage";
            return -1;
        }
        int total = 0;
        for (int i = 0; i < FILTER_SLOTS && total < limit; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) continue;
            ItemKey key = ItemKey.of(f);
            ItemStack offer = net.extractStack(key, Math.min(limit - total, key.maxStack()), true);
            if (offer.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(inv, offer, true);
            int fits = offer.getCount() - rest.getCount();
            if (fits <= 0) continue;
            ItemStack real = net.extractStack(key, fits, false);
            if (real.isEmpty()) continue;
            ItemStack left = ItemHandlerHelper.insertItem(inv, real, false);
            if (!left.isEmpty()) net.insert(left, false);
            total += real.getCount() - left.getCount();
        }
        return total;
    }

    @Nullable
    private StorageAccess access() {
        return level == null ? null : StorageNet.access(level, worldPosition);
    }

    private void refreshShown() {
        StorageAccess net = null;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) {
                shown[i] = 0;
                continue;
            }
            if (net == null) net = access();
            ItemKey key = ItemKey.of(f);
            shown[i] = net == null ? 0 : (int) Math.min(key.maxStack(), net.available(key));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putByte("mode", (byte) mode);
        tag.putBoolean("enabled", enabled);
        if (!filters.isEmpty()) tag.put("filters", filters.createTag(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        mode = tag.getByte("mode") == EXPORT ? EXPORT : IMPORT;
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        filters.clearContent();
        if (tag.contains("filters", Tag.TAG_LIST)) filters.fromTag(tag.getList("filters", Tag.TAG_COMPOUND), reg);
    }

    private final class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + FILTER_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot <= 0 || slot > FILTER_SLOTS || !enabled) return ItemStack.EMPTY;
            ItemStack f = filters.getItem(slot - 1);
            int n = shown[slot - 1];
            return f.isEmpty() || n <= 0 ? ItemStack.EMPTY : f.copyWithCount(n);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !enabled || level == null || level.isClientSide) return stack;
            StorageAccess net = access();
            if (net == null || net.drives() == 0) return stack;
            return net.insert(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot <= 0 || slot > FILTER_SLOTS || amount <= 0 || !enabled || level == null || level.isClientSide) return ItemStack.EMPTY;
            ItemStack f = filters.getItem(slot - 1);
            if (f.isEmpty()) return ItemStack.EMPTY;
            StorageAccess net = access();
            if (net == null) return ItemStack.EMPTY;
            ItemKey key = ItemKey.of(f);
            return net.extractStack(key, Math.min(amount, key.maxStack()), simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0;
        }
    }
}
