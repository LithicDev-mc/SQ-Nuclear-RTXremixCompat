package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.HazmatArmorItem;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.radiation.RespiratorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RespiratorTests {
    private RespiratorTests() {}

    private static final String FLOOR = "floor";

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        Contamination.setBody(sp, 0);
        RadiationManager.setDose(sp, 0);
        return sp;
    }

    private static ItemStack maskWithFilter(ServerPlayer sp) {
        ItemStack mask = new ItemStack(ProtectionRegistry.RESPIRATOR.get());
        sp.setItemSlot(EquipmentSlot.HEAD, mask);
        ItemStack filter = new ItemStack(ProtectionRegistry.RESPIRATOR_FILTER.get(), 2);
        if (!RespiratorItem.insert(sp, mask, filter)) throw new GameTestAssertException("Filter liess sich nicht einsetzen");
        if (filter.getCount() != 1) throw new GameTestAssertException("Filter wurde nicht verbraucht: " + filter.getCount());
        return mask;
    }

    @GameTest(template = FLOOR, batch = "prot_mask", timeoutTicks = 20)
    public static void filterCutsUptakeFromGroundAndWears(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = player(h);
        BlockPos feet = sp.blockPosition();
        try {
            Contamination.add(lv, feet, 1e-4f);
            sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProtectionRegistry.RESPIRATOR.get()));
            RadiationManager.tickOnce(lv, sp);
            double without = Contamination.body(sp);
            if (!(without > 0)) throw new GameTestAssertException("Keine Aufnahme vom Boden");
            if (RespiratorItem.filterLeft(sp.getItemBySlot(EquipmentSlot.HEAD)) != 0) throw new GameTestAssertException("Maske ohne Filter hat Restzeit");
            Contamination.setBody(sp, 0);
            ItemStack mask = maskWithFilter(sp);
            RadiationManager.tickOnce(lv, sp);
            double with = Contamination.body(sp);
            double want = 1.0 - RespiratorItem.DUST_BLOCK;
            if (Math.abs(with / without - want) > 1e-3) throw new GameTestAssertException("Aufnahme mit Filter " + with / without + " statt " + want);
            int left = RespiratorItem.filterLeft(mask);
            if (left >= RespiratorItem.FILTER_SECONDS) throw new GameTestAssertException("Filter nutzt sich in belasteter Luft nicht ab: " + left);
            h.succeed();
        } finally {
            Contamination.clean(lv, feet, 1, 1f);
            Contamination.setBody(sp, 0);
            sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    @GameTest(template = FLOOR, batch = "prot_mask", timeoutTicks = 20)
    public static void filterKeepsInCleanAir(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = player(h);
        BlockPos feet = sp.blockPosition();
        try {
            Contamination.clean(lv, feet, 2, 1f);
            ItemStack mask = maskWithFilter(sp);
            for (int i = 0; i < 5; i++) RadiationManager.tickOnce(lv, sp);
            int left = RespiratorItem.filterLeft(mask);
            if (left != RespiratorItem.FILTER_SECONDS) throw new GameTestAssertException("Filter laeuft in sauberer Luft ab: " + left);
            h.succeed();
        } finally {
            sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    @GameTest(template = FLOOR, batch = "prot_mask", timeoutTicks = 20)
    public static void filterCutsLeakAirAndEmpties(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        ServerPlayer sp = player(h);
        BlockPos feet = sp.blockPosition();
        try {
            sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProtectionRegistry.RESPIRATOR.get()));
            RadiationManager.breatheLeak(sp);
            double without = Contamination.body(sp);
            if (!(without > 0)) throw new GameTestAssertException("Kein Dampf am Leck aufgenommen");
            Contamination.setBody(sp, 0);
            ItemStack mask = maskWithFilter(sp);
            RadiationManager.breatheLeak(sp);
            double with = Contamination.body(sp);
            double want = 1.0 - RespiratorItem.AIR_BLOCK;
            if (Math.abs(with / without - want) > 1e-3) throw new GameTestAssertException("Leck-Aufnahme mit Filter " + with / without + " statt " + want);
            if (RespiratorItem.filterLeft(mask) != RespiratorItem.FILTER_SECONDS - RespiratorItem.LEAK_WEAR) {
                throw new GameTestAssertException("Leck kostet falsche Filterzeit: " + RespiratorItem.filterLeft(mask));
            }
            for (int i = 0; i < RespiratorItem.FILTER_SECONDS / RespiratorItem.LEAK_WEAR + 1; i++) RespiratorItem.leakWear(sp);
            if (RespiratorItem.filterLeft(mask) != 0) throw new GameTestAssertException("Filter wird nicht leer");
            if (RespiratorItem.airFactor(sp) != 1.0 || RespiratorItem.dustFactor(sp) != 1.0) throw new GameTestAssertException("Leerer Filter schuetzt noch");
            ItemStack spare = new ItemStack(ProtectionRegistry.RESPIRATOR_FILTER.get());
            if (!RespiratorItem.insert(sp, mask, spare) || RespiratorItem.filterLeft(mask) != RespiratorItem.FILTER_SECONDS) {
                throw new GameTestAssertException("Neuer Filter nach leerem laesst sich nicht einsetzen");
            }
            ItemStack spare2 = new ItemStack(ProtectionRegistry.RESPIRATOR_FILTER.get());
            if (RespiratorItem.insert(sp, mask, spare2) || spare2.getCount() != 1) throw new GameTestAssertException("Zweiter Filter in volle Maske");
            h.succeed();
        } finally {
            Contamination.clean(lv, feet, RadiationValues.LEAK_DEPOSIT_RANGE, 1f);
            Contamination.setBody(sp, 0);
            sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    @GameTest(template = FLOOR, batch = "prot_mask", timeoutTicks = 20)
    public static void maskCountsAsHalfHelmet(GameTestHelper h) {
        ArmorStand st = h.spawn(EntityType.ARMOR_STAND, new BlockPos(3, 2, 3));
        st.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProtectionRegistry.RESPIRATOR.get()));
        double s = HazmatArmorItem.shielding(st);
        if (Math.abs(s - 0.25 * RespiratorItem.SHIELD) > 1e-6) throw new GameTestAssertException("Maske als Helm: " + s);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "prot_mask", timeoutTicks = 20)
    public static void comboFilterActsLikeIodineAndDosimeterShowsFilter(GameTestHelper h) {
        ServerPlayer sp = player(h);
        try {
            if (com.sqc.nuclearsq.radiation.DosimeterItem.payload(sp).filter() != -1) throw new GameTestAssertException("Dosimeter meldet Maske ohne Maske");
            ItemStack mask = new ItemStack(ProtectionRegistry.RESPIRATOR.get());
            sp.setItemSlot(EquipmentSlot.HEAD, mask);
            if (com.sqc.nuclearsq.radiation.DosimeterItem.payload(sp).filter() != 0) throw new GameTestAssertException("Maske ohne Filter nicht als 0 gemeldet");
            ItemStack plain = new ItemStack(ProtectionRegistry.RESPIRATOR_FILTER.get());
            RespiratorItem.insert(sp, mask, plain);
            if (RadiationManager.iodineFactor(sp) != 1.0) throw new GameTestAssertException("Einfacher Filter wirkt wie Iod");
            if (com.sqc.nuclearsq.radiation.DosimeterItem.payload(sp).filter() != RespiratorItem.FILTER_SECONDS) throw new GameTestAssertException("Dosimeter zeigt Filter nicht");
            for (int i = 0; i < RespiratorItem.FILTER_SECONDS; i++) RespiratorItem.leakWear(sp);
            ItemStack combo = new ItemStack(ProtectionRegistry.RESPIRATOR_FILTER_IODINE.get());
            if (!RespiratorItem.insert(sp, mask, combo)) throw new GameTestAssertException("Kombifilter nicht einsetzbar");
            if (Math.abs(RadiationManager.iodineFactor(sp) - RadiationValues.IODINE_FACTOR) > 1e-9) throw new GameTestAssertException("Kombifilter ohne Iod-Wirkung");
            double dust = RespiratorItem.dustFactor(sp);
            if (Math.abs(dust - (1.0 - RespiratorItem.DUST_BLOCK)) > 1e-9) throw new GameTestAssertException("Kombifilter filtert nicht wie der Normale: " + dust);
            for (int i = 0; i < RespiratorItem.FILTER_SECONDS; i++) RespiratorItem.leakWear(sp);
            if (RadiationManager.iodineFactor(sp) != 1.0) throw new GameTestAssertException("Leerer Kombifilter wirkt noch");
            h.succeed();
        } finally {
            Contamination.setBody(sp, 0);
            sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }
}
