package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DeconShowerBlockEntity;
import com.sqc.nuclearsq.radiation.DeconSprayerItem;
import com.sqc.nuclearsq.radiation.DosimeterItem;
import com.sqc.nuclearsq.radiation.HazmatArmorItem;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionTests {
    private ProtectionTests() {}

    private static final String FLOOR = "floor";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static ServerPlayer player(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"),
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    @GameTest(template = FLOOR, batch = "prot_suit", timeoutTicks = 20)
    public static void suitShieldsByTierAndPieces(GameTestHelper h) {
        ArmorStand st = h.spawn(EntityType.ARMOR_STAND, new BlockPos(3, 2, 3));
        if (HazmatArmorItem.shielding(st) != 0) throw new GameTestAssertException("Ohne Anzug schon Abschirmung");
        st.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProtectionRegistry.SUIT.get(0).get(0).get()));
        double helm = HazmatArmorItem.shielding(st);
        if (Math.abs(helm - 0.25 * 0.60) > 1e-6) throw new GameTestAssertException("Helm I: " + helm);
        for (int t = 0; t < ProtectionRegistry.TIERS; t++) {
            for (int k = 0; k < 4; k++) st.setItemSlot(SLOTS[k], new ItemStack(ProtectionRegistry.SUIT.get(t).get(k).get()));
            double s = HazmatArmorItem.shielding(st);
            if (Math.abs(s - HazmatArmorItem.SHIELD[t]) > 1e-6) throw new GameTestAssertException("Voller Anzug Stufe " + (t + 1) + ": " + s);
        }
        ItemStack chest = st.getItemBySlot(EquipmentSlot.CHEST);
        HazmatArmorItem.wear(st, HazmatArmorItem.WEAR_FULL);
        if (chest.getDamageValue() != 1) throw new GameTestAssertException("Anzug nutzt sich nicht ab: " + chest.getDamageValue());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "prot_zone", timeoutTicks = 20)
    public static void contaminationRadiatesUntilSprayed(GameTestHelper h) {
        BlockPos spot = h.absolutePos(new BlockPos(5, 2, 5));
        Vec3 near = Vec3.atCenterOf(spot).add(0, 1, 0);
        try {
            double before = RadiationManager.rateAt(h.getLevel(), near);
            Contamination.add(h.getLevel(), spot, Contamination.RESIDUE_SV_H);
            Contamination.add(h.getLevel(), spot, Contamination.RESIDUE_SV_H);
            if (Math.abs(Contamination.zones(h.getLevel()).get(spot) - 2 * Contamination.RESIDUE_SV_H) > 1e-6) {
                throw new GameTestAssertException("Rueckstaende stapeln sich nicht");
            }
            double rate = RadiationManager.rateAt(h.getLevel(), near) - before;
            if (rate < Contamination.RESIDUE_SV_H) throw new GameTestAssertException("Zone strahlt nicht: " + rate);
            int n = DeconSprayerItem.spray(h.getLevel(), spot.offset(1, 0, -1));
            if (n < 1 || Contamination.zones(h.getLevel()).get(spot) != 0) {
                throw new GameTestAssertException("Ein Spruehstoss reinigt die Stelle nicht: " + Contamination.zones(h.getLevel()).get(spot));
            }
            h.succeed();
        } finally {
            Contamination.clean(h.getLevel(), spot, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "prot_shower", timeoutTicks = 20)
    public static void showerWashesPlayer(GameTestHelper h) {
        BlockPos shower = new BlockPos(4, 5, 4);
        h.setBlock(shower, ProtectionRegistry.SHOWER.get());
        DeconShowerBlockEntity be = h.getBlockEntity(shower);
        be.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        ServerPlayer sp = player(h);
        BlockPos below = h.absolutePos(shower.below(2));
        sp.setPos(below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
        if (!be.area().contains(sp.position().add(0, 0.1, 0))) throw new GameTestAssertException("Platz unter der Dusche liegt nicht im Strahl");
        Contamination.setBody(sp, 0.01);
        if (!be.wash(java.util.List.of(sp))) throw new GameTestAssertException("Dusche laeuft nicht");
        double body = Contamination.body(sp);
        if (body > 0.01 * Contamination.SHOWER_KEEP + 1e-9) throw new GameTestAssertException("Dusche waescht nicht: " + body);
        if (be.tank().getFluidAmount() != 1000 - DeconShowerBlockEntity.USE) throw new GameTestAssertException("Dusche verbraucht kein Wasser");
        be.tank().drain(2000, IFluidHandler.FluidAction.EXECUTE);
        if (be.wash(java.util.List.of(sp))) throw new GameTestAssertException("Dusche laeuft ohne Wasser");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "prot_dosimeter", timeoutTicks = 20)
    public static void dosimeterCountsDailyDose(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().add(new ItemStack(ProtectionRegistry.DOSIMETER.get()));
        double start = DosimeterItem.today(sp);
        DosimeterItem.tick(sp, 0.1, 0);
        DosimeterItem.tick(sp, 0.05, 0);
        double today = DosimeterItem.today(sp) - start;
        if (Math.abs(today - 0.15) > 1e-9) throw new GameTestAssertException("Tagesdosis falsch: " + today);
        if (DosimeterItem.total(sp) < 0.15 - 1e-9) throw new GameTestAssertException("Gesamtdosis zaehlt nicht mit");
        h.succeed();
    }
}
