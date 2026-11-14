package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.storage.StoragePayloads.Entry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class StorageTerminalMenu extends AbstractContainerMenu {
    public static final int WIDTH = 196, HEIGHT = 240, COLS = 9, ROWS = 6, GRID_X = 18, GRID_Y = 32, INV_Y = 158;
    public static final int TAKE = 0, TAKE_HALF = 1, TAKE_SHIFT = 2, INSERT = 3, INSERT_ONE = 4;
    private static final int SYNC_TICKS = 10;

    protected static final int INVENTORY_SLOTS = 36;

    public final BlockPos pos;
    protected final Player player;

    private List<Entry> sent;
    private int ticks, sentRacks = -1, sentOnline = -1;
    private long sentUsed = -1, sentCapacity = -1;
    protected boolean dirty = true;
    private List<ItemStack> sentCraftables;
    private final Map<ItemKey, ItemStack> shownCache = new java.util.HashMap<>();
    private int sentJobs = -1, sentMessageId = -1, craftTicks, messageId;
    private Component message = Component.empty();

    private List<Entry> entries = List.of();
    private int racks, online, version;
    private long used, capacity;
    private List<ItemStack> craftables = List.of();
    private int jobs, clientMessageId;
    private Component clientMessage = Component.empty();

    public StorageTerminalMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public StorageTerminalMenu(int id, Inventory inv, BlockPos pos) {
        this(StorageRegistry.TERMINAL_MENU.get(), id, inv, pos);
    }

    protected StorageTerminalMenu(MenuType<?> type, int id, Inventory inv, BlockPos pos) {
        super(type, id);
        this.pos = pos;
        this.player = inv.player;
        int px = (WIDTH - 162) / 2 + 1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, INV_Y + 58));
    }

    public int width() {
        return WIDTH;
    }

    public int height() {
        return HEIGHT;
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new StorageTerminalMenu(id, inv, pos),
                Component.translatable("gui.nuclearsq.storage.terminal.title")), buf -> buf.writeBlockPos(pos));
    }

    protected StorageAccess access() {
        return StorageNet.access(player.level(), pos);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && (dirty || ++craftTicks >= SYNC_TICKS * 2 || messageId != sentMessageId)) syncCraftables(sp);
        if (!(player instanceof ServerPlayer sp) || (!dirty && ++ticks < SYNC_TICKS)) return;
        ticks = 0;
        dirty = false;
        StorageAccess a = access();
        List<Entry> list = new ArrayList<>();
        list.addAll(StoragePayloads.entries(a.list(), a.listReserved(), sp.level().registryAccess(), shownCache));
        if (same(list, sent) && a.racks() == sentRacks && a.onlineRacks() == sentOnline && a.used() == sentUsed
                && a.capacity() == sentCapacity) return;
        sent = list;
        sentRacks = a.racks();
        sentOnline = a.onlineRacks();
        sentUsed = a.used();
        sentCapacity = a.capacity();
        if (sp.connection != null) {
            PacketDistributor.sendToPlayer(sp, new StoragePayloads.Items(containerId, list, sentRacks, sentOnline, sentUsed, sentCapacity));
        }
    }

    private static boolean same(List<Entry> a, List<Entry> b) {
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Entry x = a.get(i), y = b.get(i);
            if (x.count() != y.count() || x.reserved() != y.reserved() || !ItemStack.isSameItemSameComponents(x.stack(), y.stack())) return false;
        }
        return true;
    }

    public void action(ServerPlayer sp, ItemStack proto, int mode) {
        StorageAccess a = access();
        ItemStack carried = getCarried();
        switch (mode) {
            case INSERT, INSERT_ONE -> {
                if (carried.isEmpty()) return;
                int n = mode == INSERT ? carried.getCount() : 1;
                long got = a.insert(ItemKey.of(carried), n, false);
                carried.shrink((int) got);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            case TAKE, TAKE_HALF -> {
                if (!carried.isEmpty() || proto.isEmpty()) return;
                ItemKey key = StoragePayloads.resolve(a.list(), proto, sp.level().registryAccess());
                int max = key.maxStack();
                int want = mode == TAKE ? max : (int) Math.min(max, (a.available(key) + 1) / 2);
                setCarried(a.extractStack(key, want, false));
            }
            case TAKE_SHIFT -> {
                if (proto.isEmpty()) return;
                ItemKey key = StoragePayloads.resolve(a.list(), proto, sp.level().registryAccess());
                ItemStack got = a.extractStack(key, key.maxStack(), false);
                if (got.isEmpty()) return;
                moveItemStackTo(got, 0, INVENTORY_SLOTS, true);
                if (!got.isEmpty()) a.insert(key, got.getCount(), false);
            }
            default -> { return; }
        }
        dirty = true;
        broadcastChanges();
    }

    private void syncCraftables(ServerPlayer sp) {
        craftTicks = 0;
        List<ItemStack> list = new ArrayList<>();
        int j = 0;
        List<BigMachineBlockEntity> asm = StorageNet.assemblers(player.level(), pos);
        for (ItemKey key : book(asm).keySet()) list.add(key.stack(1));
        for (BigMachineBlockEntity be : asm) j += be.assembler().jobs().size();
        if (sentCraftables != null && sentJobs == j && sentMessageId == messageId && sameStacks(list, sentCraftables)) return;
        sentCraftables = list;
        sentJobs = j;
        sentMessageId = messageId;
        if (sp.connection != null) PacketDistributor.sendToPlayer(sp, new StoragePayloads.Craftables(containerId, list, j, message, messageId));
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) return false;
        return true;
    }

    static Map<ItemKey, Pattern> book(List<BigMachineBlockEntity> assemblers) {
        Map<ItemKey, Pattern> out = new java.util.LinkedHashMap<>();
        for (BigMachineBlockEntity be : assemblers) for (Pattern p : be.assembler().allPatterns()) out.putIfAbsent(p.outputKey(), p);
        return out;
    }

    public static final int MAX_ORDER = 4096;

    public boolean order(ItemStack proto, int amount) {
        Order r = placeOrder(player.level(), pos, proto, amount);
        if (!proto.isEmpty()) {
            message = r.message();
            messageId++;
        }
        return r.ok();
    }

    public static List<ItemStack> craftables(net.minecraft.world.level.Level level, BlockPos pos) {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemKey k : book(StorageNet.assemblers(level, pos)).keySet()) out.add(k.proto().copyWithCount(1));
        return out;
    }

    public record Order(boolean ok, Component message) {}

    public static Order placeOrder(net.minecraft.world.level.Level level, BlockPos pos, ItemStack proto, int amount) {
        if (proto.isEmpty()) return new Order(false, Component.empty());
        Component message;
        amount = Math.max(1, Math.min(MAX_ORDER, amount));
        ItemKey key = ItemKey.of(proto);
        String k = "gui.nuclearsq.storage.terminal.order.";
        List<BigMachineBlockEntity> asm = StorageNet.assemblers(level, pos);
        Map<ItemKey, Pattern> book = book(asm);
        boolean ok = false;
        if (!book.containsKey(key)) {
            message = Component.translatable(k + "no_pattern", proto.getHoverName());
        } else {
            CraftPlanner.Plan plan = CraftPlanner.plan(StorageNet.access(level, pos), book, key, amount);
            if (plan.tooComplex()) {
                message = Component.translatable(k + "complex", proto.getHoverName());
            } else if (!plan.missing().isEmpty()) {
                net.minecraft.network.chat.MutableComponent list = Component.empty();
                int n = 0;
                for (Map.Entry<ItemKey, Long> e : plan.missing().entrySet()) {
                    if (n == 3) {
                        list.append(", ...");
                        break;
                    }
                    if (n++ > 0) list.append(", ");
                    list.append(Component.translatable(k + "count", e.getValue(), e.getKey().proto().getHoverName()));
                }
                message = Component.translatable(k + "missing", list);
            } else {
                BigMachineBlockEntity best = null;
                boolean tooBig = false;
                int size = plan.size();
                for (BigMachineBlockEntity be : asm) {
                    ComponentAssembler a = be.assembler();
                    if (!a.hasPattern(key) || !a.accepts()) continue;
                    a.refreshNow(level, be);
                    if (!a.fits(size)) {
                        tooBig = true;
                        continue;
                    }
                    if (best == null || a.jobs().size() < best.assembler().jobs().size()) best = be;
                }
                if (best == null && tooBig) {
                    message = Component.translatable(k + "too_big", proto.getHoverName(), size);
                } else if (best == null) {
                    message = Component.translatable(k + "busy");
                } else {
                    if (best.assembler().addJob(proto, amount, plan.steps(), StorageNet.access(level, best.dataStarts()), plan.reserved())) {
                        best.setChanged();
                        message = Component.translatable(k + "ok", amount, proto.getHoverName(), plan.steps().size());
                        ok = true;
                    } else {
                        message = Component.translatable(k + "reserve", proto.getHoverName());
                    }
                }
            }
        }
        return new Order(ok, message);
    }

    public Component lastMessage() {
        return message;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (p.level().isClientSide) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack rest = access().insert(slot.getItem(), false);
        slot.set(rest);
        dirty = true;
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.level().getBlockState(pos).getBlock() instanceof StorageTerminalBlock
                && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    public void receive(StoragePayloads.Items p) {
        entries = p.entries();
        racks = p.racks();
        online = p.online();
        used = p.used();
        capacity = p.capacity();
        version++;
    }

    public void receive(StoragePayloads.Craftables p) {
        craftables = p.protos();
        jobs = p.jobs();
        clientMessage = p.message();
        clientMessageId = p.messageId();
        version++;
    }

    public List<ItemStack> craftables() { return craftables; }
    public int jobs() { return jobs; }
    public Component message() { return clientMessage; }
    public int messageId() { return clientMessageId; }
    public List<Entry> entries() { return entries; }
    public int racks() { return racks; }
    public int online() { return online; }
    public long used() { return used; }
    public long capacity() { return capacity; }
    public int version() { return version; }
}
