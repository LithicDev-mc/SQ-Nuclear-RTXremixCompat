package com.sqc.nuclearsq.robotics;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

public class RobotArmBlockEntity extends BlockEntity implements MenuProvider {
    public static final double[] REACH = {0, 3, 5};
    public static final int[] STEPS = {1, 4, 16, 32, 64};
    public static final int UPGRADE_SLOTS = 4;
    public static final int[] PERIOD = {0, 20, 10};
    public static final int[] COST = {0, 600, 2400};
    public static final int BUFFER = 60000;

    private static final class ArmEnergy extends EnergyStorage {
        ArmEnergy() {
            super(BUFFER, 2000, 0);
        }

        void consume(int fe) {
            energy = Math.max(0, energy - fe);
        }

        void set(int fe) {
            energy = Math.max(0, Math.min(capacity, fe));
        }
    }

    private final ArmEnergy energy = new ArmEnergy();
    public static final int MAX_TARGETS = 4;
    @Nullable private BlockPos source;
    private Direction sourceSide = Direction.UP;
    private final java.util.List<BlockPos> targets = new java.util.ArrayList<>();
    private final java.util.List<Direction> targetSides = new java.util.ArrayList<>();
    private int cur, next;
    public static final int FILTER_SLOTS = 5;
    private final SimpleContainer filters = new SimpleContainer(FILTER_SLOTS);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS);
    private boolean enabled = true;
    private int cooldown;
    private long animStart = -1000;
    private ItemStack carried = ItemStack.EMPTY;
    private net.minecraft.world.item.Item lastItem = net.minecraft.world.item.Items.AIR;
    private int lastMoved;
    private String lastProblem = "";

    public static final String[] PROBLEMS = {"", "paused", "unloaded", "reach", "port", "power", "idle"};

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored();
                case 1 -> enabled ? 1 : 0;
                case 2 -> lastMoved;
                case 3 -> Math.max(0, java.util.Arrays.asList(PROBLEMS).indexOf(lastProblem));
                case 4 -> source != null ? 1 : 0;
                case 5 -> source != null ? source.getX() : 0;
                case 6 -> source != null ? source.getY() : 0;
                case 7 -> source != null ? source.getZ() : 0;
                case 8 -> targets.size();
                case 9 -> target() != null ? target().getX() : 0;
                case 10 -> target() != null ? target().getY() : 0;
                case 11 -> target() != null ? target().getZ() : 0;
                case 12 -> tier();
                case 13 -> sourceSide.get3DDataValue();
                case 14 -> targets.isEmpty() ? 0 : targetSides.get(cur).get3DDataValue();
                case 15 -> amount();
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
    public static final int DATA_SIZE = 16;

    public RobotArmBlockEntity(BlockPos pos, BlockState state) {
        super(RoboticsRegistry.ARM_BE.get(), pos, state);
        filters.addListener(c -> setChanged());
        upgrades.addListener(c -> setChanged());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RobotArmMenu(id, inv, this, filters, upgrades, data);
    }

    @Nullable
    public BlockPos source() {
        return source;
    }

    @Nullable
    public BlockPos target() {
        return targets.isEmpty() ? null : targets.get(cur);
    }

    public java.util.List<BlockPos> targets() {
        return java.util.List.copyOf(targets);
    }

    public SimpleContainer filters() {
        return filters;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public int amount() {
        int n = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) if (upgrades.getItem(i).is(RoboticsRegistry.ARM_UPGRADE.get())) n++;
        return STEPS[n];
    }

    private int tier() {
        return getBlockState().getBlock() instanceof RobotArmBlock a ? a.tier : 1;
    }

    public EnergyStorage energy() {
        return energy;
    }

    public void setSource(BlockPos p, Direction side) {
        source = p.immutable();
        sourceSide = side;
        changedLinks();
    }

    public boolean setTarget(BlockPos p, Direction side) {
        int i = targets.indexOf(p);
        if (i >= 0) {
            targets.remove(i);
            targetSides.remove(i);
        } else {
            if (targets.size() >= MAX_TARGETS) {
                targets.remove(0);
                targetSides.remove(0);
            }
            targets.add(p.immutable());
            targetSides.add(side);
        }
        cur = next = 0;
        changedLinks();
        return i < 0;
    }

    public void clearLinks() {
        source = null;
        clearTarget();
    }

    public void clearSource() {
        source = null;
        changedLinks();
    }

    public void clearTarget() {
        targets.clear();
        targetSides.clear();
        cur = next = 0;
        changedLinks();
    }

    private void changedLinks() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public long animStart() {
        return animStart;
    }

    public ItemStack carried() {
        return carried;
    }

    public int animTicks() {
        return Math.max(6, PERIOD[tier()] - 2);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != 1) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            animStart = level.getGameTime();
            carried = param > 0 ? new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(param - 1)) : ItemStack.EMPTY;
        }
        return true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        if (source != null) tag.putLong("src", source.asLong());
        if (target() != null) tag.putLong("dst", target().asLong());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setChanged();
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public int lastMoved() {
        return lastMoved;
    }

    public String problem() {
        return lastProblem;
    }

    public void setFilter(ItemStack st) {
        if (st.isEmpty()) {
            clearFilters();
            return;
        }
        int free = -1;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (ItemStack.isSameItem(f, st)) return;
            if (f.isEmpty() && free < 0) free = i;
        }
        filters.setItem(free >= 0 ? free : 0, st.copyWithCount(1));
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
            if (ItemStack.isSameItem(f, st)) return true;
        }
        return !any;
    }

    public boolean inReach(BlockPos p) {
        return p.distSqr(worldPosition) <= REACH[tier()] * REACH[tier()] + 0.5;
    }

    public Component status() {
        Component src = source == null ? Component.translatable("message.nuclearsq.robot_arm.none") : Component.literal(source.toShortString());
        Component dst = target() == null ? Component.translatable("message.nuclearsq.robot_arm.none")
                : Component.literal(target().toShortString() + (targets.size() > 1 ? " (+" + (targets.size() - 1) + ")" : ""));
        Component f = filters.isEmpty() ? Component.translatable("message.nuclearsq.robot_arm.all") : Component.literal(filters.getItem(0).isEmpty() ? "..." : filters.getItem(0).getHoverName().getString());
        Component state = lastProblem.isEmpty() ? Component.translatable("message.nuclearsq.robot_arm.ok", lastMoved)
                : Component.translatable("message.nuclearsq.robot_arm.problem." + lastProblem);
        return Component.translatable("message.nuclearsq.robot_arm.status", src, dst, f, energy.getEnergyStored(), BUFFER, state);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl) || !enabled || source == null || targets.isEmpty()) return;
        if (--cooldown > 0) return;
        cooldown = PERIOD[tier()];
        if (sl.hasNeighborSignal(worldPosition)) {
            lastProblem = "paused";
            return;
        }
        if (energy.getEnergyStored() < COST[tier()]) {
            lastProblem = "power";
            return;
        }
        int moved = 0;
        lastProblem = "idle";
        for (int i = 0; i < targets.size() && moved == 0; i++) {
            int k = (next + i) % targets.size();
            BlockPos target = targets.get(k);
            if (!sl.isLoaded(source) || !sl.isLoaded(target)) {
                lastProblem = "unloaded";
                continue;
            }
            if (!inReach(source) || !inReach(target)) {
                lastProblem = "reach";
                continue;
            }
            IItemHandler from = sl.getCapability(Capabilities.ItemHandler.BLOCK, source, sourceSide);
            IItemHandler to = sl.getCapability(Capabilities.ItemHandler.BLOCK, target, targetSides.get(k));
            if (from == null || to == null) {
                lastProblem = "port";
                continue;
            }
            moved = move(from, to, amount());
            if (moved > 0) {
                next = (k + 1) % targets.size();
                if (k != cur) {
                    cur = k;
                    changedLinks();
                }
            }
        }
        lastMoved = moved;
        if (moved > 0) {
            lastProblem = "";
            energy.consume(COST[tier()]);
            setChanged();
            sl.blockEvent(worldPosition, getBlockState().getBlock(), 1, lastItem == net.minecraft.world.item.Items.AIR ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(lastItem) + 1);
        }
    }

    int move(IItemHandler from, IItemHandler to, int limit) {
        int total = 0;
        for (int slot = 0; slot < from.getSlots() && total < limit; slot++) {
            ItemStack peek = from.extractItem(slot, limit - total, true);
            if (peek.isEmpty() || !matches(peek)) continue;
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(to, peek, true);
            int fits = peek.getCount() - rest.getCount();
            if (fits <= 0) continue;
            ItemStack taken = from.extractItem(slot, fits, false);
            if (taken.isEmpty()) continue;
            ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(to, taken, false);
            if (!left.isEmpty()) from.insertItem(slot, left, false);
            if (total == 0) lastItem = taken.getItem();
            total += taken.getCount() - left.getCount();
        }
        return total;
    }

    private static byte[] toBytes(java.util.List<Direction> sides) {
        byte[] b = new byte[sides.size()];
        for (int i = 0; i < b.length; i++) b[i] = (byte) sides.get(i).get3DDataValue();
        return b;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putBoolean("enabled", enabled);
        if (source != null) {
            tag.putLong("src", source.asLong());
            tag.putByte("srcSide", (byte) sourceSide.get3DDataValue());
        }
        if (!targets.isEmpty()) {
            tag.putLongArray("dsts", targets.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putByteArray("dstSides", toBytes(targetSides));
            tag.putInt("next", next);
        }
        if (!filters.isEmpty()) tag.put("filters", filters.createTag(reg));
        if (!upgrades.isEmpty()) tag.put("upgrades", upgrades.createTag(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        energy.set(tag.getInt("energy"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        source = tag.contains("src") ? BlockPos.of(tag.getLong("src")) : null;
        sourceSide = Direction.from3DDataValue(tag.getByte("srcSide"));
        targets.clear();
        targetSides.clear();
        if (tag.contains("dst")) targets.add(BlockPos.of(tag.getLong("dst")));
        long[] dsts = tag.getLongArray("dsts");
        byte[] sides = tag.getByteArray("dstSides");
        for (int i = 0; i < dsts.length; i++) targets.add(BlockPos.of(dsts[i]));
        for (int i = 0; i < targets.size(); i++) targetSides.add(Direction.from3DDataValue(i < sides.length ? sides[i] : 1));
        next = targets.isEmpty() ? 0 : Math.floorMod(tag.getInt("next"), targets.size());
        cur = 0;
        filters.clearContent();
        upgrades.clearContent();
        if (tag.contains("upgrades", net.minecraft.nbt.Tag.TAG_LIST)) upgrades.fromTag(tag.getList("upgrades", net.minecraft.nbt.Tag.TAG_COMPOUND), reg);
        if (tag.contains("filters", net.minecraft.nbt.Tag.TAG_LIST)) filters.fromTag(tag.getList("filters", net.minecraft.nbt.Tag.TAG_COMPOUND), reg);
    }
}
