package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class BreederCore {
    public static final int DRIVERS = 4, BLANKET_FIRST = 4, BLANKETS = 8, OUT_FIRST = 12, OUT_SLOTS = 8, SLOTS = 20;
    public static final double POWER_SV_H = 0.6, BRED_SV_H = 0.0002, LEAK_SV_H = 0.05;
    private static final double LEAK_CHANCE = 1.0 / 200_000;
    private static final int WEAR_STEP_SEC = 7200;

    private final int steamPerDriver, driverTicks, blanketTicks, puPct, sodiumMin, leakMb;
    private final float[] driverLeft = new float[DRIVERS];
    private final boolean[] driverPu = new boolean[DRIVERS];
    private final float[] progress = new float[BLANKETS];
    private boolean leak, noSodium, noDriver;
    private int wearSec;
    private float load, flux;

    public BreederCore(BigMachine m) {
        steamPerDriver = m.extra("steam_per_driver_mb_t");
        driverTicks = m.extra("driver_ticks");
        blanketTicks = m.extra("blanket_ticks");
        puPct = m.extra("pu_breed_pct");
        sodiumMin = m.extra("sodium_min_mb");
        leakMb = m.extra("sodium_leak_mb_t");
    }

    private static Item item(String id) {
        return CatalogRegistry.ITEM_MAP.get(id).get();
    }

    public static boolean isDriver(ItemStack s) {
        return !s.isEmpty() && (s.is(item("uranium_core_sqc")) || s.is(item("plutonium_core_sqc")));
    }

    public static boolean isBlanket(ItemStack s) {
        return !s.isEmpty() && (s.is(item("breeder_blanket_sqc")) || s.is(item("neptunium_blanket_sqc")));
    }

    public static boolean accepts(int slot, ItemStack s) {
        return slot < BLANKET_FIRST ? isDriver(s) : slot < OUT_FIRST && isBlanket(s);
    }

    public float load() { return load; }
    public float flux() { return flux; }
    public boolean leak() { return leak; }
    public boolean noSodium() { return noSodium; }
    public boolean noDriver() { return noDriver; }
    public int wearSec() { return wearSec; }

    public int wearPercent() {
        return Math.min(100, (int) (100L * wearSec / BigMachineBlockEntity.WEAR_FULL_SEC));
    }

    public int activeDrivers() {
        int n = 0;
        for (float f : driverLeft) if (f > 0) n++;
        return n;
    }

    public int driverPercent(int i) {
        return Math.round(100f * driverLeft[i] / driverTicks);
    }

    public int blanketPercent(int i) {
        return Math.min(100, Math.round(100f * progress[i] / blanketTicks));
    }

    public void setBlanketProgress(int i, float v) {
        progress[i] = v;
    }

    public int blanketTicks() { return blanketTicks; }

    public void startLeak() {
        leak = true;
    }

    public void repair() {
        leak = false;
        wearSec = 0;
    }

    public int tick(Level level, BlockPos pos, ItemStackHandler items, FluidTank sodium, FluidTank feed, FluidTank steam, int steamRate,
                    boolean second) {
        if (leak) sodium.drain(leakMb, IFluidHandler.FluidAction.EXECUTE);
        noSodium = sodium.getFluidAmount() < sodiumMin;
        load = 0;
        flux = 0;
        int made = noSodium ? 0 : run(items, feed, steam, steamRate);
        if (second) {
            if (made > 0 && wearSec < Integer.MAX_VALUE) wearSec++;
            if (made > 0 && !leak && level.random.nextDouble() < LEAK_CHANCE * (1 + wearSec / (double) WEAR_STEP_SEC)) leak = true;
            if (level instanceof ServerLevel sl) {
                double sv = load * POWER_SV_H + bredCount(items) * BRED_SV_H + (leak ? LEAK_SV_H : 0);
                if (sv > 0) RadiationManager.emit(sl, pos, sv);
            }
        }
        return made;
    }

    private int run(ItemStackHandler items, FluidTank feed, FluidTank steam, int steamRate) {
        int demand = Math.min(steamRate, Math.min(feed.getFluidAmount(), steam.getSpace()));
        if (demand > 0) {
            for (int i = 0; i < DRIVERS; i++) {
                if (driverLeft[i] > 0) continue;
                ItemStack s = items.getStackInSlot(i);
                if (!isDriver(s)) continue;
                driverPu[i] = s.is(item("plutonium_core_sqc"));
                driverLeft[i] = driverTicks;
                items.extractItem(i, 1, false);
            }
        }
        int n = activeDrivers();
        noDriver = n == 0;
        if (n == 0 || demand <= 0) return 0;
        int s = Math.min(demand, n * steamPerDriver);
        FluidStack out = new FluidStack(ModFluids.WET_STEAM.source.get(), s);
        if (steam.fill(out, IFluidHandler.FluidAction.SIMULATE) < s) return 0;
        feed.drain(s, IFluidHandler.FluidAction.EXECUTE);
        steam.fill(out, IFluidHandler.FluidAction.EXECUTE);
        float perDriver = s / (float) (n * steamPerDriver);
        float f = 0;
        for (int i = 0; i < DRIVERS; i++) {
            if (driverLeft[i] <= 0) continue;
            f += perDriver * (driverPu[i] ? puPct / 100f : 1f);
            driverLeft[i] = Math.max(0, driverLeft[i] - perDriver);
        }
        load = perDriver * n / DRIVERS;
        flux = f / DRIVERS;
        breed(items);
        return s;
    }

    private void breed(ItemStackHandler items) {
        for (int b = 0; b < BLANKETS; b++) {
            ItemStack in = items.getStackInSlot(BLANKET_FIRST + b);
            if (!isBlanket(in)) {
                progress[b] = 0;
                continue;
            }
            progress[b] = Math.min(blanketTicks, progress[b] + flux);
            if (progress[b] < blanketTicks) continue;
            ItemStack bred = new ItemStack(item(in.is(item("neptunium_blanket_sqc")) ? "bred_neptunium_sqc" : "bred_blanket_sqc"));
            if (!addOut(items, bred, true)) continue;
            items.extractItem(BLANKET_FIRST + b, 1, false);
            addOut(items, bred, false);
            progress[b] = 0;
        }
    }

    private static boolean addOut(ItemStackHandler items, ItemStack stack, boolean simulate) {
        for (int o = 0; o < OUT_SLOTS; o++) {
            ItemStack cur = items.getStackInSlot(OUT_FIRST + o);
            if (cur.isEmpty()) {
                if (!simulate) items.setStackInSlot(OUT_FIRST + o, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(cur, stack) && cur.getCount() < cur.getMaxStackSize()) {
                if (!simulate) {
                    ItemStack grown = cur.copy();
                    grown.grow(stack.getCount());
                    items.setStackInSlot(OUT_FIRST + o, grown);
                }
                return true;
            }
        }
        return false;
    }

    private static int bredCount(ItemStackHandler items) {
        int n = 0;
        ItemStack probe = new ItemStack(item("bred_blanket_sqc")), probeNp = new ItemStack(item("bred_neptunium_sqc"));
        for (int o = 0; o < OUT_SLOTS; o++) {
            ItemStack s = items.getStackInSlot(OUT_FIRST + o);
            if (ItemStack.isSameItem(s, probe) || ItemStack.isSameItem(s, probeNp)) n += s.getCount();
        }
        return n;
    }

    public static ItemStack insert(ItemStackHandler items, ItemStack stack) {
        int from = isDriver(stack) ? 0 : isBlanket(stack) ? BLANKET_FIRST : -1;
        if (from < 0) return stack;
        int to = from == 0 ? BLANKET_FIRST : OUT_FIRST;
        ItemStack rest = stack.copy();
        for (int i = from; i < to && !rest.isEmpty(); i++) rest = items.insertItem(i, rest, false);
        return rest;
    }

    public static int outputCount(ItemStackHandler items) {
        int n = 0;
        for (int o = 0; o < OUT_SLOTS; o++) n += items.getStackInSlot(OUT_FIRST + o).getCount();
        return n;
    }

    public void save(CompoundTag tag) {
        ListTag d = new ListTag(), p = new ListTag();
        for (int i = 0; i < DRIVERS; i++) {
            CompoundTag c = new CompoundTag();
            c.putFloat("left", driverLeft[i]);
            c.putBoolean("pu", driverPu[i]);
            d.add(c);
        }
        for (float f : progress) {
            CompoundTag c = new CompoundTag();
            c.putFloat("v", f);
            p.add(c);
        }
        tag.put("drivers", d);
        tag.put("blankets", p);
        tag.putBoolean("leak", leak);
        tag.putInt("wear", wearSec);
    }

    public void load(CompoundTag tag) {
        ListTag d = tag.getList("drivers", Tag.TAG_COMPOUND), p = tag.getList("blankets", Tag.TAG_COMPOUND);
        for (int i = 0; i < DRIVERS && i < d.size(); i++) {
            driverLeft[i] = d.getCompound(i).getFloat("left");
            driverPu[i] = d.getCompound(i).getBoolean("pu");
        }
        for (int i = 0; i < BLANKETS && i < p.size(); i++) progress[i] = p.getCompound(i).getFloat("v");
        leak = tag.getBoolean("leak");
        wearSec = tag.getInt("wear");
    }
}
