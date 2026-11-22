package com.sqc.nuclearsq.storage;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WifiAdapterBlockEntity extends BlockEntity {
    private static final int CHARGE_INTERVAL = 10;

    private static final class Buffer extends EnergyStorage {
        Buffer(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        int drain(int n) {
            int d = Math.max(0, Math.min(energy, n));
            energy -= d;
            return d;
        }

        void set(int e) {
            energy = Math.max(0, Math.min(capacity, e));
        }
    }

    private static final Map<ResourceKey<Level>, Set<WifiAdapterBlockEntity>> LOADED = new ConcurrentHashMap<>();
    private static final int MESH_LIMIT = 64;

    private final Buffer energy = new Buffer(WifiValues.adapter().buffer(), WifiValues.adapter().maxReceive());
    private int antennas;
    private boolean enabled = true, online;
    private int charging;

    public WifiAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.WIFI_ADAPTER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forget();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forget();
    }

    private void forget() {
        if (level == null) return;
        Set<WifiAdapterBlockEntity> set = LOADED.get(level.dimension());
        if (set != null) set.remove(this);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public int capacity() {
        return energy.getMaxEnergyStored();
    }

    public int antennas() {
        return antennas;
    }

    public int range() {
        return WifiValues.adapter().rangeFor(antennas);
    }

    public boolean unlimited() {
        return range() < 0;
    }

    private double reach() {
        return unlimited() ? Double.POSITIVE_INFINITY : range();
    }

    public Component rangeText() {
        return unlimited() ? Component.translatable("message.nuclearsq.wifi.range_unlimited")
                : Component.translatable("message.nuclearsq.wifi.range", range());
    }

    public static Item module(int level) {
        return (level <= 2 ? StorageRegistry.WIFI_ANTENNA : level == 3 ? StorageRegistry.WIFI_ANTENNA_SECTOR
                : level == 4 ? StorageRegistry.WIFI_ANTENNA_DISH : StorageRegistry.WIFI_ANTENNA_QUANTUM).get();
    }

    public static boolean isModule(ItemStack stack) {
        return stack.is(StorageRegistry.WIFI_ANTENNA.get()) || stack.is(StorageRegistry.WIFI_ANTENNA_SECTOR.get())
                || stack.is(StorageRegistry.WIFI_ANTENNA_DISH.get()) || stack.is(StorageRegistry.WIFI_ANTENNA_QUANTUM.get());
    }

    public boolean addAntenna() {
        if (antennas >= WifiValues.adapter().maxAntennas()) return false;
        antennas++;
        setChanged();
        return true;
    }

    public int takeAntennas() {
        int n = antennas;
        antennas = 0;
        return n;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setChanged();
    }

    public boolean online() {
        return online;
    }

    public int charging() {
        return charging;
    }

    public boolean inRange(@Nullable Level l, Vec3 p) {
        if (unlimited()) return true;
        if (l != level) return false;
        double r = range();
        return level == null ? Vec3.atCenterOf(worldPosition).distanceToSqr(p) <= r * r
                : com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, Vec3.atCenterOf(worldPosition), p) <= r * r;
    }

    private boolean linksTo(WifiAdapterBlockEntity o) {
        double r = Math.min(reach(), o.reach());
        return level == null ? Vec3.atCenterOf(worldPosition).distanceToSqr(Vec3.atCenterOf(o.worldPosition)) <= r * r
                : com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, worldPosition, o.worldPosition) <= r * r;
    }

    public boolean reaches(Vec3 p) {
        return reaches(level, p);
    }

    public boolean reaches(@Nullable Level l, Vec3 p) {
        if (inRange(l, p)) return true;
        if (level == null) return false;
        Set<WifiAdapterBlockEntity> all = LOADED.get(level.dimension());
        if (all == null) return false;
        Set<WifiAdapterBlockEntity> seen = java.util.HashSet.newHashSet(8);
        ArrayDeque<WifiAdapterBlockEntity> todo = new ArrayDeque<>();
        seen.add(this);
        todo.add(this);
        while (!todo.isEmpty() && seen.size() <= MESH_LIMIT) {
            WifiAdapterBlockEntity a = todo.poll();
            for (WifiAdapterBlockEntity o : all) {
                if (o.isRemoved() || !o.online() || o.level != level || seen.contains(o) || !a.linksTo(o)) continue;
                if (o.inRange(l, p)) return true;
                seen.add(o);
                todo.add(o);
            }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WifiAdapterBlockEntity be) {
        if (level.isClientSide) return;
        WifiValues.Adapter v = WifiValues.adapter();
        boolean was = be.online;
        be.online = be.enabled && be.energy.getEnergyStored() >= v.fePerTick();
        if (be.online) be.energy.drain(v.fePerTick());
        if (be.online && level.getGameTime() % CHARGE_INTERVAL == 0) be.charge(level, v);
        else if (!be.online) be.charging = 0;
        if (be.online != was) be.setChanged();
        int shown = Math.min(be.antennas, WifiAdapterBlock.MAX_SHOWN);
        if (state.getValue(WifiAdapterBlock.ANTENNAS) != shown) level.setBlock(pos, state.setValue(WifiAdapterBlock.ANTENNAS, shown), 3);
    }

    private void charge(Level level, WifiValues.Adapter v) {
        int r = v.chargeRadius();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        int perPlayerBudget = v.chargeRate() * CHARGE_INTERVAL;
        int n = 0;
        for (ServerPlayer sp : com.sqc.nuclearsq.compat.sable.SableCompat.entitiesIn(level, ServerPlayer.class, new AABB(worldPosition).inflate(r), null)) {
            if (com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, sp.position(), c) > (double) r * r) continue;
            Inventory inv = sp.getInventory();
            int budget = perPlayerBudget;
            for (int i = 0; i < inv.getContainerSize() && budget > 0 && energy.getEnergyStored() > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!(s.getItem() instanceof WifiTerminalItem)) continue;
                int room = WifiTerminalItem.capacity() - WifiTerminalItem.energy(s);
                if (room <= 0) continue;
                int give = energy.drain(Math.min(room, Math.min(budget, energy.getEnergyStored())));
                if (give <= 0) continue;
                WifiTerminalItem.charge(s, give);
                budget -= give;
                n++;
            }
        }
        charging = n;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("antennas", antennas);
        tag.putBoolean("enabled", enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        energy.set(tag.getInt("energy"));
        antennas = Math.max(0, Math.min(tag.getInt("antennas"), WifiValues.adapter().maxAntennas()));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }

    @Nullable
    public static WifiAdapterBlockEntity at(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WifiAdapterBlockEntity a ? a : null;
    }
}
