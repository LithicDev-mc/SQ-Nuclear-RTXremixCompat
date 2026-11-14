package com.sqc.nuclearsq.gametest;

import java.util.HashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoEvents;
import com.sqc.nuclearsq.exo.ExoJetpack;
import com.sqc.nuclearsq.exo.ExoModule;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.exo.ModuleEntry;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExoModuleMoreTests {
    private ExoModuleMoreTests() {}

    private static final String FLOOR = "floor";
    private static final int HELMET = 0, CHEST = 1, LEGGINGS = 2;

    private static ItemStack piece(int index, String... ids) {
        ItemStack s = new ItemStack(ExoRegistry.ARMOR.get(index).get());
        s.set(ExoRegistry.MODULES.get(), java.util.Arrays.stream(ids).map(id -> new ModuleEntry(id, ExoModule.of(id).level())).toList());
        s.set(ExoRegistry.ACTIVE.get(), true);
        return s;
    }

    private static double sum(ItemStack s, EquipmentSlot slot, Holder<Attribute> attr) {
        Map<Holder<Attribute>, Double> m = new HashMap<>();
        s.forEachModifier(slot, (a, mod) -> m.merge(a, mod.amount(), Double::sum));
        return m.getOrDefault(attr, 0.0);
    }

    @GameTest(template = FLOOR, batch = "exo_modules_more", timeoutTicks = 20)
    public static void strengthAndBreathAttributes(GameTestHelper h) {
        ItemStack chest = piece(CHEST, "exo_module_strength_3_sqc");
        ItemStack helmet = piece(HELMET, "exo_module_breath_2_sqc");
        if (Math.abs(sum(chest, EquipmentSlot.CHEST, Attributes.ATTACK_DAMAGE) - 3.0) > 1e-6) throw new GameTestAssertException("Staerke III: +3 erwartet");
        if (Math.abs(sum(helmet, EquipmentSlot.HEAD, Attributes.OXYGEN_BONUS) - 8.0) > 1e-6) throw new GameTestAssertException("Unterwasser Atmung II: +8 erwartet");
        if (sum(chest, EquipmentSlot.FEET, Attributes.ATTACK_DAMAGE) != 0) throw new GameTestAssertException("Staerke gilt in einem anderen Platz");
        chest.set(ExoRegistry.ACTIVE.get(), false);
        if (sum(chest, EquipmentSlot.CHEST, Attributes.ATTACK_DAMAGE) != 0) throw new GameTestAssertException("Staerke wirkt ohne Strom");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_more", timeoutTicks = 20)
    public static void fireProtectionReducesFireDamage(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        if (Math.abs(ExoEvents.fireDamage(5f, 2) - 3f) > 1e-4f) throw new GameTestAssertException("Stufe II: 3 statt 5 Schaden erwartet, ist " + ExoEvents.fireDamage(5f, 2));
        if (ExoEvents.fireDamage(5f, 5) != 0f || ExoEvents.fireDamage(5f, 7) != 0f) throw new GameTestAssertException("Stufe V: kein Schaden erwartet");
        if (ExoEvents.fireDamage(5f, 0) != 5f) throw new GameTestAssertException("Ohne Modul darf der Schaden nicht sinken");
        sp.setItemSlot(EquipmentSlot.LEGS, piece(LEGGINGS, "exo_module_fire_3_sqc", "exo_module_fire_2_sqc"));
        if (ExoEvents.level(sp, ExoModule.Kind.FIRE) != 5 || ExoEvents.fireDamage(5f, ExoEvents.level(sp, ExoModule.Kind.FIRE)) != 0f) throw new GameTestAssertException("Zwei Module am Teil: Summe 5 erwartet");
        sp.getItemBySlot(EquipmentSlot.LEGS).set(ExoRegistry.ACTIVE.get(), false);
        if (ExoEvents.level(sp, ExoModule.Kind.FIRE) != 0) throw new GameTestAssertException("Feuerschutz wirkt ohne Strom");
        sp.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_more", timeoutTicks = 20)
    public static void lootAndLuckLevels(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        ItemStack helmet = piece(HELMET, "exo_module_loot_3_sqc");
        ItemStack chest = piece(CHEST, "exo_module_luck_2_sqc", "exo_module_luck_1_sqc");
        sp.setItemSlot(EquipmentSlot.HEAD, helmet);
        sp.setItemSlot(EquipmentSlot.CHEST, chest);
        if (ExoEvents.level(sp, ExoModule.Kind.LOOT) != 3) throw new GameTestAssertException("Pluenderung III erwartet");
        if (ExoEvents.level(sp, ExoModule.Kind.LUCK) != 3) throw new GameTestAssertException("Glueck II + I = 3 erwartet");
        chest.set(ExoRegistry.ACTIVE.get(), false);
        if (ExoEvents.level(sp, ExoModule.Kind.LUCK) != 0) throw new GameTestAssertException("Glueck wirkt ohne Strom");
        sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_more", timeoutTicks = 20)
    public static void jetpackMovement(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.setDeltaMovement(0, 0, 0);
        for (int i = 0; i < 10; i++) ExoJetpack.apply(sp, 3, ExoJetpack.UP);
        if (Math.abs(sp.getDeltaMovement().y - 0.55) > 1e-6) throw new GameTestAssertException("Steigtempo Stufe 3: 0,55 erwartet, ist " + sp.getDeltaMovement().y);
        sp.setDeltaMovement(0, -0.3, 0);
        ExoJetpack.apply(sp, 1, ExoJetpack.HOVER);
        if (sp.getDeltaMovement().y != -0.1) throw new GameTestAssertException("Stufe 1 darf nicht schweben (gleitet mit -0,1)");
        ExoJetpack.apply(sp, 2, ExoJetpack.HOVER);
        if (sp.getDeltaMovement().y != 0) throw new GameTestAssertException("Stufe 2 schwebt nicht");
        ExoJetpack.apply(sp, 2, ExoJetpack.DOWN);
        if (sp.getDeltaMovement().y > -0.45) throw new GameTestAssertException("Strg sinkt nicht schnell");
        sp.setDeltaMovement(0, -1.0, 0);
        sp.fallDistance = 10f;
        ExoJetpack.apply(sp, 1, 0);
        if (sp.getDeltaMovement().y != -0.1 || sp.fallDistance != 0f) throw new GameTestAssertException("Loslassen gleitet nicht oder Fallschaden bleibt");
        h.succeed();
    }
}
