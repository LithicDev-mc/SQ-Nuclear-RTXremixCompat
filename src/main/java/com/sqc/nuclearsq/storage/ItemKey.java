package com.sqc.nuclearsq.storage;

import net.minecraft.world.item.ItemStack;

public final class ItemKey {
    private final ItemStack proto;
    private final int hash;

    private ItemKey(ItemStack proto) {
        this.proto = proto;
        this.hash = ItemStack.hashItemAndComponents(proto);
    }

    public static ItemKey of(ItemStack stack) {
        return stack.isEmpty() ? null : new ItemKey(stack.copyWithCount(1));
    }

    public ItemStack stack(int count) {
        return proto.copyWithCount(count);
    }

    public ItemStack proto() {
        return proto;
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(proto, stack);
    }

    public int maxStack() {
        return proto.getMaxStackSize();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemKey k && k.hash == hash && ItemStack.isSameItemSameComponents(proto, k.proto);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
