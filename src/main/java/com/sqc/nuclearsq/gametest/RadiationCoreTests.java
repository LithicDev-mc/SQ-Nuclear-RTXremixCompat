package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DeconSprayerItem;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.radiation.Shielding;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationCoreTests {
    private RadiationCoreTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "rad_distance", timeoutTicks = 20)
    public static void doseRateFallsWithSquareOfDistance(GameTestHelper h) {
        for (int x = 0; x <= 6; x++) h.setBlock(new BlockPos(x, 4, 1), Blocks.AIR);
        BlockPos src = h.absolutePos(new BlockPos(1, 4, 1));
        Vec3 c = Vec3.atCenterOf(src);
        double near = RadiationManager.sourceRate(h.getLevel(), src, 0.1, c.add(2, 0, 0));
        double far = RadiationManager.sourceRate(h.getLevel(), src, 0.1, c.add(4, 0, 0));
        if (near <= 0) throw new GameTestAssertException("Quelle strahlt nicht");
        double ratio = far / near;
        if (Math.abs(ratio - 0.25) > 0.01) throw new GameTestAssertException("Doppelter Abstand: Faktor " + ratio + " statt 0,25");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_shield", timeoutTicks = 20)
    public static void blocksShieldRadiation(GameTestHelper h) {
        for (int x = 0; x <= 6; x++) h.setBlock(new BlockPos(x, 4, 3), Blocks.AIR);
        BlockPos src = h.absolutePos(new BlockPos(1, 4, 3));
        BlockPos wall = new BlockPos(3, 4, 3);
        Vec3 target = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 4, 3)));
        double open = Shielding.trace(h.getLevel(), Vec3.atCenterOf(src), target, src, BlockPos.containing(target));
        if (Math.abs(open - 1.0) > 1e-9) throw new GameTestAssertException("Freie Sicht nicht 1: " + open);
        double[] got = new double[3];
        net.minecraft.world.level.block.Block[] mats = {Blocks.DIRT, Blocks.GRAY_CONCRETE, Blocks.IRON_BLOCK};
        for (int i = 0; i < mats.length; i++) {
            h.setBlock(wall, mats[i]);
            got[i] = Shielding.trace(h.getLevel(), Vec3.atCenterOf(src), target, src, BlockPos.containing(target));
        }
        if (!(got[0] < 1.0 && got[1] < got[0] && got[2] < got[1])) {
            throw new GameTestAssertException("Abschirmung Erde/Beton/Eisen: " + got[0] + " / " + got[1] + " / " + got[2]);
        }
        if (Math.abs(got[1] - RadiationValues.SHIELD_CONCRETE) > RadiationValues.SHIELD_CONCRETE * 0.05) {
            throw new GameTestAssertException("Ein Block Beton laesst " + got[1] + " durch statt " + RadiationValues.SHIELD_CONCRETE);
        }
        h.setBlock(wall, Blocks.WATER);
        double water = Shielding.trace(h.getLevel(), Vec3.atCenterOf(src), target, src, BlockPos.containing(target));
        if (water >= 1.0) throw new GameTestAssertException("Wasser schirmt nicht ab");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_spray", timeoutTicks = 40)
    public static void sprayerCleansGroundAndPlayerButNotRod(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos rodRel = new BlockPos(5, 2, 5);
        BlockPos rod = h.absolutePos(rodRel);
        h.setBlock(rodRel, ReactorRegistry.FUEL_ROD_BLOCK.get());
        try {
            Contamination.add(lv, ground, 0.02f);
            Contamination.add(lv, rod, 0.02f);
            DeconSprayerItem.spray(lv, ground);
            if (Contamination.zones(lv).get(ground) > 0) throw new GameTestAssertException("Boden nicht gereinigt: " + Contamination.zones(lv).get(ground));
            DeconSprayerItem.spray(lv, rod);
            if (Contamination.zones(lv).get(rod) < 0.02f - 1e-6) throw new GameTestAssertException("Reiniger wirkt am Brennstab: " + Contamination.zones(lv).get(rod));

            ServerPlayer sp = AuditFixTests.testPlayer(h);
            ItemStack sprayer = new ItemStack(ProtectionRegistry.SPRAYER.get());
            Contamination.setBody(sp, 0.01);
            sprayer.getItem().interactLivingEntity(sprayer, sp, sp, InteractionHand.MAIN_HAND);
            double body = Contamination.body(sp);
            Contamination.setBody(sp, 0);
            if (body > 0.01 * Contamination.SPRAY_KEEP + 1e-9) throw new GameTestAssertException("Spieler nicht abgesprueht: " + body);
            if (sprayer.getDamageValue() != 1) throw new GameTestAssertException("Kein Wasser verbraucht");
            h.succeed();
        } finally {
            Contamination.clean(lv, ground, 1, 1f);
            h.setBlock(rodRel, Blocks.AIR);
            Contamination.clean(lv, rod, 0, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_debug", timeoutTicks = 20)
    public static void debugListsRadiatingBlocks(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos src = h.absolutePos(new BlockPos(1, 4, 1));
        BlockPos spot = h.absolutePos(new BlockPos(4, 2, 4));
        try {
            RadiationManager.emit(lv, src, 0.25);
            Contamination.add(lv, spot, 0.01f);
            var map = RadiationManager.debugSources(lv, Vec3.atCenterOf(src), RadiationManager.DEBUG_RANGE);
            if (Math.abs(map.get(src.asLong()) - 0.25f) > 1e-6) throw new GameTestAssertException("Quelle fehlt: " + map.get(src.asLong()));
            if (Math.abs(map.get(spot.asLong()) - 0.01f) > 1e-6) throw new GameTestAssertException("Kontamination fehlt: " + map.get(spot.asLong()));
            var far = RadiationManager.debugSources(lv, Vec3.atCenterOf(src).add(200, 0, 0), RadiationManager.DEBUG_RANGE);
            if (far.containsKey(src.asLong())) throw new GameTestAssertException("Quelle ausserhalb der Reichweite gelistet");
            h.succeed();
        } finally {
            Contamination.clean(lv, spot, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_iodine", timeoutTicks = 20)
    public static void iodineLowersInternalDose(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        sp.getInventory().clearContent();
        try {
            Contamination.setBody(sp, 0.01);
            double without = RadiationManager.carried(sp);
            RadiationManager.takeIodine(sp);
            if (!RadiationManager.iodineActive(sp)) throw new GameTestAssertException("Iod wirkt nicht");
            double with = RadiationManager.carried(sp);
            if (Math.abs(with - without * RadiationValues.IODINE_FACTOR) > 1e-9) throw new GameTestAssertException("Mit Iod " + with + " statt " + without * RadiationValues.IODINE_FACTOR);
            h.succeed();
        } finally {
            Contamination.setBody(sp, 0);
            sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        }
    }
}
