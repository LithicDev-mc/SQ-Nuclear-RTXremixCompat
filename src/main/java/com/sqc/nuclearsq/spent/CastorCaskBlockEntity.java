package com.sqc.nuclearsq.spent;

import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class CastorCaskBlockEntity extends BlockEntity {
    public static final int STACK = 16, INTERVAL = 10;

    private final int slots;
    private final ItemStackHandler items;
    private int timer;

    protected CastorCaskBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.slots = slots;
        this.items = newHandler(slots);
    }

    private ItemStackHandler newHandler(int n) {
        return new ItemStackHandler(n) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return SpentFuel.fitsCastor(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return STACK;
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    public int slots() {
        return slots;
    }

    public int capacity() {
        return slots * STACK;
    }

    public ItemStackHandler items() {
        return items;
    }

    public int[] counts() {
        return counts(stacks());
    }

    public static int[] counts(Iterable<ItemStack> stacks) {
        int rods = 0, cooled = 0;
        for (ItemStack s : stacks) {
            if (SpentFuel.isCooled(s)) cooled += s.getCount();
            else if (SpentFuel.isSpentRod(s)) rods += s.getCount();
        }
        return new int[] {rods, cooled};
    }

    private List<ItemStack> stacks() {
        List<ItemStack> l = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) l.add(items.getStackInSlot(i));
        return l;
    }

    public void serverTick() {
        if (level == null || ++timer < INTERVAL) return;
        timer = 0;
        for (BlockPos own : cells()) {
            for (Direction dir : Direction.values()) {
                BlockPos n = own.relative(dir);
                if (!(level.getBlockEntity(n) instanceof BigPortBlockEntity)) continue;
                IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, n, dir.getOpposite());
                if (h == null) continue;
                push(h);
                pull(h);
            }
        }
    }

    protected List<BlockPos> cells() {
        return List.of(worldPosition);
    }

    private void push(IItemHandler h) {
        for (int i = 0; i < slots; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (ItemHandlerHelper.insertItem(h, s.copyWithCount(1), true).isEmpty()) {
                ItemStack rest = ItemHandlerHelper.insertItem(h, items.extractItem(i, 1, false), false);
                if (!rest.isEmpty()) keep(items.insertItem(i, rest, false));
                return;
            }
        }
    }

    private void pull(IItemHandler h) {
        for (int i = 0; i < h.getSlots(); i++) {
            if (!SpentFuel.isCooled(h.getStackInSlot(i))) continue;
            ItemStack one = h.extractItem(i, 1, true);
            if (one.isEmpty() || !ItemHandlerHelper.insertItem(items, one, true).isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(items, h.extractItem(i, 1, false), false);
            if (!rest.isEmpty()) keep(ItemHandlerHelper.insertItem(h, rest, false));
            return;
        }
    }

    private void keep(ItemStack rest) {
        if (!rest.isEmpty() && level != null) net.minecraft.world.level.block.Block.popResource(level, worldPosition, rest);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks()));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        NonNullList<ItemStack> l = NonNullList.withSize(slots, ItemStack.EMPTY);
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(l);
        for (int i = 0; i < slots; i++) items.setStackInSlot(i, l.get(i));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("items");
    }
}
