package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.DeconSprayerItem;
import com.sqc.nuclearsq.radiation.ItemContamination;
import com.sqc.nuclearsq.radiation.ItemContaminationEvents;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.radiation.Shielding;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationAusbauTests {
    private RadiationAusbauTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "rad_item_zone", timeoutTicks = 40)
    public static void itemInZoneGetsContaminatedAndSprayerCleans(GameTestHelper h) {
        var lv = h.getLevel();
        BlockPos rel = new BlockPos(2, 3, 2);
        BlockPos ground = h.absolutePos(rel.below());
        ItemEntity ie = null;
        try {
            Contamination.add(lv, ground, 0.02f);
            Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
            ie = new ItemEntity(lv, at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, 3));
            ie.setNoGravity(true);
            ie.setDeltaMovement(Vec3.ZERO);
            lv.addFreshEntity(ie);
            float c = ItemContamination.contaminateFromGround(lv, ie);
            if (!(c > 0)) throw new GameTestAssertException("Item im kontaminierten Bereich bleibt sauber");
            DeconSprayerItem.spray(lv, h.absolutePos(rel));
            if (ItemContamination.get(ie.getItem()) != 0f) throw new GameTestAssertException("Reiniger laesst Item kontaminiert: " + ItemContamination.get(ie.getItem()));
            h.succeed();
        } finally {
            if (ie != null) ie.discard();
            Contamination.clean(lv, ground, 1, 1f);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_drops", timeoutTicks = 20)
    public static void mobDropsContaminatedOnlyAboveThreshold(GameTestHelper h) {
        var lv = h.getLevel();
        var pig = h.spawn(EntityType.PIG, new BlockPos(3, 3, 3));
        try {
            RadiationManager.setDose(pig, 3.0);
            double high = ItemContamination.dropContamination(lv, pig);
            if (!(high > 0)) throw new GameTestAssertException("3 Sv: Drops nicht verstrahlt");
            RadiationManager.setDose(pig, 0.5);
            double low = ItemContamination.dropContamination(lv, pig);
            if (low != 0) throw new GameTestAssertException("0,5 Sv: Drops verstrahlt " + low);
            h.succeed();
        } finally {
            RadiationManager.setDose(pig, 0);
            pig.discard();
        }
    }

    @GameTest(template = FLOOR, batch = "rad_eat", timeoutTicks = 20)
    public static void eatingContaminatedFoodRaisesDose(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        try {
            Contamination.setBody(sp, 0);
            double before = RadiationManager.carried(sp);
            ItemStack bread = new ItemStack(Items.BREAD);
            ItemContamination.set(bread, 0.001f);
            ItemContaminationEvents.onFinishUsing(new LivingEntityUseItemEvent.Finish(sp, bread, 0, ItemStack.EMPTY));
            double after = RadiationManager.carried(sp);
            if (!(after > before)) throw new GameTestAssertException("Essen ohne Wirkung: " + before + " -> " + after);
            h.succeed();
        } finally {
            Contamination.setBody(sp, 0);
        }
    }

    @GameTest(template = FLOOR, batch = "rad_lead", timeoutTicks = 20)
    public static void leadShieldsMoreThanConcrete(GameTestHelper h) {
        for (int x = 0; x <= 6; x++) h.setBlock(new BlockPos(x, 4, 5), Blocks.AIR);
        BlockPos src = h.absolutePos(new BlockPos(1, 4, 5));
        BlockPos wall = new BlockPos(3, 4, 5);
        Vec3 target = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 4, 5)));
        Block[] mats = {ProtectionRegistry.LEAD_BLOCK.get(), ProtectionRegistry.RADIATION_CONCRETE.get(), Blocks.GRAY_CONCRETE};
        double[] got = new double[mats.length];
        for (int i = 0; i < mats.length; i++) {
            h.setBlock(wall, mats[i]);
            got[i] = Shielding.trace(h.getLevel(), Vec3.atCenterOf(src), target, src, BlockPos.containing(target));
        }
        if (!(got[0] < got[1] && got[1] < got[2])) {
            throw new GameTestAssertException("Blei/Strahlenschutzbeton/Beton: " + got[0] + " / " + got[1] + " / " + got[2]);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rad_iodine_end", timeoutTicks = 20)
    public static void iodineWearsOff(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        try {
            Contamination.setBody(sp, 0.01);
            double without = RadiationManager.carried(sp);
            RadiationManager.takeIodine(sp);
            double with = RadiationManager.carried(sp);
            if (Math.abs(with / without - RadiationValues.IODINE_FACTOR) > 1e-6) throw new GameTestAssertException("Mit Iod " + with / without + " statt " + RadiationValues.IODINE_FACTOR);
            sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL, sp.level().getGameTime() - 1);
            double later = RadiationManager.carried(sp);
            if (Math.abs(later - without) > 1e-9) throw new GameTestAssertException("Nach Ablauf " + later / without + " statt 1,0");
            h.succeed();
        } finally {
            Contamination.setBody(sp, 0);
            sp.removeData(com.sqc.nuclearsq.radiation.RadiationAttachments.IODINE_UNTIL);
        }
    }
}
