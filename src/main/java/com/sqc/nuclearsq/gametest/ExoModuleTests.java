package com.sqc.nuclearsq.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoCoreItem;
import com.sqc.nuclearsq.exo.ExoModule;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.exo.ModuleEntry;
import com.sqc.nuclearsq.registry.ModDataComponents;

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
public final class ExoModuleTests {
    private ExoModuleTests() {}

    private static final String FLOOR = "floor";
    private static final int CHEST = 1;

    private static Map<Holder<Attribute>, Double> attributes(ItemStack stack, EquipmentSlot slot) {
        Map<Holder<Attribute>, Double> out = new HashMap<>();
        stack.forEachModifier(slot, (attr, mod) -> out.merge(attr, mod.amount(), Double::sum));
        return out;
    }

    private static double at(Map<Holder<Attribute>, Double> m, Holder<Attribute> a) {
        return m.getOrDefault(a, 0.0);
    }

    private static ItemStack chestWith(String... ids) {
        ItemStack chest = new ItemStack(ExoRegistry.ARMOR.get(CHEST).get());
        chest.set(ExoRegistry.MODULES.get(), java.util.Arrays.stream(ids).map(id -> new ModuleEntry(id, ExoModule.of(id).level())).toList());
        return chest;
    }

    @GameTest(template = FLOOR, batch = "exo_modules", timeoutTicks = 20)
    public static void moduleAttributesOnPiece(GameTestHelper h) {
        ItemStack chest = chestWith("exo_module_armor_3_sqc", "exo_module_reach_2_sqc", "exo_module_dig_5_sqc");
        Map<Holder<Attribute>, Double> off = attributes(chest, EquipmentSlot.CHEST);
        if (at(off, Attributes.BLOCK_INTERACTION_RANGE) != 0 || at(off, Attributes.BLOCK_BREAK_SPEED) != 0) throw new GameTestAssertException("Module wirken ohne Strom");
        chest.set(ExoRegistry.ACTIVE.get(), true);
        Map<Holder<Attribute>, Double> on = attributes(chest, EquipmentSlot.CHEST);
        if (Math.abs(at(on, Attributes.ARMOR) - at(off, Attributes.ARMOR) - 3.0) > 1e-6) throw new GameTestAssertException("Panzerung III: +3 erwartet");
        if (Math.abs(at(on, Attributes.BLOCK_INTERACTION_RANGE) - 1.0) > 1e-6) throw new GameTestAssertException("Reichweite II: +1,0 erwartet");
        if (Math.abs(at(on, Attributes.BLOCK_BREAK_SPEED) - 1.0) > 1e-6) throw new GameTestAssertException("Abbau V: +1,0 (100 %) erwartet");
        if (!attributes(chest, EquipmentSlot.FEET).isEmpty()) throw new GameTestAssertException("Attribute gelten auch in einem anderen Platz");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules", timeoutTicks = 20)
    public static void modulesNeedChargedCore(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        ItemStack chest = chestWith("exo_module_armor_3_sqc", "exo_module_dig_1_sqc");
        sp.setItemSlot(EquipmentSlot.CHEST, chest);
        ItemStack core = new ItemStack(ExoRegistry.CORE.get());
        sp.getInventory().add(core);
        core = sp.getInventory().getItem(0);
        chest.getItem().inventoryTick(chest, h.getLevel(), sp, 0, false);
        if (chest.getOrDefault(ExoRegistry.ACTIVE.get(), false)) throw new GameTestAssertException("Module an mit leerem Kern");
        core.set(ModDataComponents.STORED_ENERGY.get(), 1000);
        chest.getItem().inventoryTick(chest, h.getLevel(), sp, 0, false);
        int cost = ExoModule.COST_PER_LEVEL * (3 + 1);
        if (!chest.getOrDefault(ExoRegistry.ACTIVE.get(), false)) throw new GameTestAssertException("Module aus trotz geladenem Kern");
        if (ExoCoreItem.energy(core) != 1000 - cost) throw new GameTestAssertException("Verbrauch falsch: " + ExoCoreItem.energy(core));
        core.set(ModDataComponents.STORED_ENERGY.get(), cost - 1);
        chest.getItem().inventoryTick(chest, h.getLevel(), sp, 0, false);
        if (chest.getOrDefault(ExoRegistry.ACTIVE.get(), false)) throw new GameTestAssertException("Module an ohne genug Ladung");
        sp.getInventory().clearContent();
        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules", timeoutTicks = 20)
    public static void batteryModuleRaisesCapacity(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        ItemStack legs = new ItemStack(ExoRegistry.ARMOR.get(2).get());
        legs.set(ExoRegistry.MODULES.get(), List.of(new ModuleEntry("exo_module_battery_3_sqc", 3)));
        sp.setItemSlot(EquipmentSlot.LEGS, legs);
        sp.getInventory().add(new ItemStack(ExoRegistry.CORE.get()));
        ItemStack core = sp.getInventory().getItem(0);
        int base = ExoCoreItem.CAPACITY[1];
        sp.tickCount = 20;
        core.getItem().inventoryTick(core, h.getLevel(), sp, 0, false);
        if (ExoCoreItem.capacity(core) != base * 160 / 100) throw new GameTestAssertException("Akku III: 160 % erwartet, ist " + ExoCoreItem.capacity(core));
        sp.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        core.getItem().inventoryTick(core, h.getLevel(), sp, 0, false);
        if (ExoCoreItem.capacity(core) != base) throw new GameTestAssertException("Kapazitaet nach Ablegen nicht gekappt: " + ExoCoreItem.capacity(core));
        sp.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules", timeoutTicks = 20)
    public static void modulesFitOnlyTheirPiece(GameTestHelper h) {
        List<String> expect = List.of("helmet", "chestplate", "leggings", "boots");
        for (var m : ExoRegistry.MODULE_ITEMS) {
            ExoModule mod = m.get();
            for (int i = 0; i < 4; i++) {
                boolean fits = mod.fits(new ItemStack(ExoRegistry.ARMOR.get(i).get()));
                if (fits != expect.get(i).equals(mod.kind().piece.getName())) throw new GameTestAssertException(m.getId().getPath() + " passt falsch in " + expect.get(i));
            }
        }
        h.succeed();
    }
}
