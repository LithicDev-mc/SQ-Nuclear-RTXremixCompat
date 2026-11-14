package com.sqc.nuclearsq.economy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BankTerminalBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 18;
    public static final long SESSION_TICKS = 20L * 60 * 5;
    private static final double REACH_SQR = 8.0 * 8.0;

    private static final java.util.Map<UUID, ServerPlayer> TEST_PLAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerTestPlayer(ServerPlayer player) {
        TEST_PLAYERS.put(player.getUUID(), player);
    }

    public static void clearTestPlayers() {
        TEST_PLAYERS.clear();
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private UUID user;
    private long loginTick;
    private UUID owner;
    private final Throttle tickerThrottle = new Throttle(10, 20);
    private final ActionConfirm confirm = new ActionConfirm();

    public BankTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.TERMINAL_BE.get(), pos, state);
    }

    public boolean login(ServerPlayer player) {
        if (!isEmpty() && owner != null && !owner.equals(player.getUUID())) return false;
        owner = player.getUUID();
        user = player.getUUID();
        loginTick = level.getGameTime();
        EconomyData.get(player.server).touch(user, player.getGameProfile().getName());
        return true;
    }

    public void logout() {
        user = null;
        confirm.clear();
    }

    public void requireConfirmation(String key, net.minecraft.network.chat.Component text) {
        ServerPlayer p = requireUser();
        switch (confirm.check(key, level.getGameTime())) {
            case CONSUMED -> { }
            case NEW_PENDING -> {
                p.displayClientMessage(Component.translatable("message.nuclearsq.bank_terminal.confirm", text), true);
                throw new BankException(CONFIRM_MESSAGE);
            }
            case STILL_PENDING -> throw new BankException(CONFIRM_MESSAGE);
        }
    }

    public static final String CONFIRM_MESSAGE = "confirmation required: right-click the terminal within 20 seconds, then repeat the call";

    public boolean confirmPending(ServerPlayer player) {
        return currentUser() == player && confirm.confirm(level.getGameTime());
    }

    @Nullable
    public ServerPlayer currentUser() {
        if (user == null || level == null || level.getServer() == null) return null;
        ServerPlayer p = TEST_PLAYERS.get(user);
        if (p == null) p = level.getServer().getPlayerList().getPlayer(user);
        if (p == null || p.level() != level || level.getGameTime() - loginTick > SESSION_TICKS) return null;
        return p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= REACH_SQR ? p : null;
    }

    public ServerPlayer requireUser() {
        ServerPlayer p = currentUser();
        if (p == null) throw new BankException("nobody is logged in at this terminal (right-click it, then stay within 8 blocks)");
        if (online()) EconomyData.get(level.getServer()).throttle(p.getUUID(), level.getGameTime());
        return p;
    }

    public boolean online() {
        return level != null && BankNet.online(level, worldPosition);
    }

    public EconomyData data() {
        if (!online()) throw new BankException("no connection to a bank server (put a bank modem next to this terminal and next to the server)");
        EconomyData d = EconomyData.get(level.getServer());
        d.prune(level.getGameTime());
        return d;
    }

    public long deposit() {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        long sum = 0;
        for (ItemStack s : items) sum += Coins.value(s);
        if (sum == 0) throw new BankException("no coins in the container");
        d.deposit(p.getUUID(), sum, level.getGameTime());
        for (int i = 0; i < SLOTS; i++) if (Coins.value(items.get(i)) > 0) items.set(i, ItemStack.EMPTY);
        setChanged();
        return sum;
    }

    public void withdraw(long amount) {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        List<ItemStack> coins = Coins.change(amount);
        if (!insert(coins, true)) throw new BankException("not enough free space in the container for the coins");
        d.withdraw(p.getUUID(), amount, level.getGameTime());
        insert(coins, false);
        setChanged();
    }

    public int listSlot(int slot, long price) {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        if (slot < 1 || slot > SLOTS) throw new BankException("slot must be 1-" + SLOTS);
        ItemStack s = items.get(slot - 1);
        if (Coins.value(s) > 0) throw new BankException("coins cannot be sold");
        int id = d.listOffer(p.getUUID(), s, price, level.getGameTime()).id;
        items.set(slot - 1, ItemStack.EMPTY);
        setChanged();
        return id;
    }

    public EconomyData tickerData() {
        EconomyData d = data();
        tickerThrottle.hit(level.getGameTime());
        return d;
    }

    public long exchangeDeposit() {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        java.util.Map<String, Long> sum = new java.util.LinkedHashMap<>();
        java.util.List<Integer> slots = new ArrayList<>();
        java.util.List<String> why = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            if (!Exchange.isGood(id)) {
                why.add("slot " + (i + 1) + ": " + id + " is not a tradable good");
                continue;
            }
            if (!s.getComponentsPatch().isEmpty()) {
                java.util.List<String> keys = new ArrayList<>();
                for (var e : s.getComponentsPatch().entrySet()) {
                    var key = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(e.getKey());
                    keys.add(key == null ? "?" : key.toString());
                }
                why.add("slot " + (i + 1) + ": " + id + " has extra data (" + String.join(", ", keys) + ")");
                continue;
            }
            sum.merge(id, (long) s.getCount(), Long::sum);
            slots.add(i);
            total += s.getCount();
        }
        if (sum.isEmpty()) {
            throw new BankException("no tradable goods in the container" + (why.isEmpty() ? " (it is empty)" : " (" + String.join("; ", why) + ")"));
        }
        d.exchange().depositGoods(p.getUUID(), sum, level.getGameTime());
        for (int i : slots) items.set(i, ItemStack.EMPTY);
        setChanged();
        return total;
    }

    public void exchangeWithdraw(String good, long qty) {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        if (!Exchange.isGood(good)) throw new BankException("unknown good '" + good + "'");
        if (qty < 1 || qty > Exchange.MAX_WITHDRAW) throw new BankException("quantity must be 1-" + Exchange.MAX_WITHDRAW);
        net.minecraft.world.item.Item item = Exchange.itemOf(good);
        List<ItemStack> stacks = new ArrayList<>();
        long left = qty;
        int max = new ItemStack(item).getMaxStackSize();
        while (left > 0) {
            int c = (int) Math.min(max, left);
            stacks.add(new ItemStack(item, c));
            left -= c;
        }
        if (!insert(stacks, true)) throw new BankException("not enough free space in the container");
        d.exchange().withdrawGoods(p.getUUID(), good, qty, level.getGameTime());
        insert(stacks, false);
        setChanged();
    }

    public record Claimed(long money, int moved, int waiting) {}

    public Claimed claim() {
        ServerPlayer p = requireUser();
        EconomyData d = data();
        long money = d.claimMoney(p.getUUID());
        int moved = 0;
        List<ItemStack> pickup = d.pickup(p.getUUID());
        for (Iterator<ItemStack> it = pickup.iterator(); it.hasNext();) {
            ItemStack s = it.next();
            int before = s.getCount();
            List<ItemStack> one = new ArrayList<>();
            one.add(s);
            insertPartial(one);
            moved += before - s.getCount();
            if (s.isEmpty()) it.remove();
        }
        d.setDirty();
        setChanged();
        return new Claimed(money, moved, pickup.size());
    }

    private boolean insert(List<ItemStack> stacks, boolean simulate) {
        NonNullList<ItemStack> target = simulate ? copy() : items;
        for (ItemStack in : stacks) {
            ItemStack rest = in.copy();
            for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
                ItemStack cur = target.get(i);
                if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, rest) && cur.getCount() < cur.getMaxStackSize()) {
                    int n = Math.min(rest.getCount(), cur.getMaxStackSize() - cur.getCount());
                    cur.grow(n);
                    rest.shrink(n);
                }
            }
            for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
                if (target.get(i).isEmpty()) {
                    int n = Math.min(rest.getCount(), rest.getMaxStackSize());
                    target.set(i, rest.copyWithCount(n));
                    rest.shrink(n);
                }
            }
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    private void insertPartial(List<ItemStack> stacks) {
        for (ItemStack in : stacks) {
            for (int i = 0; i < SLOTS && !in.isEmpty(); i++) {
                ItemStack cur = items.get(i);
                if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, in) && cur.getCount() < cur.getMaxStackSize()) {
                    int n = Math.min(in.getCount(), cur.getMaxStackSize() - cur.getCount());
                    cur.grow(n);
                    in.shrink(n);
                }
            }
            for (int i = 0; i < SLOTS && !in.isEmpty(); i++) {
                if (items.get(i).isEmpty()) {
                    int n = Math.min(in.getCount(), in.getMaxStackSize());
                    items.set(i, in.copyWithCount(n));
                    in.shrink(n);
                }
            }
        }
    }

    private NonNullList<ItemStack> copy() {
        NonNullList<ItemStack> c = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) c.set(i, items.get(i).copy());
        return c;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nuclearsq.bank_terminal_sqc");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChestMenu(MenuType.GENERIC_9x2, id, inv, this, 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }
}
