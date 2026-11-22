package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.CraftingTerminalMenu;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.storage.WifiAdapterBlockEntity;
import com.sqc.nuclearsq.storage.WifiLink;
import com.sqc.nuclearsq.storage.WifiTerminalItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingTerminalTests {
    private CraftingTerminalTests() {}

    private static final String FLOOR = "floor";

    private record Net(BigMachineBlockEntity rack, BlockPos port) {}

    private static Net rack(GameTestHelper h, BlockPos min, boolean drive) {
        BlockPos ctrl = h.absolutePos(min);
        BigMachine m = BigMachine.SERVER_RACK_SMALL;
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || be.rack() == null) {
            throw new GameTestAssertException("kein Rack-Controller");
        }
        if (drive) be.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        be.energy().add(be.energy().getMaxEnergyStored());
        BlockPos port = m.structure.world(ctrl, 0, 0, 0, 1);
        return new Net(be, port.offset(port.subtract(ctrl)));
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("nsq_ct_" + name).getBytes()), "[nsq_" + name + "]"));
        p.getInventory().clearContent();
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        return p;
    }

    private static long count(GameTestHelper h, BlockPos at, ItemStack s) {
        return StorageNet.access(h.getLevel(), at).count(ItemKey.of(s));
    }

    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 80)
    public static void craftingTerminalShiftCrafts(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.CRAFTING_TERMINAL.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            ServerPlayer p = player(h, "craftingTerminalShiftCrafts");
            StorageNet.access(h.getLevel(), n.port()).insert(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 10, false);
            CraftingTerminalMenu m = new CraftingTerminalMenu(1, p.getInventory(), n.port(), -1);
            if (!m.fillRecipe(STICK).getString().isEmpty()) throw new GameTestAssertException("Fuellen mit Brettern im Netz klappt nicht");
            if (m.resultSlot().getItem().getCount() != 4) throw new GameTestAssertException("Ergebnis: " + m.resultSlot().getItem());
            m.quickMoveStack(p, CraftingTerminalMenu.RESULT_SLOT);
            long sticks = count(h, n.port(), new ItemStack(Items.STICK));
            if (sticks != 20) throw new GameTestAssertException("Stecken im Netz " + sticks + " statt 20");
            if (count(h, n.port(), new ItemStack(Items.OAK_PLANKS)) != 0) throw new GameTestAssertException("Bretter noch im Netz");
            for (int i = 0; i < 9; i++) if (!m.grid().getItem(i).isEmpty()) throw new GameTestAssertException("Raster nicht leer: " + m.grid().getItem(i));
            if (p.getInventory().countItem(Items.STICK) != 0) throw new GameTestAssertException("Stecken im Inventar statt im Netz");
            if (m.fillRecipe(STICK).getString().isEmpty()) throw new GameTestAssertException("Fuellen ohne Zutaten meldet keinen Fehler");
            if (!m.grid().getItem(0).isEmpty()) throw new GameTestAssertException("halb gefuelltes Raster");
            m.removed(p);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 80)
    public static void craftingTerminalKeepsGridItemsOnClose(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.CRAFTING_TERMINAL.get().defaultBlockState());
        BlockPos lone = h.absolutePos(new BlockPos(12, 2, 12));
        h.getLevel().setBlockAndUpdate(lone, StorageRegistry.CRAFTING_TERMINAL.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            ServerPlayer p = player(h, "craftingTerminalKeepsGridItemsOnClose");
            CraftingTerminalMenu m = new CraftingTerminalMenu(1, p.getInventory(), n.port(), -1);
            m.grid().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
            m.grid().setItem(4, new ItemStack(Items.DIAMOND, 2));
            m.removed(p);
            if (count(h, n.port(), new ItemStack(Items.COBBLESTONE)) != 5 || count(h, n.port(), new ItemStack(Items.DIAMOND)) != 2) {
                throw new GameTestAssertException("Raster-Inhalt nicht im Netz");
            }
            CraftingTerminalMenu m2 = new CraftingTerminalMenu(2, p.getInventory(), lone, -1);
            m2.grid().setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            m2.removed(p);
            if (p.getInventory().countItem(Items.IRON_INGOT) != 7) throw new GameTestAssertException("Eisen ohne Netz nicht im Inventar");
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            CraftingTerminalMenu m3 = new CraftingTerminalMenu(3, p.getInventory(), lone, -1);
            m3.grid().setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
            m3.removed(p);
            int dropped = 0;
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(6))) {
                if (e.getItem().is(Items.GOLD_INGOT)) dropped += e.getItem().getCount();
            }
            if (dropped != 9) throw new GameTestAssertException("Gold auf dem Boden: " + dropped + " statt 9");
            p.getInventory().clearContent();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 80)
    public static void craftingTerminalStopsWithoutRoom(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), false);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.CRAFTING_TERMINAL.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            ServerPlayer p = player(h, "craftingTerminalStopsWithoutRoom");
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            p.getInventory().items.set(0, new ItemStack(Items.OAK_PLANKS, 64));
            CraftingTerminalMenu m = new CraftingTerminalMenu(1, p.getInventory(), n.port(), -1);
            m.fillRecipe(STICK);
            m.quickMoveStack(p, CraftingTerminalMenu.RESULT_SLOT);
            if (p.getInventory().countItem(Items.STICK) != 0) throw new GameTestAssertException("Ergebnis trotz voller Tasche hergestellt");
            int planks = p.getInventory().countItem(Items.OAK_PLANKS);
            for (int i = 0; i < 9; i++) planks += m.grid().getItem(i).is(Items.OAK_PLANKS) ? m.grid().getItem(i).getCount() : 0;
            if (planks != 64) throw new GameTestAssertException("Bretter verschwunden: " + planks);
            m.removed(p);
            p.getInventory().clearContent();
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 80)
    public static void wifiLinkChecks(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            WifiAdapterBlockEntity a = WifiAdapterBlockEntity.at(h.getLevel(), n.port());
            if (a == null) throw new GameTestAssertException("kein Adapter-BlockEntity");
            ServerPlayer p = player(h, "wifiLinkChecks");
            ItemStack t = new ItemStack(StorageRegistry.WIFI_TERMINAL.get());
            if (WifiLink.check(p, t) != WifiLink.Status.NOT_LINKED) throw new GameTestAssertException("ungekoppelt nicht erkannt");
            WifiTerminalItem.setLink(t, GlobalPos.of(Level.NETHER, n.port()));
            if (WifiLink.check(p, t) != WifiLink.Status.OTHER_DIMENSION) throw new GameTestAssertException("andere Dimension nicht erkannt");
            WifiTerminalItem.setLink(t, GlobalPos.of(h.getLevel().dimension(), n.port()));
            if (WifiLink.check(p, t) != WifiLink.Status.ADAPTER_OFFLINE) throw new GameTestAssertException("Adapter ohne Strom nicht offline");
            a.energy().receiveEnergy(a.capacity(), false);
            h.runAfterDelay(3, () -> {
                if (WifiLink.check(p, t) != WifiLink.Status.OK) throw new GameTestAssertException("mit Strom: " + WifiLink.check(p, t));
                Vec3 c = Vec3.atCenterOf(n.port());
                p.setPos(c.add(a.range() - 2, 0, 0));
                if (WifiLink.check(p, t) != WifiLink.Status.OK) throw new GameTestAssertException("knapp in Reichweite abgewiesen");
                p.setPos(c.add(a.range() + 2000, 0, 0));
                if (WifiLink.check(p, t) != WifiLink.Status.OUT_OF_RANGE) throw new GameTestAssertException("ausserhalb nicht abgewiesen");
                int base = a.range();
                if (!a.addAntenna() || a.range() <= base) throw new GameTestAssertException("Antenne erhoeht die Reichweite nicht");
                a.setEnabled(false);
                h.runAfterDelay(3, () -> {
                    p.setPos(c);
                    if (WifiLink.check(p, t) != WifiLink.Status.ADAPTER_OFFLINE) throw new GameTestAssertException("ausgeschalteter Adapter online");
                    h.getLevel().setBlockAndUpdate(n.port(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    if (WifiLink.check(p, t) != WifiLink.Status.ADAPTER_GONE) throw new GameTestAssertException("fehlender Adapter nicht erkannt");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 100)
    public static void wifiTerminalPaysAndLosesConnection(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            WifiAdapterBlockEntity a = WifiAdapterBlockEntity.at(h.getLevel(), n.port());
            a.energy().receiveEnergy(a.capacity(), false);
            ServerPlayer p = player(h, "wifiTerminalPaysAndLosesConnection");
            ItemStack t = new ItemStack(StorageRegistry.WIFI_TERMINAL.get());
            WifiTerminalItem.setLink(t, GlobalPos.of(h.getLevel().dimension(), n.port()));
            WifiTerminalItem.charge(t, 60);
            p.getInventory().setItem(0, t);
            p.getInventory().selected = 0;
            StorageNet.access(h.getLevel(), n.port()).insert(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 10, false);
            h.runAfterDelay(3, () -> {
                CraftingTerminalMenu m = new CraftingTerminalMenu(1, p.getInventory(), n.port(), 0);
                if (!m.stillValid(p)) throw new GameTestAssertException("Fenster direkt nach dem Oeffnen ungueltig");
                m.fillRecipe(STICK);
                m.quickMoveStack(p, CraftingTerminalMenu.RESULT_SLOT);
                long sticks = count(h, n.port(), new ItemStack(Items.STICK));
                if (sticks != 12) throw new GameTestAssertException("Stecken " + sticks + " statt 12 (3 Rezepte fuer 60 FE)");
                if (WifiTerminalItem.energy(p.getInventory().getItem(0)) != 0) throw new GameTestAssertException("Akku nicht leer: " + WifiTerminalItem.energy(p.getInventory().getItem(0)));
                if (m.resultSlot().mayPickup(p)) throw new GameTestAssertException("Ergebnis ohne Akku entnehmbar");
                p.setPos(Vec3.atCenterOf(n.port()).add(a.range() + 2000, 0, 0));
                if (m.stillValid(p)) throw new GameTestAssertException("Fenster ausserhalb der Reichweite noch gueltig");
                long planksBefore = count(h, n.port(), new ItemStack(Items.OAK_PLANKS));
                int inGrid = 0;
                for (int i = 0; i < 9; i++) inGrid += m.grid().getItem(i).getCount();
                m.removed(p);
                long planksAfter = count(h, n.port(), new ItemStack(Items.OAK_PLANKS)) + p.getInventory().countItem(Items.OAK_PLANKS);
                if (planksAfter != planksBefore + inGrid) throw new GameTestAssertException("Raster-Inhalt bei Verbindungsverlust verloren: " + planksAfter + " statt " + (planksBefore + inGrid));
                p.getInventory().clearContent();
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 80)
    public static void craftingTerminalLeavesReservedAlone(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.CRAFTING_TERMINAL.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            ServerPlayer p = player(h, "craftingTerminalLeavesReservedAlone");
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            StorageNet.access(h.getLevel(), n.port()).insert(planks, 4, false);
            if (StorageNet.access(h.getLevel(), n.port()).reserve(77L, planks, 3) != 3) throw new GameTestAssertException("Reservierung klappt nicht");
            CraftingTerminalMenu m = new CraftingTerminalMenu(1, p.getInventory(), n.port(), -1);
            if (m.fillRecipe(STICK).getString().isEmpty()) throw new GameTestAssertException("Fuellen nahm Reserviertes (nur 1 Brett frei)");
            if (StorageNet.access(h.getLevel(), n.port()).count(planks) != 4) throw new GameTestAssertException("Bretter verschwunden");
            m.removed(p);
            h.succeed();
        });
    }

    private static void tickAll(GameTestHelper h, WifiAdapterBlockEntity... as) {
        for (WifiAdapterBlockEntity a : as) WifiAdapterBlockEntity.tick(h.getLevel(), a.getBlockPos(), a.getBlockState(), a);
    }

    @GameTest(template = FLOOR, batch = "ct_mesh", timeoutTicks = 80)
    public static void wifiAdaptersFormMesh(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        BlockPos near = n.port().offset(40, 0, 0);
        h.getLevel().getChunkAt(near);
        h.getLevel().setBlockAndUpdate(near, StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            WifiAdapterBlockEntity home = WifiAdapterBlockEntity.at(h.getLevel(), n.port());
            WifiAdapterBlockEntity mid = WifiAdapterBlockEntity.at(h.getLevel(), near);
            if (home == null || mid == null) throw new GameTestAssertException("Adapter fehlen");
            for (WifiAdapterBlockEntity a : new WifiAdapterBlockEntity[] {home, mid}) a.energy().receiveEnergy(a.capacity(), false);
            tickAll(h, home, mid);
            h.runAfterDelay(3, () -> {
                tickAll(h, home, mid);
                Vec3 p = Vec3.atCenterOf(n.port()).add(80, 0, 0);
                if (!home.reaches(p)) throw new GameTestAssertException("Kette Heimat-Relais erreicht den Spieler nicht: home " + home.online() + " mid " + mid.online() + " range " + home.range() + " dist " + Vec3.atCenterOf(near).distanceTo(p));
                mid.setEnabled(false);
                tickAll(h, home, mid);
                h.runAfterDelay(3, () -> {
                    if (home.reaches(p)) throw new GameTestAssertException("Kette haelt trotz ausgeschaltetem Relais");
                    mid.setEnabled(true);
                    tickAll(h, home, mid);
                    h.runAfterDelay(3, () -> {
                        Vec3 away = Vec3.atCenterOf(n.port()).add(2000, 0, 0);
                        if (home.reaches(away)) throw new GameTestAssertException("Spieler weit ausserhalb erreicht");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "ct_unlimited", timeoutTicks = 60)
    public static void wifiAntennaStagesReachUnlimited(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            WifiAdapterBlockEntity a = WifiAdapterBlockEntity.at(h.getLevel(), n.port());
            if (a == null) throw new GameTestAssertException("kein Adapter-BlockEntity");
            a.energy().receiveEnergy(a.capacity(), false);
            int max = com.sqc.nuclearsq.storage.WifiValues.adapter().maxAntennas(), last = a.range();
            if (max != com.sqc.nuclearsq.storage.WifiAdapterBlock.MAX_SHOWN) throw new GameTestAssertException("Stufen " + max + " statt " + com.sqc.nuclearsq.storage.WifiAdapterBlock.MAX_SHOWN);
            for (int i = 1; i <= max; i++) {
                if (!WifiAdapterBlockEntity.isModule(new ItemStack(WifiAdapterBlockEntity.module(i)))) throw new GameTestAssertException("Stufe " + i + " ohne Modul");
                if (!a.addAntenna()) throw new GameTestAssertException("Stufe " + i + " nicht einsetzbar");
                if (i < max && (a.unlimited() || a.range() <= last)) throw new GameTestAssertException("Stufe " + i + " erhoeht die Reichweite nicht: " + a.range());
                last = a.range();
            }
            if (!a.unlimited()) throw new GameTestAssertException("letzte Stufe nicht unbegrenzt: " + a.range());
            if (a.addAntenna()) throw new GameTestAssertException("mehr Module als Stufen");
            tickAll(h, a);
            if (a.getBlockState().getValue(com.sqc.nuclearsq.storage.WifiAdapterBlock.ANTENNAS) != max) throw new GameTestAssertException("Modell zeigt die Stufe nicht");
            ServerPlayer p = player(h, "wifiAntennaStagesReachUnlimited");
            ItemStack t = new ItemStack(StorageRegistry.WIFI_TERMINAL.get());
            WifiTerminalItem.setLink(t, GlobalPos.of(h.getLevel().dimension(), n.port()));
            p.setPos(Vec3.atCenterOf(n.port()).add(250000, 0, 0));
            if (WifiLink.check(p, t) != WifiLink.Status.OK) throw new GameTestAssertException("unbegrenzt, aber " + WifiLink.check(p, t));
            if (!a.reaches(null, Vec3.ZERO)) throw new GameTestAssertException("unbegrenzt erreicht andere Dimension nicht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "ct_a1", timeoutTicks = 40)
    public static void wifiAdapterIsDataPortWithDemand(GameTestHelper h) {
        Net n = rack(h, new BlockPos(2, 2, 2), true);
        h.getLevel().setBlockAndUpdate(n.port(), StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            if (!com.sqc.nuclearsq.data.DataSources.isPort(h.getLevel(), n.port())) throw new GameTestAssertException("Adapter ist kein Datenanschluss");
            h.succeed();
        });
    }
}
