package com.sqc.nuclearsq.crate;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.ChunkGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public class StorageCrateBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int BASE_SIZE = 27, MAX_SIZE = 108, EJECT = 64, PERIOD = 20;
    public static final int FRONT = 0, BACK = 1, LEFT = 2, RIGHT = 3, TOP = 4, BOTTOM = 5, SIDES = 6;
    public static final int OFF = 0, IN = 1, OUT = 2, BOTH = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(MAX_SIZE, ItemStack.EMPTY);
    private final SimpleContainer upgrade = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            StorageCrateBlockEntity.this.setChanged();
        }
    };
    private final int[] modes = {BOTH, BOTH, BOTH, BOTH, BOTH, BOTH};
    private boolean eject;
    private int cooldown, lastMoved;
    private final IItemHandler[] handlers = new IItemHandler[SIDES];

    public StorageCrateBlockEntity(BlockPos pos, BlockState st) {
        super(CrateRegistry.BLOCK_ENTITY.get(), pos, st);
    }

    public static int capacityFor(ItemStack up) {
        return up.getItem() instanceof CrateUpgradeItem u ? BASE_SIZE << u.tier : BASE_SIZE;
    }

    public int capacity() {
        return capacityFor(upgrade.getItem(0));
    }

    public SimpleContainer upgrade() {
        return upgrade;
    }

    public int needed() {
        for (int i = MAX_SIZE - 1; i >= 0; i--) if (!items.get(i).isEmpty()) return i + 1;
        return 0;
    }

    public int used() {
        int n = 0;
        for (ItemStack s : items) if (!s.isEmpty()) n++;
        return n;
    }

    public int types() {
        java.util.List<ItemStack> seen = new java.util.ArrayList<>();
        for (ItemStack s : items) if (!s.isEmpty() && seen.stream().noneMatch(t -> ItemStack.isSameItemSameComponents(t, s))) seen.add(s);
        return seen.size();
    }

    public int relative(Direction d) {
        Direction f = getBlockState().getValue(StorageCrateBlock.FACING);
        return d == Direction.UP ? TOP : d == Direction.DOWN ? BOTTOM : d == f ? FRONT : d == f.getOpposite() ? BACK
                : d == f.getClockWise() ? LEFT : RIGHT;
    }

    public Direction absolute(int rel) {
        Direction f = getBlockState().getValue(StorageCrateBlock.FACING);
        return switch (rel) {
            case FRONT -> f;
            case BACK -> f.getOpposite();
            case LEFT -> f.getClockWise();
            case RIGHT -> f.getCounterClockWise();
            case TOP -> Direction.UP;
            default -> Direction.DOWN;
        };
    }

    public int mode(int rel) {
        return modes[rel];
    }

    public void cycle(int rel, int step) {
        modes[rel] = Math.floorMod(modes[rel] + step, 4);
        changedSides();
    }

    public void setMode(int rel, int mode) {
        modes[rel] = Math.floorMod(mode, 4);
        changedSides();
    }

    public boolean eject() {
        return eject;
    }

    public void setEject(boolean on) {
        eject = on;
        cooldown = 0;
        setChanged();
    }

    public int lastMoved() {
        return lastMoved;
    }

    private void changedSides() {
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.invalidateCapabilities(worldPosition.relative(StorageCrateBlock.toSecond(getBlockState())));
        }
    }

    @Nullable
    public IItemHandler handler(@Nullable Direction side) {
        if (side == null) return new InvWrapper(this);
        int rel = relative(side);
        if (modes[rel] == OFF) return null;
        if (handlers[rel] == null) handlers[rel] = new SideHandler(rel);
        return handlers[rel];
    }

    void serverTick() {
        if (!eject || !(level instanceof ServerLevel sl) || --cooldown > 0) return;
        cooldown = PERIOD;
        int left = EJECT;
        for (int rel = 0; rel < SIDES && left > 0; rel++) {
            if ((modes[rel] & OUT) == 0) continue;
            Direction d = absolute(rel);
            BlockPos p = (rel == RIGHT ? worldPosition.relative(StorageCrateBlock.toSecond(getBlockState())) : worldPosition).relative(d);
            if (!ChunkGuard.loaded(sl, p)) continue;
            IItemHandler target = sl.getCapability(Capabilities.ItemHandler.BLOCK, p, d.getOpposite());
            if (target != null) left -= push(target, left);
        }
        lastMoved = EJECT - left;
    }

    private int push(IItemHandler target, int limit) {
        int moved = 0, cap = capacity();
        for (int i = 0; i < cap && moved < limit; i++) {
            ItemStack in = items.get(i);
            if (in.isEmpty()) continue;
            ItemStack offer = in.copyWithCount(Math.min(in.getCount(), limit - moved));
            int fits = offer.getCount() - ItemHandlerHelper.insertItem(target, offer, true).getCount();
            if (fits <= 0) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(target, in.copyWithCount(fits), false);
            int done = fits - rest.getCount();
            in.shrink(done);
            moved += done;
        }
        if (moved > 0) setChanged();
        return moved;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        ContainerHelper.saveAllItems(tag, items, regs);
        if (!upgrade.isEmpty()) tag.put("Upgrade", upgrade.getItem(0).save(regs));
        tag.putIntArray("modes", modes);
        tag.putBoolean("eject", eject);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, regs);
        upgrade.setItem(0, tag.contains("Upgrade") ? ItemStack.parseOptional(regs, tag.getCompound("Upgrade")) : ItemStack.EMPTY);
        int[] m = tag.getIntArray("modes");
        if (m.length == SIDES) System.arraycopy(m, 0, modes, 0, SIDES);
        eject = tag.getBoolean("eject");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(CrateRegistry.CONTENTS.get(), ItemContainerContents.fromItems(items));
        builder.set(CrateRegistry.UPGRADE.get(), ItemContainerContents.fromItems(java.util.List.of(upgrade.getItem(0))));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        items.clear();
        input.getOrDefault(CrateRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).copyInto(items);
        upgrade.setItem(0, input.getOrDefault(CrateRegistry.UPGRADE.get(), ItemContainerContents.EMPTY).copyOne());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
        tag.remove("Upgrade");
    }

    @Override
    public int getContainerSize() {
        return capacity();
    }

    @Override
    public boolean isEmpty() {
        return needed() == 0 && upgrade.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < capacity() && stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        upgrade.clearContent();
        setChanged();
    }

    @Override
    public void startOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    ContainerData data() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return i < SIDES ? modes[i] : eject ? 1 : 0;
            }

            @Override
            public void set(int i, int v) {
                if (i < SIDES) setMode(i, v);
                else setEject(v != 0);
            }

            @Override
            public int getCount() {
                return StorageCrateMenu.DATA;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nuclearsq.storage_crate_sqc");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StorageCrateMenu(id, inv, this, upgrade, data(), capacity());
    }

    private final class SideHandler implements IItemHandler {
        private final int rel;

        SideHandler(int rel) {
            this.rel = rel;
        }

        @Override
        public int getSlots() {
            return capacity();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < capacity() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if ((modes[rel] & IN) == 0 || stack.isEmpty() || !canPlaceItem(slot, stack)) return stack;
            ItemStack in = items.get(slot);
            if (!in.isEmpty() && !ItemStack.isSameItemSameComponents(in, stack)) return stack;
            int room = Math.min(getMaxStackSize(stack), stack.getMaxStackSize()) - in.getCount();
            if (room <= 0) return stack;
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                if (in.isEmpty()) items.set(slot, stack.copyWithCount(n));
                else in.grow(n);
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if ((modes[rel] & OUT) == 0 || amount <= 0 || slot >= capacity()) return ItemStack.EMPTY;
            ItemStack in = items.get(slot);
            if (in.isEmpty()) return ItemStack.EMPTY;
            int n = Math.min(amount, in.getCount());
            if (simulate) return in.copyWithCount(n);
            ItemStack out = in.split(n);
            setChanged();
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return (modes[rel] & IN) != 0 && canPlaceItem(slot, stack);
        }
    }
}
