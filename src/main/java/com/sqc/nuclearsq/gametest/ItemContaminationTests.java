package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.ItemContamination;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ItemContaminationTests {
    private ItemContaminationTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "item_contam_value", timeoutTicks = 20)
    public static void setGetCapAndClean(GameTestHelper h) {
        ItemStack s = new ItemStack(Items.COBBLESTONE, 4);
        if (ItemContamination.get(s) != 0f) throw new GameTestAssertException("Neuer Stapel ist kontaminiert");
        ItemContamination.set(s, 0.002f);
        if (Math.abs(ItemContamination.get(s) - ItemContamination.quantize(0.002f)) > 1e-9) throw new GameTestAssertException("set/get: " + ItemContamination.get(s));
        ItemContamination.add(s, 0.001f);
        if (Math.abs(ItemContamination.get(s) - ItemContamination.quantize(ItemContamination.quantize(0.002f) + 0.001f)) > 1e-7) throw new GameTestAssertException("add: " + ItemContamination.get(s));
        ItemContamination.set(s, 5f);
        if (ItemContamination.get(s) != ItemContamination.MAX) throw new GameTestAssertException("Obergrenze: " + ItemContamination.get(s));
        ItemContamination.set(s, ItemContamination.CLEAN * 0.5f);
        if (ItemContamination.get(s) != 0f || !s.getComponentsPatch().isEmpty()) {
            throw new GameTestAssertException("Unter der Schwelle nicht entfernt: " + ItemContamination.get(s));
        }
        if (!ItemStack.isSameItemSameComponents(s, new ItemStack(Items.COBBLESTONE))) {
            throw new GameTestAssertException("Gereinigter Stapel stapelt nicht mehr mit sauberen");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "item_contam_carry", timeoutTicks = 20)
    public static void carriedSumsAndWashLowers(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        try {
            ItemStack a = new ItemStack(Items.WHEAT, 3);
            ItemStack b = new ItemStack(Items.IRON_INGOT, 1);
            ItemContamination.set(a, 0.001f);
            ItemContamination.set(b, 0.002f);
            sp.getInventory().setItem(3, a);
            sp.getInventory().setItem(10, b);
            sp.getInventory().setItem(11, new ItemStack(Items.DIRT, 5));
            double carried = ItemContamination.carried(sp);
            if (Math.abs(carried - (ItemContamination.quantize(0.001f) + ItemContamination.quantize(0.002f))) > 1e-7) throw new GameTestAssertException("carried: " + carried + " statt 0,003");
            ItemContamination.washInventory(sp, 0.25);
            double after = ItemContamination.carried(sp);
            if (Math.abs(after - carried * 0.25) > carried * 0.25 * 0.2) throw new GameTestAssertException("Nach dem Waschen: " + after + " statt 0,00075");
            h.succeed();
        } finally {
            sp.getInventory().clearContent();
        }
    }

    @GameTest(template = FLOOR, batch = "item_contam_eat", timeoutTicks = 20)
    public static void eatDoseOnlyForContaminatedFood(GameTestHelper h) {
        ItemStack bread = new ItemStack(Items.BREAD);
        if (ItemContamination.eatDose(bread) != 0) throw new GameTestAssertException("Sauberes Brot: " + ItemContamination.eatDose(bread));
        ItemContamination.set(bread, 0.001f);
        double d = ItemContamination.eatDose(bread);
        if (!(d > 0)) throw new GameTestAssertException("Verstrahltes Brot ohne Wirkung");
        if (Math.abs(d - 0.001 * ItemContamination.EAT_FACTOR) > 1e-7) throw new GameTestAssertException("eatDose: " + d);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemContamination.set(stone, 0.001f);
        if (ItemContamination.eatDose(stone) != 0) throw new GameTestAssertException("Stein ist kein Essen");
        h.succeed();
    }
}
