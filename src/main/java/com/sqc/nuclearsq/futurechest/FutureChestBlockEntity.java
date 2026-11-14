package com.sqc.nuclearsq.futurechest;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FutureChestBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int BASE_SIZE = 32, MAX_UPGRADES = 2, MAX_SIZE = BASE_SIZE << MAX_UPGRADES, UPGRADE_SLOTS = 3;
    public static final int SLOT_CAPACITY = 0, SLOT_MAGNET = 1, SLOT_SORTER = 2;
    public static final double MAGNET_RANGE = 6.0;
    public static final int THROW_TICKS = 8, UNFOLD_TICKS = 20, ANIM_TICKS = THROW_TICKS + UNFOLD_TICKS + 2;
    public static final long NONE = Long.MIN_VALUE;

    private final NonNullList<ItemStack> items = NonNullList.withSize(MAX_SIZE, ItemStack.EMPTY);
    private final net.minecraft.world.SimpleContainer upgrades = new net.minecraft.world.SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            FutureChestBlockEntity.this.setChanged();
        }
    };
    private long animStart = NONE;
    private Vec3 from = Vec3.ZERO;
    public boolean landedSound;
    public boolean taken;

    public FutureChestBlockEntity(BlockPos pos, BlockState st) {
        super(FutureChestRegistry.BLOCK_ENTITY.get(), pos, st);
    }

    public long animStart() {
        return animStart;
    }

    public Vec3 from() {
        return from;
    }

    public void startAnim(Vec3 start) {
        if (level == null) return;
        animStart = level.getGameTime();
        from = start;
        landedSound = false;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void finishAnim() {
        animStart = NONE;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        ContainerHelper.saveAllItems(tag, items, regs);
        CompoundTag up = new CompoundTag();
        ContainerHelper.saveAllItems(up, upgradeList(), regs);
        tag.put("Upgrades", up);
        saveAnim(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        if (tag.contains("Items")) {
            items.clear();
            ContainerHelper.loadAllItems(tag, items, regs);
        }
        if (tag.contains("Upgrades")) {
            NonNullList<ItemStack> list = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Upgrades"), list, regs);
            for (int i = 0; i < UPGRADE_SLOTS; i++) upgrades.setItem(i, list.get(i));
        }
        animStart = tag.contains("anim_start") ? tag.getLong("anim_start") : NONE;
        from = new Vec3(tag.getDouble("fx"), tag.getDouble("fy"), tag.getDouble("fz"));
    }

    private void saveAnim(CompoundTag tag) {
        if (animStart == NONE) return;
        tag.putLong("anim_start", animStart);
        tag.putDouble("fx", from.x);
        tag.putDouble("fy", from.y);
        tag.putDouble("fz", from.z);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        CompoundTag tag = new CompoundTag();
        saveAnim(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.fromItems(items));
        builder.set(FutureChestRegistry.UPGRADES.get(), ItemContainerContents.fromItems(upgradeList()));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        items.clear();
        input.getOrDefault(FutureChestRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).copyInto(items);
        NonNullList<ItemStack> up = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        input.getOrDefault(FutureChestRegistry.UPGRADES.get(), ItemContainerContents.EMPTY).copyInto(up);
        for (int i = 0; i < UPGRADE_SLOTS; i++) upgrades.setItem(i, up.get(i));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
        tag.remove("Upgrades");
    }

    private NonNullList<ItemStack> upgradeList() {
        NonNullList<ItemStack> list = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < UPGRADE_SLOTS; i++) list.set(i, upgrades.getItem(i));
        return list;
    }

    public net.minecraft.world.SimpleContainer upgrades() {
        return upgrades;
    }

    public boolean hasUpgrade(QuantumUpgradeItem.Kind kind) {
        return upgrades.getItem(kind.ordinal()).getItem() instanceof QuantumUpgradeItem u && u.kind == kind;
    }

    public int capacity() {
        ItemStack st = upgrades.getItem(SLOT_CAPACITY);
        int n = st.getItem() instanceof QuantumUpgradeItem u && u.kind == QuantumUpgradeItem.Kind.CAPACITY ? Math.min(MAX_UPGRADES, st.getCount()) : 0;
        return BASE_SIZE << n;
    }

    public boolean isEmptyFrom(int from) {
        for (int i = from; i < MAX_SIZE; i++) if (!items.get(i).isEmpty()) return false;
        return true;
    }

    @Override
    public int getContainerSize() {
        return capacity();
    }

    @Override
    public boolean isEmpty() {
        return isEmptyFrom(0);
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
        return slot < capacity() && !(stack.getItem() instanceof FutureChestItem);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        upgrades.clearContent();
        setChanged();
    }

    @Override
    public void startOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator()) {
            if (hasUpgrade(QuantumUpgradeItem.Kind.SORTER)) sort();
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof FutureChestItem) return stack;
        ItemStack rest = stack.copy();
        int cap = capacity();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < cap && !rest.isEmpty(); i++) {
                ItemStack in = items.get(i);
                if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest)) {
                    int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                    if (move > 0) { in.grow(move); rest.shrink(move); }
                } else if (pass == 1 && in.isEmpty()) {
                    int move = Math.min(rest.getCount(), rest.getMaxStackSize());
                    items.set(i, rest.split(move));
                }
            }
        }
        if (rest.getCount() != stack.getCount()) setChanged();
        return rest;
    }

    public void serverTick() {
        if (level == null || level.isClientSide || level.getGameTime() % 8 != 3 || !hasUpgrade(QuantumUpgradeItem.Kind.MAGNET)) return;
        if (!getBlockState().getValue(FutureChestBlock.UNFOLDED)) return;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition).inflate(MAGNET_RANGE);
        for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            if (e.isRemoved() || e.hasPickUpDelay()) continue;
            ItemStack rest = insert(e.getItem());
            if (rest.getCount() != e.getItem().getCount()) {
                if (rest.isEmpty()) e.discard(); else e.setItem(rest);
            }
        }
    }

    public void sort() {
        int cap = capacity();
        java.util.List<ItemStack> merged = new java.util.ArrayList<>();
        for (int i = 0; i < cap; i++) {
            ItemStack s = items.get(i).copy();
            for (ItemStack m : merged) {
                if (s.isEmpty()) break;
                if (ItemStack.isSameItemSameComponents(m, s)) {
                    int move = Math.min(s.getCount(), m.getMaxStackSize() - m.getCount());
                    if (move > 0) { m.grow(move); s.shrink(move); }
                }
            }
            if (!s.isEmpty()) merged.add(s);
        }
        merged.sort(java.util.Comparator.comparing((ItemStack s) -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed()));
        for (int i = 0; i < cap; i++) items.set(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.nuclearsq.future_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FutureChestMenu(id, inv, this, upgrades, capacity());
    }
}
