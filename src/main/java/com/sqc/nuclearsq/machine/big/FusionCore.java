package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.CoolantTypes;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import com.sqc.nuclearsq.machine.MachineEnergy;

import java.util.Map;

public final class FusionCore {
    public static final int COLD = 0, HEATING = 1, PLASMA = 2, DISRUPTED = 3;
    public static final String NO_FUEL = "no_fuel", NO_COOLANT = "no_coolant", ASH_FULL = "ash_full", WALL_WORN = "wall_worn";
    public static final int INPUTS = 4, SLOTS = 8;
    public static final int DISRUPT_TICKS = 200;
    public record Upgrade(String item, String key) {}
    public static final java.util.List<Upgrade> UPGRADES = java.util.List.of(new Upgrade("plasma_optimizer_sqc", "plasma_optimizer"));
    public static final int MAX_MK = 150;

    private final BigMachine machine;
    private final MachineEnergy out;
    private final int[] upgrades = new int[UPGRADES.size()];

    private int phase = COLD;
    private long charge, shot, owed;
    private int burnLeft, lastDraw;
    private int burnSetting = 100, burnNow, wallHeat, disruptLeft, pelletTimer, lastNet, lastGross;
    private long wear;
    private int dAcc, tAcc, heAcc;
    private boolean ddMode;
    private String reason = "";

    public FusionCore(BigMachine machine, Runnable changed) {
        this.machine = machine;
        this.out = new MachineEnergy(Math.max(1, machine.extra("out_buffer")), 0, Integer.MAX_VALUE).onChange(changed);
    }

    public static boolean handles(BigMachine machine) {
        return machine == BigMachine.FUSION_REACTOR;
    }

    public MachineEnergy out() { return out; }
    public int phase() { return phase; }
    public int burnNow() { return burnNow; }
    public int burnSetting() { return burnSetting; }
    public int wallHeat() { return wallHeat; }
    public boolean ddMode() { return ddMode; }
    public int lastNet() { return lastNet; }
    public int lastGross() { return lastGross; }
    public int upgradeCount(int index) { return upgrades[index]; }
    public int lastDraw() { return lastDraw; }
    public int burnLeft() { return phase == PLASMA ? burnLeft : 0; }

    public long ignitionCost() {
        int pct = 100;
        for (int i = 0; i < upgrades.length; i++) pct += upgrades[i] * machine.extra(UPGRADES.get(i).key() + "_cost_pct");
        return (long) machine.extra("ignition_fe") * pct / 100;
    }

    public int fullPower() {
        return (int) (ignitionCost() * (machine.extra("return_pct") + upgradeBonusPercent()) / 100 / Math.max(1, machine.extra("burn_ticks")));
    }

    public int upgradeBonusPercent() {
        int sum = 0;
        for (int i = 0; i < upgrades.length; i++) sum += upgrades[i] * machine.extra(UPGRADES.get(i).key() + "_gross_pct");
        return sum;
    }
    public String reason() { return reason; }

    public void setBurn(int percent) {
        burnSetting = Mth.clamp(percent, 0, 100);
    }

    public int wearPercent() {
        return (int) Math.min(100, 100 * wear / Math.max(1, machine.extra("wall_wear_ticks")));
    }

    public int ignitionPercent() {
        return (int) Math.min(100, 100 * charge / Math.max(1, ignitionCost()));
    }

    public int chargePermille() {
        return (int) Math.min(1000, 1000 * charge / Math.max(1, ignitionCost()));
    }

    public int temperature() {
        return switch (phase) {
            case PLASMA -> Math.round(MAX_MK * (0.35f + 0.65f * burnNow / 100f));
            case HEATING -> Math.round(MAX_MK * 0.35f * ignitionPercent() / 100f);
            default -> 0;
        };
    }

    public int signalOut() {
        return phase == PLASMA ? Math.max(1, Math.round(15f * burnNow / 100f)) : phase == HEATING ? Math.min(7, ignitionPercent() * 8 / 100) : 0;
    }

    public boolean burning() {
        return phase == PLASMA;
    }

