package com.sqc.nuclearsq.exo;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ExoChamber {
    public static final int MODULE_MAX = 6, BUFFER = 40_000, DATA_SIZE = 8;

    private final MachineEnergy energy;
    private final Runnable changed;
    private final SimpleContainer piece = new SimpleContainer(1), core = new SimpleContainer(1), modules = new SimpleContainer(MODULE_MAX);
    private boolean syncing, dirty;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            ItemStack c = core.getItem(0);
            boolean hasCore = !c.isEmpty();
            return switch (i) {
                case 0 -> energy.getEnergyStored() >>> 16;
                case 1 -> energy.getEnergyStored() & 0xFFFF;
                case 2 -> hasCore ? ExoCoreItem.energy(c) >>> 16 : 0;
                case 3 -> hasCore ? ExoCoreItem.energy(c) & 0xFFFF : 0;
                case 4 -> hasCore ? ExoCoreItem.capacity(c) >>> 16 : 0;
                case 5 -> hasCore ? ExoCoreItem.capacity(c) & 0xFFFF : 0;
                case 6 -> hasCore ? ExoCoreItem.tier(c) : 0;
                case 7 -> moduleSlots();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public ExoChamber(MachineEnergy energy, Runnable changed) {
        this.energy = energy;
        this.changed = changed;
        piece.addListener(c -> {
            if (!syncing) loadModules();
            dirty = true;
            changed.run();
        });
        modules.addListener(c -> {
            if (!syncing) storeModules();
            changed.run();
        });
        core.addListener(c -> changed.run());
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.EXO_STATION;
    }

    public SimpleContainer piece() { return piece; }

    public SimpleContainer core() { return core; }

    public SimpleContainer modules() { return modules; }

    public ContainerData data() { return data; }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public int drain(int max) {
        return energy.drain(max);
    }

    public int moduleSlots() {
        return piece.getItem(0).isEmpty() || core.getItem(0).isEmpty() ? 0 : Math.min(MODULE_MAX, ExoCoreItem.moduleSlots(core.getItem(0)));
    }

    public boolean insert(ItemStack held) {
        if (!piece.getItem(0).isEmpty() || !ExoRegistry.isPiece(held)) return false;
        piece.setItem(0, held.split(1));
        return true;
    }

    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        if (!piece.getItem(0).isEmpty()) out.add(piece.getItem(0));
        if (!core.getItem(0).isEmpty()) out.add(core.getItem(0));
        return out;
    }

    private void loadModules() {
        syncing = true;
        modules.clearContent();
        List<ModuleEntry> list = piece.getItem(0).getOrDefault(ExoRegistry.MODULES.get(), List.of());
        for (int i = 0; i < Math.min(list.size(), MODULE_MAX); i++) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, list.get(i).id()));
            if (it != Items.AIR) modules.setItem(i, new ItemStack(it));
        }
        syncing = false;
    }

    private void storeModules() {
        ItemStack p = piece.getItem(0);
        if (p.isEmpty()) return;
        List<ModuleEntry> list = new ArrayList<>();
        for (int i = 0; i < MODULE_MAX; i++) {
            ItemStack m = modules.getItem(i);
            if (m.getItem() instanceof ExoModuleItem mod) list.add(new ModuleEntry(BuiltInRegistries.ITEM.getKey(m.getItem()).getPath(), mod.level()));
        }
        if (list.isEmpty()) p.remove(ExoRegistry.MODULES.get());
        else p.set(ExoRegistry.MODULES.get(), list);
    }

    public boolean tick() {
        ItemStack c = core.getItem(0);
        if (c.isEmpty() || energy.getEnergyStored() <= 0) return false;
        int bonus = ExoCoreItem.batteryBonus(piece.getItem(0));
        if (!piece.getItem(0).isEmpty() && c.getOrDefault(ExoRegistry.BATTERY.get(), 0) != bonus) c.set(ExoRegistry.BATTERY.get(), bonus);
        int move = Math.min(Math.min(energy.getEnergyStored(), ExoCoreItem.MAX_RECEIVE), ExoCoreItem.capacity(c) - ExoCoreItem.energy(c));
        if (move <= 0) return false;
        c.set(ModDataComponents.STORED_ENERGY.get(), ExoCoreItem.energy(c) + move);
        energy.drain(move);
        changed.run();
        return true;
    }

    public void save(CompoundTag tag, HolderLookup.Provider reg) {
        if (!piece.isEmpty()) tag.put("piece", piece.createTag(reg));
        if (!core.isEmpty()) tag.put("core", core.createTag(reg));
    }

    public void load(CompoundTag tag, HolderLookup.Provider reg) {
        syncing = true;
        piece.clearContent();
        core.clearContent();
        if (tag.contains("piece", Tag.TAG_LIST)) piece.fromTag(tag.getList("piece", Tag.TAG_COMPOUND), reg);
        if (tag.contains("core", Tag.TAG_LIST)) core.fromTag(tag.getList("core", Tag.TAG_COMPOUND), reg);
        syncing = false;
        loadModules();
    }

    public Tag pieceSync(HolderLookup.Provider reg) {
        return piece.getItem(0).isEmpty() ? new CompoundTag() : piece.getItem(0).save(reg);
    }

    public void readPieceSync(Tag t, HolderLookup.Provider reg) {
        syncing = true;
        piece.setItem(0, t instanceof CompoundTag c && !c.isEmpty() ? ItemStack.parse(reg, c).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        syncing = false;
    }
}
