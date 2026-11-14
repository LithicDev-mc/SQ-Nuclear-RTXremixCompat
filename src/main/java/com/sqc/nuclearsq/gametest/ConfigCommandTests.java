package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.ReactorDetonatorBlock;
import com.sqc.nuclearsq.radiation.RadiationDebug;
import com.sqc.nuclearsq.radiation.RadiationSickness;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.ModDamageTypes;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ConfigCommandTests {
    private ConfigCommandTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile CMD_PROFILE = new GameProfile(UUID.fromString("5c0f1a2b-3d4e-4f50-8a61-72b3c4d5e6f7"), "nsq_cmd");
    private static final GameProfile DET_PROFILE = new GameProfile(UUID.fromString("5c0f1a2b-3d4e-4f50-8a61-72b3c4d5e6f8"), "nsq_det");

    private static void run(GameTestHelper h, CommandSourceStack src, String cmd) {
        h.getLevel().getServer().getCommands().performPrefixedCommand(src.withSuppressedOutput(), cmd);
    }

    @GameTest(template = FLOOR, batch = "cfg_debug_cmd", timeoutTicks = 20)
    public static void debugCommandNeedsOperator(GameTestHelper h) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), CMD_PROFILE);
        try {
            RadiationDebug.set(sp, false);
            run(h, sp.createCommandSourceStack().withPermission(0), "nuclearsq debug radiation on");
            if (RadiationDebug.enabled(sp)) throw new GameTestAssertException("Debug ohne Operator-Recht eingeschaltet");
            run(h, sp.createCommandSourceStack().withPermission(0), "nuclearsq debug radiation");
            if (RadiationDebug.enabled(sp)) throw new GameTestAssertException("Debug ohne Operator-Recht umgeschaltet");

            CommandSourceStack op = sp.createCommandSourceStack().withPermission(2);
            run(h, op, "nuclearsq debug radiation on");
            if (!RadiationDebug.enabled(sp)) throw new GameTestAssertException("Debug mit Operator-Recht nicht an");
            run(h, op, "nuclearsq debug radiation");
            if (RadiationDebug.enabled(sp)) throw new GameTestAssertException("Umschalten ohne Argument schaltet nicht aus");
            run(h, op, "/nuclearsq debug radiation");
            if (!RadiationDebug.enabled(sp)) throw new GameTestAssertException("Umschalten ohne Argument schaltet nicht an");
            run(h, op, "nuclearsq debug radiation off");
            if (RadiationDebug.enabled(sp)) throw new GameTestAssertException("Debug mit off nicht aus");
        } finally {
            RadiationDebug.set(sp, false);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_detonator_recipe", timeoutTicks = 20)
    public static void detonatorHasNoRecipe(GameTestHelper h) {
        var id = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "reactor_detonator_sqc");
        if (h.getLevel().getRecipeManager().byKey(id).isPresent()) throw new GameTestAssertException("Rezept fuer den Zuendknopf existiert noch");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_detonator_use", timeoutTicks = 20)
    public static void detonatorSurvivalWithoutOpDoesNothing(GameTestHelper h) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), DET_PROFILE);
        try {
            sp.setGameMode(GameType.SURVIVAL);
            BlockPos rel = new BlockPos(2, 2, 2);
            h.setBlock(rel, AssemblyRegistry.DETONATOR.get());
            BlockPos abs = h.absolutePos(rel);
            BlockState state = h.getLevel().getBlockState(abs);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            if (sp.hasPermissions(2)) {
                NuclearSQ.LOGGER.warn("ConfigCommandTests: FakePlayer hat Operator-Recht, Ueberlebens-Pruefung uebersprungen");
            } else {
                if (ReactorDetonatorBlock.mayUse(sp)) throw new GameTestAssertException("Ueberleben ohne OP darf den Knopf benutzen");
                state.useWithoutItem(h.getLevel(), sp, hit);
                state.useWithoutItem(h.getLevel(), sp, hit);
                if (ReactorDetonatorBlock.isArmed(sp)) throw new GameTestAssertException("Knopf im Ueberlebensmodus scharf geschaltet");
            }
            sp.setGameMode(GameType.CREATIVE);
            if (!ReactorDetonatorBlock.mayUse(sp)) throw new GameTestAssertException("Kreativmodus darf den Knopf nicht benutzen");
        } finally {
            sp.setGameMode(GameType.SURVIVAL);
            ReactorDetonatorBlock.forget(sp.getUUID());
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_damage_type", timeoutTicks = 20)
    public static void radiationDamageHasOwnType(GameTestHelper h) {
        var lv = h.getLevel();
        var type = lv.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ModDamageTypes.RADIATION);
        if (type.isEmpty()) throw new GameTestAssertException("DamageType nuclearsq:radiation fehlt");
        if (!type.get().is(DamageTypeTags.BYPASSES_ARMOR)) throw new GameTestAssertException("Strahlung steht nicht in bypasses_armor");
        if (!"nuclearsq.radiation".equals(type.get().value().msgId())) throw new GameTestAssertException("message_id: " + type.get().value().msgId());

        var pig = h.spawn(EntityType.PIG, new BlockPos(3, 3, 3));
        float before = pig.getHealth();
        RadiationSickness.tick(lv, pig, 12.0, lv.getServer().getTickCount());
        DamageSource last = pig.getLastDamageSource();
        if (!(pig.getHealth() < before)) throw new GameTestAssertException("Kein Strahlenschaden bei 12 Sv");
        if (last == null || !last.is(ModDamageTypes.RADIATION)) {
            throw new GameTestAssertException("Strahlenschaden hat Typ " + (last == null ? "null" : last.getMsgId()));
        }
        pig.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void serverConfigValuesFollow(GameTestHelper h) {
        int old = NuclearConfig.BOMB_FUSE_SECONDS.get();
        try {
            if (!NuclearConfig.SPEC.isLoaded()) throw new GameTestAssertException("Server-Config nicht geladen");
            NuclearConfig.BOMB_FUSE_SECONDS.set(7);
            if (NuclearConfig.fuseSeconds() != 7) throw new GameTestAssertException("fuseSeconds() = " + NuclearConfig.fuseSeconds() + " statt 7");
            if (NuclearConfig.value(NuclearConfig.VEIN_MINER_LIMIT).intValue() != NuclearConfig.VEIN_MINER_LIMIT.get()) {
                throw new GameTestAssertException("value() weicht vom geladenen Wert ab");
            }
        } finally {
            NuclearConfig.BOMB_FUSE_SECONDS.set(old);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void difficultyPresetScalesValues(GameTestHelper h) {
        var old = NuclearConfig.DIFFICULTY.get();
        int fuse = NuclearConfig.BOMB_FUSE_SECONDS.get();
        try {
            NuclearConfig.BOMB_FUSE_SECONDS.set(20);
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.NORMAL);
            if (NuclearConfig.fuseSeconds() != 20 || NuclearConfig.blastFactor() != 1 || NuclearConfig.radiationFactor() != 1) throw new GameTestAssertException("Normal veraendert Werte");
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.HARDCORE);
            if (NuclearConfig.fuseSeconds() != 10 || NuclearConfig.blastFactor() <= 1 || NuclearConfig.radiationFactor() <= 1) throw new GameTestAssertException("Hardcore falsch");
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.RELAXED);
            if (NuclearConfig.fuseSeconds() != 30 || NuclearConfig.blastFactor() >= 1 || NuclearConfig.radiationFactor() >= 1) throw new GameTestAssertException("Entspannt falsch");
        } finally {
            NuclearConfig.DIFFICULTY.set(old);
            NuclearConfig.BOMB_FUSE_SECONDS.set(fuse);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void titlesFollowProgress(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.setData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Titles.SELECTED.get(), "");
        if (com.sqc.nuclearsq.progression.Titles.check(sp)) throw new GameTestAssertException("Titel ohne Fortschritt");
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "power:coal_generator");
        if (!com.sqc.nuclearsq.progression.Titles.check(sp) || !com.sqc.nuclearsq.progression.Titles.has(sp, "first_power")) throw new GameTestAssertException("Erster Strom fehlt");
        if (!com.sqc.nuclearsq.progression.Titles.ids().contains("stage_start") || com.sqc.nuclearsq.progression.Titles.has(sp, "stage_start")) throw new GameTestAssertException("Etappe zu frueh");
        var nodes = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        var seen = sp.getData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get());
        for (var n : nodes) seen.addAll(com.sqc.nuclearsq.progression.AchievementTree.proofItems(n));
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "stage_start") || !com.sqc.nuclearsq.progression.Titles.has(sp, "stage_beyond")) throw new GameTestAssertException("Etappen-Titel fehlen");
        for (var l : com.sqc.nuclearsq.progression.LineTrees.LINES)
            if (!com.sqc.nuclearsq.progression.Titles.has(sp, "line_" + l.key())) throw new GameTestAssertException("Linien-Titel fehlt: " + l.key());
        if (!sp.getData(com.sqc.nuclearsq.progression.Titles.SELECTED.get()).equals("first_power")) throw new GameTestAssertException("Erster Titel nicht gewaehlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void streakTitlesFollowHours(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Streak.TICKS.get(), 72000);
        com.sqc.nuclearsq.progression.Streak.apply(sp, false, false);
        if (com.sqc.nuclearsq.progression.Streak.ticks(sp) != 72000) throw new GameTestAssertException("Pause aendert die Serie");
        com.sqc.nuclearsq.progression.Streak.apply(sp, false, true);
        if (com.sqc.nuclearsq.progression.Streak.ticks(sp) != 72000 + com.sqc.nuclearsq.progression.Streak.STEP) throw new GameTestAssertException("Laufender Reaktor zaehlt nicht");
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "streak_1h") || com.sqc.nuclearsq.progression.Titles.has(sp, "streak_10h")) throw new GameTestAssertException("Serien-Titel falsch");
        com.sqc.nuclearsq.progression.Streak.apply(sp, true, true);
        if (com.sqc.nuclearsq.progression.Streak.ticks(sp) != 0) throw new GameTestAssertException("Alarm setzt nicht zurueck");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void stageRewardsGivenOnce(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        var seen = new java.util.LinkedHashSet<String>();
        for (var n : com.sqc.nuclearsq.progression.AchievementTree.NODES) seen.addAll(com.sqc.nuclearsq.progression.AchievementTree.proofItems(n));
        sp.setData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get(), seen);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        int stages = com.sqc.nuclearsq.progression.AchievementTree.STAGES.size();
        var inv = sp.getInventory();
        if (inv.countItem(net.minecraft.world.item.Items.IRON_INGOT) != 16 * stages || inv.countItem(net.minecraft.world.item.Items.GOLD_BLOCK) != 1)
            throw new GameTestAssertException("Belohnung falsch: " + inv.countItem(net.minecraft.world.item.Items.IRON_INGOT) + " Eisen, " + inv.countItem(net.minecraft.world.item.Items.GOLD_BLOCK) + " Trophaeen");
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (inv.countItem(net.minecraft.world.item.Items.IRON_INGOT) != 16 * stages) throw new GameTestAssertException("Belohnung doppelt");
        h.succeed();
    }
}
