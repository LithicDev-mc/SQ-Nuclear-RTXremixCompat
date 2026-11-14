package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.crate.CrateRegistry;
import com.sqc.nuclearsq.crate.StorageCrateBlock;
import com.sqc.nuclearsq.crate.StorageCrateBlockEntity;
import com.sqc.nuclearsq.crate.StorageCrateMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StorageCrateTests {
    private StorageCrateTests() {}

    private static final String FLOOR = "floor";

    private static StorageCrateBlockEntity place(GameTestHelper h, BlockPos rel) {
        var st = CrateRegistry.BLOCK.get().defaultBlockState().setValue(StorageCrateBlock.FACING, Direction.NORTH);
        h.setBlock(rel.west(), st.setValue(StorageCrateBlock.SECOND, true));
        h.setBlock(rel, st);
        if (!(h.getBlockEntity(rel) instanceof StorageCrateBlockEntity be)) throw new GameTestAssertException("keine Lagerkiste");
        return be;
    }

    @GameTest(template = FLOOR, batch = "storage_crate", timeoutTicks = 40)
    public static void upgradesSetCapacityAndBreakKeepsContents(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 2, 2);
        StorageCrateBlockEntity be = place(h, rel);
        if (be.capacity() != 27) throw new GameTestAssertException("Grundgroesse " + be.capacity());
        be.upgrade().setItem(0, new ItemStack(CrateRegistry.UPGRADE_1.get()));
        if (be.capacity() != 54) throw new GameTestAssertException("Upgrade I " + be.capacity());
        be.upgrade().setItem(0, new ItemStack(CrateRegistry.UPGRADE_2.get()));
        if (be.capacity() != 108) throw new GameTestAssertException("Upgrade II " + be.capacity());
        if (be.canPlaceItem(0, new ItemStack(CrateRegistry.ITEM.get())) || be.canPlaceItem(0, new ItemStack(Items.SHULKER_BOX))) {
            throw new GameTestAssertException("Kiste in Kiste erlaubt");
        }
        be.setItem(100, new ItemStack(Items.DIAMOND, 7));
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("nuclearsq:storage_crate_break".getBytes()), "crate_break"));
        sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        sp.gameMode.destroyBlock(h.absolutePos(rel.west()));
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(CrateRegistry.BLOCK.get(), rel);
            h.assertBlockNotPresent(CrateRegistry.BLOCK.get(), rel.west());
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(rel)).inflate(3)).stream()
                    .filter(e -> e.getItem().is(CrateRegistry.ITEM.get())).toList();
            if (drops.size() != 1) throw new GameTestAssertException("Kisten gefallen: " + drops.size());
            ItemEntity drop = drops.get(0);
            long diamonds = drop.getItem().getOrDefault(CrateRegistry.CONTENTS.get(), ItemContainerContents.EMPTY).nonEmptyStream()
                    .filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            ItemStack up = drop.getItem().getOrDefault(CrateRegistry.UPGRADE.get(), ItemContainerContents.EMPTY).copyOne();
            if (diamonds != 7 || !up.is(CrateRegistry.UPGRADE_2.get())) throw new GameTestAssertException("Inhalt " + diamonds + ", Upgrade " + up);
            drop.discard();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_crate", timeoutTicks = 20)
    public static void sidesFollowTheirMode(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 2, 2);
        StorageCrateBlockEntity be = place(h, rel);
        be.setMode(StorageCrateBlockEntity.TOP, StorageCrateBlockEntity.IN);
        be.setMode(StorageCrateBlockEntity.BOTTOM, StorageCrateBlockEntity.OUT);
        be.setMode(StorageCrateBlockEntity.FRONT, StorageCrateBlockEntity.OFF);
        BlockPos abs = h.absolutePos(rel);
        IItemHandler top = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
        IItemHandler bottom = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.DOWN);
        IItemHandler front = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs.west(), Direction.NORTH);
        IItemHandler right = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs.west(), Direction.WEST);
        if (right == null || !right.insertItem(5, new ItemStack(Items.GOLD_INGOT), true).isEmpty()) throw new GameTestAssertException("rechte Seite am zweiten Block fehlt");
        if (top == null || bottom == null || front != null) throw new GameTestAssertException("Seiten: oben " + top + ", unten " + bottom + ", vorne " + front);
        if (!top.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false).isEmpty()) throw new GameTestAssertException("oben nimmt nichts an");
        if (!top.extractItem(0, 5, false).isEmpty()) throw new GameTestAssertException("oben gibt heraus");
        if (bottom.insertItem(1, new ItemStack(Items.IRON_INGOT, 1), false).isEmpty()) throw new GameTestAssertException("unten nimmt an");
        if (bottom.extractItem(0, 4, false).getCount() != 4) throw new GameTestAssertException("unten gibt nichts heraus");
        if (be.getItem(0).getCount() != 6) throw new GameTestAssertException("Bestand " + be.getItem(0));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "storage_crate", timeoutTicks = 80)
    public static void ejectPushesIntoChestBelow(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 3, 2), below = rel.below();
        h.setBlock(below, Blocks.CHEST);
        StorageCrateBlockEntity be = place(h, rel);
        for (int r = 0; r < StorageCrateBlockEntity.SIDES; r++) be.setMode(r, StorageCrateBlockEntity.OFF);
        be.setMode(StorageCrateBlockEntity.BOTTOM, StorageCrateBlockEntity.OUT);
        be.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        be.setItem(1, new ItemStack(Items.COBBLESTONE, 36));
        be.setEject(true);
        h.succeedWhen(() -> {
            if (!(h.getBlockEntity(below) instanceof ChestBlockEntity chest)) throw new GameTestAssertException("keine Truhe");
            int n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) n += chest.getItem(i).getCount();
            if (n != 100) throw new GameTestAssertException("in der Truhe " + n);
        });
    }

    @GameTest(template = FLOOR, batch = "storage_crate", timeoutTicks = 20)
    public static void upgradeSlotGuardsContents(GameTestHelper h) {
        StorageCrateBlockEntity be = place(h, new BlockPos(2, 2, 2));
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("nuclearsq:storage_crate".getBytes()), "crate_test"));
        sp.getInventory().clearContent();
        sp.getInventory().setItem(0, new ItemStack(CrateRegistry.UPGRADE_2.get(), 2));
        StorageCrateMenu menu = (StorageCrateMenu) be.createMenu(1, sp.getInventory(), sp);
        int invHotbar0 = menu.slots.size() - 9;
        menu.quickMoveStack(sp, invHotbar0);
        if (!be.upgrade().getItem(0).is(CrateRegistry.UPGRADE_2.get()) || be.upgrade().getItem(0).getCount() != 1) {
            throw new GameTestAssertException("Shift-Klick legt kein einzelnes Upgrade ein: " + be.upgrade().getItem(0));
        }
        be.setItem(80, new ItemStack(Items.STONE));
        var upSlot = menu.slots.get(menu.capacity);
        if (upSlot.mayPickup(sp)) throw new GameTestAssertException("Upgrade trotz Inhalt hinter 54 entnehmbar");
        be.setItem(80, ItemStack.EMPTY);
        be.setItem(40, new ItemStack(Items.STONE));
        menu.setCarried(new ItemStack(CrateRegistry.UPGRADE_1.get()));
        if (!upSlot.mayPickup(sp)) throw new GameTestAssertException("Tausch gegen Upgrade I trotz passendem Inhalt gesperrt");
        menu.setCarried(ItemStack.EMPTY);
        if (upSlot.mayPickup(sp)) throw new GameTestAssertException("Upgrade ohne Ersatz entnehmbar, obwohl Platz 41 belegt");
        h.succeed();
    }
}
