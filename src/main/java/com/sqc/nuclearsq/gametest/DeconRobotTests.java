package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.CreativeSourceBlock;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.ItemContamination;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.TestSourceRegistry;
import com.sqc.nuclearsq.robotics.decon.DeconRegistry;
import com.sqc.nuclearsq.robotics.decon.DeconRobotEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DeconRobotTests {
    private DeconRobotTests() {}

    private static final String FLOOR = "floor";

    private static DeconRobotEntity spawn(GameTestHelper h, BlockPos rel) {
        ServerLevel lv = h.getLevel();
        Vec3 p = Vec3.atBottomCenterOf(h.absolutePos(rel));
        DeconRobotEntity r = DeconRegistry.ROBOT.get().create(lv);
        if (r == null) throw new GameTestAssertException("Roboter nicht erzeugbar");
        r.moveTo(p.x, p.y, p.z, 0f, 0f);
        r.setEnergy(DeconRobotEntity.BATTERY);
        r.setAgent(DeconRobotEntity.AGENT_MAX);
        lv.addFreshEntity(r);
        return r;
    }

    private static BlockPos front(DeconRobotEntity r) {
        Vec3 dir = Vec3.directionFromRotation(0, r.getYRot());
        return BlockPos.containing(r.getX() + dir.x * 1.3, r.getY() + 0.1, r.getZ() + dir.z * 1.3);
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void sweepRemovesContaminationIntoTank(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        ServerLevel lv = h.getLevel();
        BlockPos spot = front(r);
        Contamination.add(lv, spot, 0.01f);
        if (Contamination.zones(lv).get(spot) <= 0) throw new GameTestAssertException("Testkontamination fehlt");
        int n = r.sweep(lv);
        if (n < 1) throw new GameTestAssertException("Roboter reinigte nichts");
        if (Contamination.zones(lv).get(spot) > 0) throw new GameTestAssertException("Kontamination blieb liegen");
        if (r.waste() != n) throw new GameTestAssertException("Tank " + r.waste() + " statt " + n);
        if (r.energy() >= DeconRobotEntity.BATTERY) throw new GameTestAssertException("Reinigung kostete keinen Strom");
        if (r.agent() != DeconRobotEntity.AGENT_MAX - DeconRobotEntity.AGENT_COST) throw new GameTestAssertException("Reinigung kostete kein Reinigungsmittel");
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void cameraPitchIsClampedAndResets(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        r.input(0, 0, 200f);
        if (r.getXRot() != DeconRobotEntity.PITCH_MAX) throw new GameTestAssertException("Neigung nicht begrenzt: " + r.getXRot());
        r.input(0, 0, -200f);
        if (r.getXRot() != -DeconRobotEntity.PITCH_MAX) throw new GameTestAssertException("Neigung nicht begrenzt: " + r.getXRot());
        r.input(0, 0, Float.NaN);
        if (r.getXRot() != -DeconRobotEntity.PITCH_MAX) throw new GameTestAssertException("NaN veraenderte die Neigung");
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void noAgentStopsCleaning(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        ServerLevel lv = h.getLevel();
        BlockPos spot = front(r);
        r.setAgent(DeconRobotEntity.AGENT_COST - 1);
        Contamination.add(lv, spot, 0.01f);
        if (r.sweep(lv) != 0 || Contamination.zones(lv).get(spot) <= 0) throw new GameTestAssertException("Ohne Reinigungsmittel reinigte er trotzdem");
        Contamination.clean(lv, spot, 1, 1f);
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 60)
    public static void armGoesDownOnToggleAndUpOnStop(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setPos(r.getX(), r.getY(), r.getZ() + 3);
        if (r.armDown() || r.arm != 0) throw new GameTestAssertException("Arm startet nicht oben");
        r.startControl(player);
        r.toggleArm();
        if (!r.armDown()) throw new GameTestAssertException("Arm fuhr nicht runter");
        r.stopControl("");
        if (r.armDown()) throw new GameTestAssertException("Arm blieb nach dem Ende der Steuerung unten");
        r.toggleArm();
        h.runAfterDelay(DeconRobotEntity.ARM_TICKS + 2, () -> {
            if (r.arm != DeconRobotEntity.ARM_TICKS) throw new GameTestAssertException("Arm nicht unten: " + r.arm);
            r.toggleArm();
            h.runAfterDelay(DeconRobotEntity.ARM_TICKS + 2, () -> {
                if (r.arm != 0) throw new GameTestAssertException("Arm nicht wieder oben: " + r.arm);
                r.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void fullTankStopsCleaning(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        ServerLevel lv = h.getLevel();
        BlockPos spot = front(r);
        r.setWaste(DeconRobotEntity.WASTE_MAX);
        Contamination.add(lv, spot, 0.01f);
        if (r.sweep(lv) != 0 || Contamination.zones(lv).get(spot) <= 0) throw new GameTestAssertException("Voller Tank reinigte trotzdem");
        Contamination.clean(lv, spot, 1, 1f);
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void emptyTankMakesContaminatedRubble(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        r.setWaste(45);
        int items = r.emptyTank();
        if (items != 3) throw new GameTestAssertException("Schutt-Items " + items + " statt 3");
        if (r.waste() != 0) throw new GameTestAssertException("Tank nicht leer");
        var found = h.getLevel().getEntitiesOfClass(ItemEntity.class, r.getBoundingBox().inflate(3), e -> e.getItem().is(DeconRegistry.RUBBLE.get()));
        if (found.size() != 3) throw new GameTestAssertException("Gefunden " + found.size() + " Schutt-Items");
        if (ItemContamination.get(found.get(0).getItem()) <= 0) throw new GameTestAssertException("Schutt strahlt nicht");
        found.forEach(ItemEntity::discard);
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void chargesNextToPowerSource(GameTestHelper h) {
        BlockPos src = new BlockPos(8, 4, 6);
        h.setBlock(src, TestSourceRegistry.ENERGY_SOURCE.get());
        if (!(h.getBlockState(src).getBlock() instanceof CreativeSourceBlock)) throw new GameTestAssertException("Kreativquelle fehlt");
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        r.setEnergy(1000);
        r.charge(h.getLevel());
        if (r.energy() <= 1000) throw new GameTestAssertException("Roboter lud nicht");
        r.discard();
        h.setBlock(src, Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void radiationDamagesAndLeadPlateHelps(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        DeconRobotEntity bare = spawn(h, new BlockPos(2, 4, 2));
        RadiationManager.emit(lv, bare.blockPosition(), 3.0);
        float before = bare.getHealth();
        for (int i = 0; i < 3; i++) bare.radiation(lv);
        float lostBare = before - bare.getHealth();
        if (lostBare < 3) throw new GameTestAssertException("Ungeschuetzt nur " + lostBare + " Schaden");
        if (bare.noise() <= 0) throw new GameTestAssertException("Kein Bildrauschen");
        DeconRobotEntity plated = spawn(h, new BlockPos(2, 4, 2));
        var player = FakePlayerFactory.getMinecraft(lv);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProtectionRegistry.LEAD_BLOCK_ITEM.get()));
        plated.interact(player, InteractionHand.MAIN_HAND);
        if (!plated.plated()) throw new GameTestAssertException("Bleiplatte nicht angebracht");
        float before2 = plated.getHealth();
        for (int i = 0; i < 3; i++) plated.radiation(lv);
        float lostPlated = before2 - plated.getHealth();
        if (lostPlated >= lostBare) throw new GameTestAssertException("Bleiplatte half nicht: " + lostPlated + " gegen " + lostBare);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        bare.discard();
        plated.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void brokenRobotRepairsWithSteelSheet(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        r.setHealth(1f);
        if (!r.broken()) throw new GameTestAssertException("Roboter mit 1 Herz ist nicht defekt");
        var player = FakePlayerFactory.getMinecraft(lv);
        var sheet = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "steel_sheet_sqc"));
        if (sheet == Items.AIR) throw new GameTestAssertException("Stahlblech nicht gefunden");
        for (int i = 0; i < 2; i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sheet));
            r.interact(player, InteractionHand.MAIN_HAND);
        }
        if (r.broken()) throw new GameTestAssertException("Reparatur wirkte nicht, Haltbarkeit " + r.getHealth());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void remoteBindingSurvivesInteract(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        var player = FakePlayerFactory.getMinecraft(lv);
        boolean oldCreative = player.getAbilities().instabuild;
        try {
            for (boolean creative : new boolean[] {false, true}) {
                player.getAbilities().instabuild = creative;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DeconRegistry.REMOTE.get()));
                player.interactOn(r, InteractionHand.MAIN_HAND);
                ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (!r.getUUID().equals(held.get(DeconRegistry.BOUND.get())))
                    throw new GameTestAssertException("Bindung fehlt in der Hand (kreativ=" + creative + ")");
                player.setPos(r.getX(), r.getY(), r.getZ() + 3);
                held.getItem().use(lv, player, InteractionHand.MAIN_HAND);
                if (DeconRobotEntity.controlledBy(player) != r)
                    throw new GameTestAssertException("Steuerung startete nicht nach dem Binden (kreativ=" + creative + ")");
                r.stopControl("stopped");
            }
        } finally {
            player.getAbilities().instabuild = oldCreative;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void platedAndBrokenAreSyncedState(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProtectionRegistry.LEAD_BLOCK_ITEM.get()));
        r.interact(player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!r.plated()) throw new GameTestAssertException("Bleiplatte nicht gesetzt");
        var tag = new net.minecraft.nbt.CompoundTag();
        r.addAdditionalSaveData(tag);
        DeconRobotEntity copy = spawn(h, new BlockPos(2, 4, 2));
        copy.readAdditionalSaveData(tag);
        if (!copy.plated()) throw new GameTestAssertException("Platte ging beim Laden verloren");
        r.setHealth(1f);
        if (!r.broken()) throw new GameTestAssertException("Defekt nicht erkannt");
        r.discard();
        copy.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 40)
    public static void attackOnRobotWhileControlledIsHarmless(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(6, 4, 6));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setPos(r.getX(), r.getY(), r.getZ() + 3);
        r.startControl(player);
        float before = r.getHealth();
        player.attack(r);
        if (r.getHealth() >= before) throw new GameTestAssertException("Angriff traf den Roboter nicht");
        if (!r.controlled()) throw new GameTestAssertException("Steuerung brach durch den Angriff ab");
        r.stopControl("");
        r.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "decon_robot", timeoutTicks = 1600)
    public static void zoneJobCleansAndParksAtStart(GameTestHelper h) {
        DeconRobotEntity r = spawn(h, new BlockPos(2, 4, 2));
        ServerLevel lv = h.getLevel();
        Vec3 home = r.position();
        BlockPos spot = h.absolutePos(new BlockPos(7, 4, 7));
        Contamination.add(lv, spot, 0.01f);
        var player = FakePlayerFactory.getMinecraft(lv);
        r.startZone(player, java.util.List.of(h.absolutePos(new BlockPos(5, 4, 5)), h.absolutePos(new BlockPos(9, 4, 9))));
        if (!r.zoneJob() || !r.armDown()) throw new GameTestAssertException("Auftrag startete nicht");
        h.runAfterDelay(20, () -> h.succeedWhen(() -> {
            if (r.zoneJob()) throw new GameTestAssertException("Roboter faehrt noch");
            if (Contamination.zones(lv).get(spot) > 0) throw new GameTestAssertException("Kontamination blieb liegen");
            if (r.position().distanceTo(home) > 0.6) throw new GameTestAssertException("Nicht am Startplatz: " + r.position().distanceTo(home));
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(r.getYRot())) > 1) throw new GameTestAssertException("Falsch herum geparkt: " + r.getYRot());
            r.discard();
        }));
    }
}
