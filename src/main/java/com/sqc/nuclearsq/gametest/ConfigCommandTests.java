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
    public static void hardcoreCostsMore(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        var bearing = com.sqc.nuclearsq.assembler.SmallPartsRecipe.forItem(rm, h.getLevel().registryAccess().registryOrThrow(Registries.ITEM).get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "brass_bearing_sqc")));
        var quantum = com.sqc.nuclearsq.assembler.AssemblyRecipe.forItem(rm, h.getLevel().registryAccess().registryOrThrow(Registries.ITEM).get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "quantum_computer_controller_sqc")));
        var fusion = rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "heavy_assembly/fusion_reactor_kit_sqc")).map(r -> (com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe) r.value()).orElseThrow();
        if (bearing == null || quantum == null) throw new GameTestAssertException("Rezepte fehlen");
        var old = NuclearConfig.DIFFICULTY.get();
        try {
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.NORMAL);
            int b = bearing.weight(), q = quantum.weight();
            String f = String.join("", fusion.pattern());
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.HARDCORE);
            if (bearing.weight() <= b || quantum.weight() <= q) throw new GameTestAssertException("Hardcore nicht teurer");
            if (String.join("", fusion.pattern()).replace(" ", "").length() <= f.replace(" ", "").length()) throw new GameTestAssertException("Hardcore-Muster fehlt");
            NuclearConfig.DIFFICULTY.set(NuclearConfig.Difficulty.RELAXED);
            if (bearing.weight() != b || !String.join("", fusion.pattern()).equals(f)) throw new GameTestAssertException("Entspannt nicht wie Normal");
        } finally {
            NuclearConfig.DIFFICULTY.set(old);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void titlesFollowProgress(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.setData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Titles.SELECTED.get(), "");
        sp.setData(com.sqc.nuclearsq.progression.Streak.TICKS.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Streak.TOTAL.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.CLEAN.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.FOSSIL.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.POWER_FE.get(), 0L);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), 0.0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.HIGH_DOSE.get(), false);
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

    private static net.minecraft.world.level.block.Block nsqBlock(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void newRanksFollowBuilding(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.setData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Streak.TICKS.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Streak.TOTAL.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Titles.SELECTED.get(), "");
        sp.setData(com.sqc.nuclearsq.progression.Titles.CLEAN.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.FOSSIL.get(), 0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.POWER_FE.get(), 0L);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), 0.0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.HIGH_DOSE.get(), false);
        if (com.sqc.nuclearsq.progression.Titles.check(sp)) throw new GameTestAssertException("Titel ohne Fortschritt");
        if (!com.sqc.nuclearsq.progression.Titles.join(sp) || com.sqc.nuclearsq.progression.Titles.join(sp)) throw new GameTestAssertException("Beitritts-Rang falsch");
        if (!sp.getData(com.sqc.nuclearsq.progression.Titles.SELECTED.get()).equals("upcoming_engineer")) throw new GameTestAssertException("Beitritts-Rang nicht gewaehlt");
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "formed:coal_generator");
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "constructor") || com.sqc.nuclearsq.progression.Titles.has(sp, "chemist")) throw new GameTestAssertException("Constructor falsch");
        var coal = nsqBlock("coal_generator_controller_sqc");
        var gas = nsqBlock("gas_generator_controller_sqc");
        var solar = nsqBlock("solar_panel_sqc");
        if (com.sqc.nuclearsq.progression.Titles.placed(sp, solar)) throw new GameTestAssertException("Zaehlt vor erstem Strom");
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "power:coal_generator");
        com.sqc.nuclearsq.progression.Titles.check(sp);
        for (int i = 0; i < 4; i++) com.sqc.nuclearsq.progression.Titles.placed(sp, coal);
        if (com.sqc.nuclearsq.progression.Titles.has(sp, "coal_mogul")) throw new GameTestAssertException("Coal Mogul vor der 5. Maschine");
        if (!com.sqc.nuclearsq.progression.Titles.placed(sp, gas) || !com.sqc.nuclearsq.progression.Titles.has(sp, "coal_mogul")) throw new GameTestAssertException("Coal Mogul fehlt");
        for (int i = 0; i < 5; i++) com.sqc.nuclearsq.progression.Titles.placed(sp, solar);
        if (com.sqc.nuclearsq.progression.Titles.has(sp, "environmental_engineer")) throw new GameTestAssertException("Gleichstand reicht nicht");
        com.sqc.nuclearsq.progression.Titles.placed(sp, nsqBlock("wind_turbine_small_controller_sqc"));
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "environmental_engineer")) throw new GameTestAssertException("Environmental Engineer fehlt");
        com.sqc.nuclearsq.progression.Titles.select(sp, "environmental_engineer");
        com.sqc.nuclearsq.progression.Titles.placed(sp, coal);
        com.sqc.nuclearsq.progression.Titles.placed(sp, coal);
        if (!sp.getData(com.sqc.nuclearsq.progression.Titles.SELECTED.get()).equals("coal_mogul")) throw new GameTestAssertException("Rang schwingt nicht zu Kohle");
        com.sqc.nuclearsq.progression.Titles.placed(sp, solar);
        com.sqc.nuclearsq.progression.Titles.placed(sp, solar);
        if (!sp.getData(com.sqc.nuclearsq.progression.Titles.SELECTED.get()).equals("environmental_engineer")) throw new GameTestAssertException("Rang schwingt nicht zu Solar");
        com.sqc.nuclearsq.progression.Titles.select(sp, "upcoming_engineer");
        if (sp.getData(com.sqc.nuclearsq.progression.Titles.CLEAN.get()) != 8 || sp.getData(com.sqc.nuclearsq.progression.Titles.FOSSIL.get()) != 7) throw new GameTestAssertException("Zaehlung falsch");
        if (com.sqc.nuclearsq.progression.Titles.placed(sp, nsqBlock("acid_plant_controller_sqc")) || com.sqc.nuclearsq.progression.Titles.placed(sp, net.minecraft.world.level.block.Blocks.STONE))
            throw new GameTestAssertException("Keine Strommaschine gezaehlt");
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "formed:acid_plant");
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "chemist")) throw new GameTestAssertException("Chemist fehlt");
        if (!sp.getData(com.sqc.nuclearsq.progression.Titles.SELECTED.get()).equals("upcoming_engineer")) throw new GameTestAssertException("Gewaehlter Rang ueberschrieben");
        com.sqc.nuclearsq.progression.GuideProgress.award(sp, "reactor_power");
        for (int i = 0; i < 6000 / com.sqc.nuclearsq.progression.Streak.STEP - 1; i++) com.sqc.nuclearsq.progression.Streak.apply(sp, i % 50 == 0, true);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "reactor_start") || com.sqc.nuclearsq.progression.Titles.has(sp, "reactor_newcomer")) throw new GameTestAssertException("Reactor Newcomer zu frueh");
        com.sqc.nuclearsq.progression.Streak.apply(sp, true, true);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "reactor_newcomer") || com.sqc.nuclearsq.progression.Titles.has(sp, "reactor_operator")) throw new GameTestAssertException("Reactor Newcomer falsch");
        sp.setData(com.sqc.nuclearsq.progression.Streak.TOTAL.get(), 72000);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (!com.sqc.nuclearsq.progression.Titles.has(sp, "reactor_operator")) throw new GameTestAssertException("Reactor Operator fehlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "cfg_server_config", timeoutTicks = 20)
    public static void moreRanksFollowProgress(GameTestHelper h) {
        var sp = AuditFixTests.testPlayer(h);
        sp.setData(com.sqc.nuclearsq.progression.GuideProgress.MILESTONES.get(), new java.util.LinkedHashSet<>());
        sp.setData(com.sqc.nuclearsq.progression.Titles.POWER_FE.get(), 0L);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), 0.0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.HIGH_DOSE.get(), false);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) if (slot.isArmor()) sp.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        String[] marks = {"oil", "fusion", "waste", "breeder", "chip", "storage", "bank", "decon"};
        String[] ranks = {"oil_baron", "fusion_pioneer", "waste_manager", "breeder", "chip_maker", "data_hoarder", "banker", "decon_specialist", "iron_man", "survivor", "power_baron"};
        for (String r : ranks) if (com.sqc.nuclearsq.progression.Titles.has(sp, r)) throw new GameTestAssertException("Rang zu frueh: " + r);
        for (String m : marks) if (!com.sqc.nuclearsq.progression.Titles.mark(sp, "rank:" + m) || com.sqc.nuclearsq.progression.Titles.mark(sp, "rank:" + m)) throw new GameTestAssertException("Marke falsch: " + m);
        com.sqc.nuclearsq.progression.Titles.power(sp, com.sqc.nuclearsq.progression.Titles.POWER_BARON_FE - 1);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), com.sqc.nuclearsq.radiation.RadiationValues.SV_DAMAGE - 0.1);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        for (int i = 0; i < 8; i++) if (!com.sqc.nuclearsq.progression.Titles.has(sp, ranks[i])) throw new GameTestAssertException("Rang fehlt: " + ranks[i]);
        if (com.sqc.nuclearsq.progression.Titles.has(sp, "iron_man") || com.sqc.nuclearsq.progression.Titles.has(sp, "survivor") || com.sqc.nuclearsq.progression.Titles.has(sp, "power_baron"))
            throw new GameTestAssertException("Rang ohne Bedingung");
        com.sqc.nuclearsq.progression.Titles.power(sp, 1);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), com.sqc.nuclearsq.radiation.RadiationValues.SV_DAMAGE);
        com.sqc.nuclearsq.progression.Titles.check(sp);
        if (com.sqc.nuclearsq.progression.Titles.has(sp, "survivor")) throw new GameTestAssertException("Survivor schon bei 3 Sv");
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), 0.01);
        for (var holder : com.sqc.nuclearsq.exo.ExoRegistry.ARMOR) {
            var item = holder.get();
            var stack = new net.minecraft.world.item.ItemStack(item);
            var mods = new java.util.ArrayList<com.sqc.nuclearsq.exo.ModuleEntry>();
            for (var k : com.sqc.nuclearsq.exo.ExoModule.Kind.values())
                if (k.piece == ((net.minecraft.world.item.ArmorItem) item).getType()) mods.add(new com.sqc.nuclearsq.exo.ModuleEntry("exo_module_" + k.id + "_1_sqc", 1));
            stack.set(com.sqc.nuclearsq.exo.ExoRegistry.MODULES.get(), mods);
            stack.set(com.sqc.nuclearsq.exo.ExoRegistry.ACTIVE.get(), true);
            sp.setItemSlot(((net.minecraft.world.item.ArmorItem) item).getType().getSlot(), stack);
        }
        com.sqc.nuclearsq.progression.Titles.check(sp);
        for (String r : ranks) if (!com.sqc.nuclearsq.progression.Titles.has(sp, r)) throw new GameTestAssertException("Rang fehlt: " + r);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) if (slot.isArmor()) sp.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        sp.setData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get(), 0.0);
        sp.setData(com.sqc.nuclearsq.progression.Titles.POWER_FE.get(), 0L);
        sp.setData(com.sqc.nuclearsq.progression.Titles.HIGH_DOSE.get(), false);
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
