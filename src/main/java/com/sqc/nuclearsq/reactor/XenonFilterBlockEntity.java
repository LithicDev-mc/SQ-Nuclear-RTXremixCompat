package com.sqc.nuclearsq.reactor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class XenonFilterBlockEntity extends BlockEntity {
    public static final int SLOTS = 4, FE_PER_TICK = 600;
    private static final Map<ResourceKey<Level>, Set<Long>> ACTIVE = new ConcurrentHashMap<>();

    private final MachineEnergy energy = new MachineEnergy(FE_PER_TICK * 40, FE_PER_TICK * 5).onChange(this::setChanged);
    private int filters, left;

    private final IItemHandler items = new IItemHandler() {
        public int getSlots() { return 1; }
        public ItemStack getStackInSlot(int slot) { return filters == 0 ? ItemStack.EMPTY : new ItemStack(filter(), filters); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            int n = Math.min(stack.getCount(), SLOTS - filters);
            if (!simulate) for (int i = 0; i < n; i++) insert();
            return stack.copyWithCount(stack.getCount() - n);
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        public int getSlotLimit(int slot) { return SLOTS; }
        public boolean isItemValid(int slot, ItemStack stack) { return stack.is(filter()); }
    };

    public XenonFilterBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorRegistry.XENON_FILTER_BE.get(), pos, state);
    }

    private static net.minecraft.world.item.Item filter() {
        return CatalogRegistry.ITEM_MAP.get("carbon_filter_sqc").get();
    }

    public MachineEnergy energy() { return energy; }
    public IItemHandler items() { return items; }
    public int filters() { return filters; }
    public int leftPercent() { return Math.round(100f * left / CoreChemistry.FILTER_TICKS); }

    public boolean insert() {
        if (filters >= SLOTS) return false;
        if (filters++ == 0) left = CoreChemistry.FILTER_TICKS;
        setChanged();
        return true;
    }

    private boolean run(int ticks, boolean use) {
        if (filters == 0 || energy.getEnergyStored() < FE_PER_TICK * ticks) return false;
        if (!use) return true;
        energy.drain(FE_PER_TICK * ticks);
        left -= ticks;
        if (left <= 0) left = --filters > 0 ? CoreChemistry.FILTER_TICKS : 0;
        setChanged();
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register(level, worldPosition);
    }

    public static void register(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) ACTIVE.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.asLong());
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            Set<Long> set = ACTIVE.get(sl.dimension());
            if (set != null) set.remove(worldPosition.asLong());
        }
        super.setRemoved();
    }

    public static int work(ServerLevel level, BlockPos center, int range, int ticks, boolean use) {
        Set<Long> set = ACTIVE.get(level.dimension());
        if (set == null) return 0;
        int n = 0;
        for (long l : set) {
            BlockPos p = BlockPos.of(l);
            if (Math.abs(p.getX() - center.getX()) <= range && Math.abs(p.getY() - center.getY()) <= range
                    && Math.abs(p.getZ() - center.getZ()) <= range && level.isLoaded(p)
                    && level.getBlockEntity(p) instanceof XenonFilterBlockEntity be && be.run(ticks, use)) n++;
        }
        return n;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("filters", filters);
        tag.putInt("left", left);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        energy.drain(energy.getEnergyStored());
        energy.add(tag.getInt("energy"));
        filters = Mth.clamp(tag.getInt("filters"), 0, SLOTS);
        left = Mth.clamp(tag.getInt("left"), 0, CoreChemistry.FILTER_TICKS);
    }
}
