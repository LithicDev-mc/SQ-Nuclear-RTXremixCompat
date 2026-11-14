package com.sqc.nuclearsq.economy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Coins {
    private Coins() {}

    private static Item[] items() {
        return new Item[]{EconomyRegistry.COIN_BAR.get(), EconomyRegistry.COIN_ROLL.get(), EconomyRegistry.COIN.get()};
    }

    private static final long[] VALUES = {10_000L, 100L, 1L};

    public static long value(Item item) {
        Item[] it = items();
        for (int i = 0; i < it.length; i++) if (item == it[i]) return VALUES[i];
        return 0;
    }

    public static long value(ItemStack stack) {
        return value(stack.getItem()) * stack.getCount();
    }

    public static final long MAX_CHANGE = 18L * 64 * 10_000L;

    public static List<ItemStack> change(long amount) {
        if (amount < 1 || amount > MAX_CHANGE) throw new BankException("amount must be 1-" + MAX_CHANGE + " for a payout");
        List<ItemStack> out = new ArrayList<>();
        Item[] it = items();
        for (int i = 0; i < it.length; i++) {
            long n = amount / VALUES[i];
            amount -= n * VALUES[i];
            int max = new ItemStack(it[i]).getMaxStackSize();
            while (n > 0) {
                int c = (int) Math.min(max, n);
                out.add(new ItemStack(it[i], c));
                n -= c;
            }
        }
        return out;
    }
}
