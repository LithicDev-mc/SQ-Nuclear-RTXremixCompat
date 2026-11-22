package com.sqc.nuclearsq.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;

public class CraftingTerminalMenu extends StorageTerminalMenu {
    public static final int WIDE = 312, GRID_X = 206, GRID_Y = 36, RESULT_X = 282, RESULT_Y = 54;
    public static final int RESULT_SLOT = INVENTORY_SLOTS, GRID_SLOT = INVENTORY_SLOTS + 1;
    public static final int MAX_SHIFT_CRAFTS = 1024;

    private final TransientCraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final TerminalResultSlot resultSlot;
    private final int wifiSlot;
    private final net.minecraft.resources.ResourceKey<Level> dimension;
    private final DataSlot energyLo = DataSlot.standalone(), energyHi = DataSlot.standalone();
    @Nullable
    private WifiLink.Status lost;
    @Nullable
    private StorageAccess session;

    public CraftingTerminalMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readInt());
    }

    public CraftingTerminalMenu(int id, Inventory inv, BlockPos pos, int wifiSlot) {
        super(StorageRegistry.CRAFTING_MENU.get(), id, inv, pos);
        this.wifiSlot = wifiSlot;
        this.dimension = inv.player.level().dimension();
        this.resultSlot = (TerminalResultSlot) addSlot(new TerminalResultSlot(inv.player, grid, resultSlots, 0, RESULT_X, RESULT_Y));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) addSlot(new Slot(grid, c + r * 3, GRID_X + c * 18, GRID_Y + r * 18));
        addDataSlot(energyLo);
        addDataSlot(energyHi);
    }

    public static void openBlock(ServerPlayer sp, BlockPos pos) {
        open(sp, pos, -1);
    }

    public static void openWireless(ServerPlayer sp, BlockPos adapter, int wifiSlot) {
        open(sp, adapter, wifiSlot);
    }

    private static void open(ServerPlayer sp, BlockPos pos, int wifiSlot) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingTerminalMenu(id, inv, pos, wifiSlot),
                Component.translatable(wifiSlot < 0 ? "gui.nuclearsq.storage.crafting.title" : "gui.nuclearsq.storage.crafting.title_wifi")),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(wifiSlot);
                });
    }

    @Override
    public int width() {
        return WIDE;
    }

    public boolean wireless() {
        return wifiSlot >= 0;
    }

    public TransientCraftingContainer grid() {
        return grid;
    }

    public Slot resultSlot() {
        return resultSlot;
    }

    public int energy() {
        return (energyHi.get() << 16) | (energyLo.get() & 0xFFFF);
    }

    private ItemStack wifiStack(Player p) {
        if (wifiSlot < 0 || wifiSlot >= p.getInventory().getContainerSize()) return ItemStack.EMPTY;
        ItemStack s = p.getInventory().getItem(wifiSlot);
        return s.getItem() instanceof WifiTerminalItem ? s : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        if (wifiSlot < 0) {
            return p.level().getBlockState(pos).getBlock() instanceof CraftingTerminalBlock
                    && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
        }
        ItemStack s = wifiStack(p);
        WifiLink.Status st;
        if (s.isEmpty()) {
            st = WifiLink.Status.NOT_LINKED;
        } else if (!(p instanceof ServerPlayer sp)) {
            return true;
        } else if (WifiTerminalItem.link(s) == null || !WifiTerminalItem.link(s).pos().equals(pos) || !dimension.equals(p.level().dimension())) {
            st = WifiTerminalItem.link(s) == null ? WifiLink.Status.NOT_LINKED : WifiLink.Status.OTHER_DIMENSION;
        } else {
            st = WifiLink.check(sp, s);
        }
        if (st != WifiLink.Status.OK && lost == null) lost = st;
        return st == WifiLink.Status.OK;
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer && wifiSlot >= 0) {
            int e = WifiTerminalItem.energy(wifiStack(player));
            energyLo.set(e & 0xFFFF);
            energyHi.set(e >>> 16);
        }
        super.broadcastChanges();
    }

    @Override
    public void slotsChanged(Container c) {
        if (c == grid) updateResult();
    }

    protected void updateResult() {
        Level level = player.level();
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
        CraftingInput input = grid.asCraftInput();
        ItemStack out = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = found.get();
            if (resultSlots.setRecipeUsed(level, sp, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) out = assembled;
            }
        }
        resultSlots.setItem(0, out);
    }

    private boolean canCraft() {
        if (wifiSlot < 0) return true;
        return WifiTerminalItem.energy(wifiStack(player)) >= WifiValues.terminal().craftCost();
    }

    private final class TerminalResultSlot extends ResultSlot {
        TerminalResultSlot(Player player, TransientCraftingContainer craftSlots, Container container, int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player p) {
            return canCraft();
        }

        @Override
        public void onTake(Player p, ItemStack stack) {
            checkTakeAchievements(stack);
            if (wifiSlot >= 0) WifiTerminalItem.drain(wifiStack(p), WifiValues.terminal().craftCost());
            CraftingInput.Positioned positioned = grid.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            int left = positioned.left(), top = positioned.top();
            CommonHooks.setCraftingPlayer(p);
            NonNullList<ItemStack> remaining = p.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, input, p.level());
            CommonHooks.setCraftingPlayer(null);
            for (int k = 0; k < input.height(); k++) {
                for (int l = 0; l < input.width(); l++) {
                    int i = l + left + (k + top) * grid.getWidth();
                    ItemStack in = grid.getItem(i);
                    ItemStack rest = remaining.get(l + k * input.width());
                    ItemStack used = in.isEmpty() ? ItemStack.EMPTY : in.copyWithCount(1);
                    if (!in.isEmpty()) {
                        grid.removeItem(i, 1);
                        in = grid.getItem(i);
                    }
                    if (!rest.isEmpty()) {
                        if (in.isEmpty()) {
                            grid.setItem(i, rest);
                        } else if (ItemStack.isSameItemSameComponents(in, rest)) {
                            rest.grow(in.getCount());
                            grid.setItem(i, rest);
                        } else {
                            store(rest);
                        }
                    } else if (in.isEmpty() && !used.isEmpty()) {
                        restock(i, used);
                    }
                }
            }
            updateResult();
            dirty = true;
        }
    }

    private void restock(int index, ItemStack proto) {
        if (!(player instanceof ServerPlayer)) return;
        ItemKey key = ItemKey.of(proto);
        if (key != null && access().extract(key, 1, false) == 1) grid.setItem(index, key.stack(1));
    }

    @Override
    protected Level netLevel() {
        GlobalPos link = WifiTerminalItem.link(wifiStack(player));
        Level l = link != null && player instanceof ServerPlayer sp ? sp.server.getLevel(link.dimension()) : null;
        return l != null ? l : player.level();
    }

    @Override
    protected StorageAccess access() {
        return session != null ? session : super.access();
    }

    private void beginSession() {
        session = super.access();
    }

    private void endSession() {
        session = null;
    }

    private void store(ItemStack stack) {
        if (stack.isEmpty() || !(player instanceof ServerPlayer sp)) return;
        ItemStack rest = access().insert(stack, false);
        if (rest.isEmpty()) return;
        if (sp.isAlive() && !sp.hasDisconnected()) sp.getInventory().placeItemBackInInventory(rest);
        else sp.drop(rest, false);
    }

    private boolean roomFor(ItemStack stack) {
        ItemStack rest = access().insert(stack, true);
        if (rest.isEmpty()) return true;
        int free = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size() && free < rest.getCount(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) free += rest.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, rest)) free += Math.max(0, s.getMaxStackSize() - s.getCount());
        }
        return free >= rest.getCount();
    }

    public void clearGrid() {
        if (!(player instanceof ServerPlayer)) return;
        for (int i = 0; i < grid.getContainerSize(); i++) store(grid.removeItemNoUpdate(i));
        updateResult();
        dirty = true;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (p instanceof ServerPlayer sp) {
            if (lost != null && sp.isAlive() && !sp.hasDisconnected()) sp.displayClientMessage(lost.message(), true);
            beginSession();
            try {
                for (int i = 0; i < grid.getContainerSize(); i++) store(grid.removeItemNoUpdate(i));
            } finally {
                endSession();
            }
            resultSlots.clearContent();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (p.level().isClientSide || !(p instanceof ServerPlayer sp)) return ItemStack.EMPTY;
        if (index == RESULT_SLOT) {
            craftAll(sp);
            return ItemStack.EMPTY;
        }
        if (index >= GRID_SLOT) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack rest = access().insert(stack, false);
            if (!rest.isEmpty()) moveItemStackTo(rest, 0, INVENTORY_SLOTS, true);
            slot.set(rest.isEmpty() ? ItemStack.EMPTY : rest);
            dirty = true;
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(p, index);
    }

    private void craftAll(ServerPlayer sp) {
        beginSession();
        try {
            for (int n = 0; n < MAX_SHIFT_CRAFTS; n++) {
                ItemStack out = resultSlot.getItem();
                if (out.isEmpty() || !resultSlot.mayPickup(sp) || !roomFor(out)) break;
                ItemStack taken = resultSlot.remove(out.getCount());
                resultSlot.onTake(sp, taken.copy());
                store(taken);
            }
        } finally {
            endSession();
        }
        dirty = true;
    }

    public Component fillRecipe(ResourceLocation recipeId) {
        if (!(player instanceof ServerPlayer sp)) return Component.empty();
        Optional<RecipeHolder<?>> holder = sp.server.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
            return Component.translatable("message.nuclearsq.crafting.fill.unknown");
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe sr ? sr.getWidth() : 3, height = recipe instanceof ShapedRecipe sr2 ? sr2.getHeight() : 3;
        if (ingredients.isEmpty() || (recipe instanceof ShapedRecipe && (width > 3 || height > 3)) || ingredients.size() > 9) {
            return Component.translatable("message.nuclearsq.crafting.fill.unsupported");
        }
        beginSession();
        try {
            return fill(sp, recipe, ingredients, width);
        } finally {
            endSession();
        }
    }

    private Component fill(ServerPlayer sp, CraftingRecipe recipe, NonNullList<Ingredient> ingredients, int width) {
        clearGrid();
        StorageAccess net = access();
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        for (ItemKey k : net.list().keySet()) {
            long free = net.available(k);
            if (free > 0) stock.put(k, free);
        }
        Map<ItemKey, Integer> carried = new LinkedHashMap<>();
        for (ItemStack s : sp.getInventory().items) {
            ItemKey k = ItemKey.of(s);
            if (k != null) carried.merge(k, s.getCount(), Integer::sum);
        }
        record Pick(int slot, ItemKey key, boolean fromNet) {}
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            if (ing.isEmpty()) continue;
            int slot = recipe instanceof ShapedRecipe ? (i / width) * 3 + i % width : i;
            Pick pick = null;
            for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
                if (e.getValue() > 0 && ing.test(e.getKey().proto())) {
                    stock.put(e.getKey(), e.getValue() - 1);
                    pick = new Pick(slot, e.getKey(), true);
                    break;
                }
            }
            if (pick == null) {
                for (Map.Entry<ItemKey, Integer> e : carried.entrySet()) {
                    if (e.getValue() > 0 && ing.test(e.getKey().proto())) {
                        carried.put(e.getKey(), e.getValue() - 1);
                        pick = new Pick(slot, e.getKey(), false);
                        break;
                    }
                }
            }
            if (pick == null) return Component.translatable("message.nuclearsq.crafting.fill.missing");
            picks.add(pick);
        }
        for (Pick pk : picks) {
            if (pk.fromNet()) {
                if (net.extract(pk.key(), 1, false) != 1) {
                    clearGrid();
                    return Component.translatable("message.nuclearsq.crafting.fill.missing");
                }
                grid.setItem(pk.slot(), pk.key().stack(1));
            } else {
                for (ItemStack s : sp.getInventory().items) {
                    if (pk.key().matches(s)) {
                        s.shrink(1);
                        grid.setItem(pk.slot(), pk.key().stack(1));
                        break;
                    }
                }
            }
        }
        updateResult();
        dirty = true;
        return Component.empty();
    }
}
