package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.PowerRegistry;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StoragePayloads;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AttackerTests {
    private AttackerTests() {}

    private static final String FLOOR = "floor";
    private static final int S2C_LIMIT = 1 << 20, C2S_LIMIT = 32767;

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static ItemStack fullBook(int n, int pages) {
        List<Filterable<Component>> list = new ArrayList<>();
        for (int p = 0; p < pages; p++) {
            list.add(Filterable.passThrough(Component.literal(("Buch " + n + " Seite " + p + " ").repeat(100).substring(0, 1000))));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Buch " + n), "Griefer", 0, list, true));
        return book;
    }

    @GameTest(template = FLOOR, batch = "attack_payload", timeoutTicks = 100)
    public static void heavyItemsCannotBreakTheTerminal(GameTestHelper h) {
        var ra = h.getLevel().registryAccess();
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) stock.put(ItemKey.of(fullBook(i, 100)), 3L);
        stock.put(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 4096L);
        check(StoragePayloads.size(stock.keySet().iterator().next().stack(1), ra) > C2S_LIMIT,
                "Vorbedingung: ein volles Buch muss ueber 32 KiB gross sein");

        List<StoragePayloads.Entry> list = StoragePayloads.entries(stock, Map.of(), ra, new LinkedHashMap<>());
        StoragePayloads.Items payload = new StoragePayloads.Items(1, list, 1, 1, 0, 0);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ra);
        StoragePayloads.Items.STREAM_CODEC.encode(buf, payload);
        check(buf.readableBytes() < S2C_LIMIT, "Bestandsliste ist " + buf.readableBytes() + " Bytes gross (Grenze " + S2C_LIMIT + ")");
        buf.release();
        check(list.size() == stock.size(), "Buecher sollen gekuerzt, nicht weggelassen werden (" + list.size() + " von " + stock.size() + ")");

        StoragePayloads.Entry shown = list.get(17);
        check(shown.stack().is(Items.WRITTEN_BOOK), "Eintrag 17 ist kein Buch");
        StoragePayloads.Action click = new StoragePayloads.Action(1, shown.stack(), 0);
        RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), ra);
        StoragePayloads.Action.STREAM_CODEC.encode(out, click);
        check(out.readableBytes() < C2S_LIMIT, "Klick-Paket ist " + out.readableBytes() + " Bytes gross (Grenze " + C2S_LIMIT + ")");
        out.release();
        ItemKey key = StoragePayloads.resolve(stock, shown.stack(), ra);
        check(key != null && stock.containsKey(key), "gekuerzte Vorlage wird keinem Bestand zugeordnet");
        WrittenBookContent c = key.proto().get(DataComponents.WRITTEN_BOOK_CONTENT);
        check(c != null && c.title().raw().equals("Buch 17"), "falsches Buch zugeordnet: " + (c == null ? "kein Buch" : c.title().raw()));

        StoragePayloads.Entry cobble = list.get(list.size() - 1);
        check(cobble.stack().is(Items.COBBLESTONE) && StoragePayloads.resolve(stock, cobble.stack(), ra).equals(ItemKey.of(new ItemStack(Items.COBBLESTONE))),
                "kleine Vorlage veraendert");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "attack_payload", timeoutTicks = 200)
    public static void listHasAHardBudget(GameTestHelper h) {
        var ra = h.getLevel().registryAccess();
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        for (int i = 0; i < 4000; i++) {
            ItemStack s = new ItemStack(Items.PAPER);
            s.set(DataComponents.CUSTOM_NAME, Component.literal(("Name " + i + " ").repeat(40)));
            stock.put(ItemKey.of(s), 1L);
        }
        List<StoragePayloads.Entry> list = StoragePayloads.entries(stock, Map.of(), ra, new LinkedHashMap<>());
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ra);
        StoragePayloads.Items.STREAM_CODEC.encode(buf, new StoragePayloads.Items(1, list, 1, 1, 0, 0));
        check(buf.readableBytes() < S2C_LIMIT, "Liste " + buf.readableBytes() + " Bytes");
        buf.release();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "attack_capsule", timeoutTicks = 40)
    public static void capsulesCannotBeRepaired(GameTestHelper h) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        ContainerLevelAccess access = ContainerLevelAccess.create(h.getLevel(), pos);
        for (var capsule : List.of(PowerRegistry.ISOTOPE_CAPSULE.get(), PowerRegistry.PLUTONIUM_CAPSULE.get())) {
            ItemStack a = new ItemStack(capsule), b = new ItemStack(capsule);
            a.setDamageValue(2400);
            b.setDamageValue(2400);
            AnvilMenu anvil = new AnvilMenu(1, p.getInventory(), access);
            anvil.getSlot(0).set(a.copy());
            anvil.getSlot(1).set(b.copy());
            check(anvil.getSlot(2).getItem().isEmpty(), "Amboss repariert die Kapsel: Schaden danach " + anvil.getSlot(2).getItem().getDamageValue());
            GrindstoneMenu grind = new GrindstoneMenu(2, p.getInventory(), access);
            grind.getSlot(0).set(a.copy());
            grind.getSlot(1).set(b.copy());
            check(grind.getSlot(2).getItem().isEmpty(), "Schleifstein repariert die Kapsel: Schaden danach " + grind.getSlot(2).getItem().getDamageValue());
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "attack_kit", timeoutTicks = 40)
    public static void kitPartsCannotBePushed(GameTestHelper h) {
        TreeSet<String> movable = new TreeSet<>();
        for (MachineKits.Def d : MachineKits.all().values()) {
            List<Block> blocks = new ArrayList<>();
            for (MultiblockStructure.Cell c : d.structure().cells(BlockPos.ZERO, 0)) {
                blocks.add(c.block());
                blocks.add(c.alt());
            }
            if (d.shell() != null) blocks.add(d.shell());
            for (Block b : blocks) {
                if (b == net.minecraft.world.level.block.Blocks.AIR) continue;
                if (b.defaultBlockState().getPistonPushReaction() != PushReaction.BLOCK) {
                    movable.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b) + " (" + d.id() + ")");
                }
            }
        }
        check(movable.isEmpty(), "Kit-Teile mit Schiebe-Verhalten ungleich BLOCK: " + movable);
        h.succeed();
    }
}