    public String state() {
        if (phase == DISRUPTED) return "disrupted";
        return reason;
    }

    public int tick(ServerLevel sl, BlockPos pos, Map<String, FluidTank> tanks, MachineEnergy energy, ItemStackHandler items,
                    boolean enabled, int signalIn) {
        FluidTank d = tanks.get("deuterium_in"), t = tanks.get("tritium_io"), he = tanks.get("helium_out");
        FluidTank cin = tanks.get("coolant_in"), cout = tanks.get("coolant_out");
        reason = "";
        lastNet = 0;
        lastGross = 0;
        burnNow = 0;
        lastDraw = 0;
        if (owed > 0) owed -= out.add((int) Math.min(owed, Integer.MAX_VALUE));
        if (d == null || t == null || he == null || cin == null || cout == null) return 0;
        if (!enabled) {
            if (phase == PLASMA || phase == HEATING) quench(false);
            wallHeat = Math.max(0, wallHeat - 1);
            return 0;
        }
        if (phase == DISRUPTED) {
            if (--disruptLeft <= 0) phase = COLD;
            wallHeat = Math.max(0, wallHeat - 2);
            return 0;
        }
        absorbUpgrades(items);
        int target = signalIn > 0 ? Math.round(100f * Math.min(15, signalIn) / 15f) : burnSetting;
        if (wear >= machine.extra("wall_wear_ticks") && !swapTile(items)) {
            reason = WALL_WORN;
            if (phase == PLASMA) disrupt(sl, pos);
            return 0;
        }
        int fuel = machine.extra("fuel_cmb_t");
        if (phase == COLD || phase == HEATING) {
            boolean haveFuel = d.getFluidAmount() >= 3 && (t.getFluidAmount() >= 3 || d.getFluidAmount() >= 6);
            if (!haveFuel) {
                reason = NO_FUEL;
                phase = COLD;
                return 0;
            }
            if (cin.getFluidAmount() < machine.port("coolant_in").rate() / 4 || CoolantTypes.heatFactor(cin.getFluid()) <= 0) {
                reason = NO_COOLANT;
                phase = COLD;
                return 0;
            }
            if (target <= 0) {
                phase = COLD;
                return 0;
            }
            int draw = Math.min(machine.extra("ignition_fe_t"), (int) Math.min(Integer.MAX_VALUE, ignitionCost() - charge));
            draw = energy.drain(draw);
            if (draw <= 0) {
                reason = "no_power";
                return 0;
            }
            charge += draw;
            lastDraw = draw;
            phase = HEATING;
            if (charge >= ignitionCost()) {
                shot = charge;
                burnLeft = machine.extra("burn_ticks");
                charge = 0;
                phase = PLASMA;
                pelletTimer = 0;
                rollUpgradeDrops(sl, pos, items);
            }
            return 1;
        }
        if (target <= 0) {
            quench(false);
            return 0;
        }
        boolean tritiumOk = t.getFluidAmount() >= 3;
        ddMode = !tritiumOk;
        int pct = ddMode ? Math.min(target, machine.extra("dd_pct")) : target;
        dAcc += Math.round((float) fuel * pct / 100f * (ddMode ? 2 : 1));
        if (!ddMode) tAcc += Math.round((float) fuel * pct / 100f);
        heAcc += Math.round((float) fuel * pct / 100f);
        if (ddMode) tAcc -= Math.round((float) machine.extra("dd_tritium_cmb_t") * pct / 100f);
        int needD = dAcc / 100, needT = Math.max(0, tAcc / 100), makeT = tAcc < 0 ? -tAcc / 100 : 0, makeHe = heAcc / 100;
        if (d.getFluidAmount() < needD || t.getFluidAmount() < needT) {
            reason = NO_FUEL;
            quench(true);
            return 0;
        }
        if (t.getSpace() < makeT) {
            reason = ASH_FULL;
            quench(true);
            return 0;
        }
        d.drain(needD, IFluidHandler.FluidAction.EXECUTE);
        dAcc -= needD * 100;
        if (needT > 0) {
            t.drain(needT, IFluidHandler.FluidAction.EXECUTE);
            tAcc -= needT * 100;
        }
        if (makeT > 0) {
            t.fill(new FluidStack(ModFluids.TRITIUM.source.get(), makeT), IFluidHandler.FluidAction.EXECUTE);
            tAcc += makeT * 100;
        }
        if (makeHe > 0) {
            he.fill(new FluidStack(ModFluids.HELIUM.source.get(), makeHe), IFluidHandler.FluidAction.EXECUTE);
            heAcc -= makeHe * 100;
        }
        int heat = Math.round(machine.port("coolant_in").rate() * pct / 100f);
        int f = CoolantTypes.heatFactor(cin.getFluid());
        int cooled = f <= 0 ? 0 : Math.min(heat, f * Math.min(cin.getFluidAmount(), cout.getSpace()));
        if (cooled > 0) {
            int used = (cooled + f - 1) / f;
            cin.drain(used, IFluidHandler.FluidAction.EXECUTE);
            cout.fill(new FluidStack(ModFluids.HOT_COOLANT.source.get(), used), IFluidHandler.FluidAction.EXECUTE);
        }
        if (cooled < heat) {
            reason = NO_COOLANT;
            wallHeat += 1 + 3 * (heat - cooled) / Math.max(1, heat);
            if (wallHeat >= machine.extra("wall_heat_max")) {
                disrupt(sl, pos);
                return 0;
            }
        } else {
            wallHeat = Math.max(0, wallHeat - 1);
        }
        lastNet = lastGross = (int) (shot * (machine.extra("return_pct") + upgradeBonusPercent()) / 100 / Math.max(1, machine.extra("burn_ticks")) * pct / 100);
        owed += lastNet;
        wear += pct;
        burnNow = pct;
        if (++pelletTimer >= machine.extra("pellet_ticks")) {
            pelletTimer = 0;
            if (takePellet(items)) {
                t.fill(new FluidStack(ModFluids.TRITIUM.source.get(), machine.extra("tritium_per_pellet_mb")), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        if (sl.getGameTime() % 20 == 0) {
            RadiationManager.emit(sl, pos, RadiationValues.REACTOR_POWER_SV_H * machine.extra("radiation_pct") / 100.0 * pct / 100.0);
        }
        if (--burnLeft <= 0) quench(false);
        return Math.max(1, pct);
    }

    private void quench(boolean keepReason) {
        phase = COLD;
        charge = 0;
        if (!keepReason) reason = "";
    }

    private void disrupt(ServerLevel sl, BlockPos pos) {
        phase = DISRUPTED;
        disruptLeft = DISRUPT_TICKS;
        charge = 0;
        wear += machine.extra("wall_wear_ticks") / 10;
        RadiationManager.emit(sl, pos, RadiationValues.REACTOR_LEAK_SV_H * 20);
        wallHeat = 0;
    }

    private void absorbUpgrades(ItemStackHandler items) {
        for (int u = 0; u < upgrades.length; u++) {
            Item it = item(UPGRADES.get(u).item());
            for (int i = 0; i < INPUTS && upgrades[u] < machine.extra(UPGRADES.get(u).key() + "_max"); i++) {
                if (items.getStackInSlot(i).is(it)) {
                    items.extractItem(i, 1, false);
                    upgrades[u]++;
                    i--;
                }
            }
        }
    }

    private void rollUpgradeDrops(ServerLevel sl, BlockPos pos, ItemStackHandler items) {
        for (int u = 0; u < UPGRADES.size(); u++) {
            if (sl.getRandom().nextInt(1000) < machine.extra(UPGRADES.get(u).key() + "_chance_permille")) giveUpgrade(sl, pos, items, u);
        }
    }

    public void giveUpgrade(ServerLevel sl, BlockPos pos, ItemStackHandler items, int index) {
        ItemStack stack = new ItemStack(item(UPGRADES.get(index).item()));
        for (int i = INPUTS; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack in = items.getStackInSlot(i);
            if (in.isEmpty()) {
                items.setStackInSlot(i, stack);
                stack = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                in.grow(1);
                items.setStackInSlot(i, in);
                stack = ItemStack.EMPTY;
            }
        }
        if (!stack.isEmpty()) net.minecraft.world.level.block.Block.popResource(sl, pos.above(), stack);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static boolean takePellet(ItemStackHandler items) {
        Item pellet = item("lithium_pellet_sqc");
        for (int i = 0; i < INPUTS; i++) {
            if (items.getStackInSlot(i).is(pellet)) {
                items.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    private boolean swapTile(ItemStackHandler items) {
        Item fresh = item("first_wall_tile_sqc"), worn = item("worn_wall_tile_sqc");
        int src = -1, dst = -1;
        for (int i = 0; i < INPUTS && src < 0; i++) if (items.getStackInSlot(i).is(fresh)) src = i;
        if (src < 0) return false;
        for (int i = INPUTS; i < SLOTS && dst < 0; i++) {
            if (items.insertItem(i, new ItemStack(worn), true).isEmpty()) dst = i;
        }
        if (dst < 0) return false;
        items.extractItem(src, 1, false);
        items.insertItem(dst, new ItemStack(worn), false);
        wear = 0;
        return true;
    }

    public static IItemHandler view(ItemStackHandler items, boolean input) {
        return new IItemHandler() {
            @Override public int getSlots() { return items.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return input && slot < INPUTS ? items.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return !input && slot >= INPUTS ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return input && slot < INPUTS && items.isItemValid(slot, stack); }
        };
    }

    public static boolean valid(int slot, ItemStack stack) {
        if (slot >= INPUTS) return stack.is(item("worn_wall_tile_sqc"));
        if (stack.is(item("lithium_pellet_sqc")) || stack.is(item("first_wall_tile_sqc"))) return true;
        for (Upgrade u : UPGRADES) if (stack.is(item(u.item()))) return true;
        return false;
    }

    public static IEnergyStorage receiveOnly(IEnergyStorage s) {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int n, boolean sim) { return s.receiveEnergy(n, sim); }
            @Override public int extractEnergy(int n, boolean sim) { return 0; }
            @Override public int getEnergyStored() { return s.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return s.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return true; }
        };
    }

    public void save(CompoundTag tag) {
        CompoundTag f = new CompoundTag();
        f.putInt("phase", phase);
        f.putLong("charge", charge);
        f.putInt("burn", burnSetting);
        f.putInt("wallHeat", wallHeat);
        f.putInt("disruptLeft", disruptLeft);
        f.putInt("pelletTimer", pelletTimer);
        f.putLong("wear", wear);
        f.putInt("dAcc", dAcc);
        f.putInt("tAcc", tAcc);
        f.putInt("heAcc", heAcc);
        f.putInt("out", out.getEnergyStored());
        f.putLong("shot", shot);
        f.putLong("owed", owed);
        f.putInt("burnLeft", burnLeft);
        for (int i = 0; i < upgrades.length; i++) f.putInt("up_" + UPGRADES.get(i).key(), upgrades[i]);
        tag.put("fusion", f);
    }

    public void load(CompoundTag tag) {
        CompoundTag f = tag.getCompound("fusion");
        phase = f.getInt("phase");
        charge = f.getLong("charge");
        burnSetting = f.contains("burn") ? f.getInt("burn") : 100;
        wallHeat = f.getInt("wallHeat");
        disruptLeft = f.getInt("disruptLeft");
        pelletTimer = f.getInt("pelletTimer");
        wear = f.getLong("wear");
        dAcc = f.getInt("dAcc");
        tAcc = f.getInt("tAcc");
        heAcc = f.getInt("heAcc");
        out.setStored(f.getInt("out"));
        shot = f.getLong("shot");
        owed = f.getLong("owed");
        burnLeft = f.getInt("burnLeft");
        for (int i = 0; i < upgrades.length; i++) upgrades[i] = f.getInt("up_" + UPGRADES.get(i).key());
    }
}
