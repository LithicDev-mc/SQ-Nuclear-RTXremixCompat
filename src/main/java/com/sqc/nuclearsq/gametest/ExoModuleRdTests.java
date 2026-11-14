package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoCoreItem;
import com.sqc.nuclearsq.exo.ExoMagnet;
import com.sqc.nuclearsq.exo.ExoModule;
import com.sqc.nuclearsq.exo.ExoRadiation;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.exo.ModuleEntry;
import com.sqc.nuclearsq.radiation.HazmatArmorItem;
import com.sqc.nuclearsq.radiation.RespiratorItem;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExoModuleRdTests {
    private ExoModuleRdTests() {}

    private static final String FLOOR = "floor";

    private static ItemStack piece(int index, String... ids) {
        ItemStack s = new ItemStack(ExoRegistry.ARMOR.get(index).get());
        s.set(ExoRegistry.MODULES.get(), java.util.Arrays.stream(ids).map(id -> new ModuleEntry(id, ExoModule.of(id).level())).toList());
        s.set(ExoRegistry.ACTIVE.get(), true);
        return s;
    }

    @GameTest(template = FLOOR, batch = "exo_modules_rd", timeoutTicks = 20)
    public static void radiationModuleActsLikeHelmetAndMask(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        ItemStack helmet = piece(0, "exo_module_rad_3_sqc");
        sp.setItemSlot(EquipmentSlot.HEAD, helmet);
        double helmetShare = HazmatArmorItem.weight(net.minecraft.world.item.ArmorItem.Type.HELMET);
        if (Math.abs(HazmatArmorItem.shielding(sp) - helmetShare * ExoRadiation.SHIELD[3]) > 1e-9) throw new GameTestAssertException("Abschirmung Stufe III falsch: " + HazmatArmorItem.shielding(sp));
        if (Math.abs(RespiratorItem.dustFactor(sp) - (1 - ExoRadiation.DUST[3])) > 1e-9 || Math.abs(RespiratorItem.airFactor(sp) - (1 - ExoRadiation.AIR[3])) > 1e-9) throw new GameTestAssertException("Staub/Dampf Stufe III falsch");
        helmet.set(ExoRegistry.ACTIVE.get(), false);
        if (HazmatArmorItem.shielding(sp) != 0 || RespiratorItem.dustFactor(sp) != 1.0 || RespiratorItem.airFactor(sp) != 1.0) throw new GameTestAssertException("Modul wirkt ohne Strom");
        sp.setItemSlot(EquipmentSlot.HEAD, piece(0, "exo_module_rad_5_sqc"));
        if (HazmatArmorItem.shielding(sp) >= HazmatArmorItem.SHIELD[0]) throw new GameTestAssertException("Stufe V ersetzt den ganzen Anzug (Abschirmung " + HazmatArmorItem.shielding(sp) + ")");
        if (ExoRadiation.SHIELD[5] <= RespiratorItem.SHIELD || ExoRadiation.AIR[5] < RespiratorItem.AIR_BLOCK) throw new GameTestAssertException("Stufe V soll die Filtermaske ersetzen");
        sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_rd", timeoutTicks = 20)
    public static void magnetPullsItemsInRadius(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 5.5));
        sp.setPos(at.x, at.y, at.z);
        sp.setItemSlot(EquipmentSlot.CHEST, piece(1, "exo_module_magnet_2_sqc"));
        sp.getInventory().add(new ItemStack(ExoRegistry.CORE.get()));
        ItemStack core = sp.getInventory().getItem(0);
        core.set(ModDataComponents.STORED_ENERGY.get(), 100);
        ItemEntity near = new ItemEntity(h.getLevel(), at.x + 3, at.y, at.z, new ItemStack(Items.COBBLESTONE));
        ItemEntity delayed = new ItemEntity(h.getLevel(), at.x, at.y, at.z + 3, new ItemStack(Items.COBBLESTONE));
        ItemEntity far = new ItemEntity(h.getLevel(), at.x - 8, at.y, at.z, new ItemStack(Items.COBBLESTONE));
        near.setPickUpDelay(0);
        delayed.setPickUpDelay(40);
        far.setPickUpDelay(0);
        for (ItemEntity e : List.of(near, delayed, far)) h.getLevel().addFreshEntity(e);
        ExoMagnet.pull(sp);
        if (near.distanceToSqr(sp) > 0.5) throw new GameTestAssertException("Gegenstand im Radius nicht geholt");
        if (Math.abs(delayed.getX() - at.x) > 0.01 || Math.abs(delayed.getZ() - (at.z + 3)) > 0.01) throw new GameTestAssertException("Gegenstand mit Aufnahme-Verzoegerung wurde geholt");
        if (Math.abs(far.getX() - (at.x - 8)) > 0.01) throw new GameTestAssertException("Gegenstand ausserhalb des Radius wurde geholt");
        if (ExoCoreItem.energy(core) != 100 - ExoMagnet.COST) throw new GameTestAssertException("Strom je Gegenstand falsch: " + ExoCoreItem.energy(core));
        ExoMagnet.pull(sp);
        if (ExoCoreItem.energy(core) != 100 - ExoMagnet.COST) throw new GameTestAssertException("Gegenstand direkt am Spieler wurde erneut bezahlt");
        for (ItemEntity e : List.of(near, delayed, far)) e.discard();
        sp.getInventory().clearContent();
        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_rd", timeoutTicks = 20)
    public static void levelTablesGrow(GameTestHelper h) {
        for (int lv = 1; lv <= 5; lv++) {
            if (ExoModule.of("exo_module_rad_" + lv + "_sqc") == null || ExoModule.of("exo_module_magnet_" + lv + "_sqc") == null) throw new GameTestAssertException("Modul Stufe " + lv + " fehlt");
            if (ExoRadiation.SHIELD[lv] <= ExoRadiation.SHIELD[lv - 1] || ExoRadiation.DUST[lv] <= ExoRadiation.DUST[lv - 1] || ExoRadiation.AIR[lv] <= ExoRadiation.AIR[lv - 1]
                    || ExoMagnet.RADIUS[lv] <= ExoMagnet.RADIUS[lv - 1]) throw new GameTestAssertException("Tabelle waechst nicht bei Stufe " + lv);
        }
        h.succeed();
    }
}
