package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.registry.TestSourceRegistry;
import com.sqc.nuclearsq.robotics.pet.RoboCollarItem;
import com.sqc.nuclearsq.robotics.pet.RoboPetEntity;
import com.sqc.nuclearsq.robotics.pet.RoboPetKitItem;
import com.sqc.nuclearsq.robotics.pet.RoboPetRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RoboPetTests {
    private RoboPetTests() {}

    private static final String FLOOR = "floor";

    private static RoboPetEntity spawn(GameTestHelper h, EntityType<RoboPetEntity> type, BlockPos rel) {
        ServerLevel lv = h.getLevel();
        Vec3 p = Vec3.atBottomCenterOf(h.absolutePos(rel));
        RoboPetEntity e = type.create(lv);
        if (e == null) throw new GameTestAssertException("Tier nicht erzeugbar");
        e.moveTo(p.x, p.y, p.z, 0f, 0f);
        lv.addFreshEntity(e);
        return e;
    }

    private static ServerPlayer owner(GameTestHelper h, String name, BlockPos rel) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("nsq_robo_" + name).getBytes()), "[nsq_" + name + "]"));
        p.revive();
        Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(rel));
        p.moveTo(v.x, v.y, v.z, 0f, 0f);
        p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void leave(GameTestHelper h, ServerPlayer p) {
        h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void recipesOnlyInEquipmentAssembler(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (String kit : new String[] {"robo_dog_kit_sqc", "robo_cat_kit_sqc", "robo_ball_sqc", "robo_feather_wand_sqc",
                "robo_collar_sniffer_sqc", "robo_collar_tidy_sqc", "robo_collar_lamp_sqc"}) {
            if (rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "equipment_assembly/" + kit)).isEmpty()) throw new GameTestAssertException("Rezept fehlt: " + kit);
            for (String other : new String[] {"assembly/", "small_parts_assembly/", "heavy_assembly/"}) {
                if (rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, other + kit)).isPresent()) throw new GameTestAssertException(kit + " auch in " + other);
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void kitKeepsBatteryAndLyingWhenEmpty(GameTestHelper h) {
        RoboPetEntity cat = spawn(h, RoboPetRegistry.CAT.get(), new BlockPos(6, 4, 6));
        cat.setEnergy(1234);
        if (cat.lying()) throw new GameTestAssertException("Tier liegt mit Akku");
        var kit = cat.kitStack();
        if (!kit.is(RoboPetRegistry.CAT_KIT.get()) || RoboPetKitItem.energy(kit) != 1234) throw new GameTestAssertException("Kit ohne Akkustand");
        cat.setEnergy(0);
        if (!cat.lying()) throw new GameTestAssertException("Tier liegt nicht bei leerem Akku");
        if (RoboPetKitItem.energy(new net.minecraft.world.item.ItemStack(RoboPetRegistry.DOG_KIT.get())) != RoboPetEntity.BATTERY) throw new GameTestAssertException("neues Kit nicht voll");
        cat.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void chargesNextToPowerSource(GameTestHelper h) {
        BlockPos src = new BlockPos(8, 4, 6);
        h.setBlock(src, TestSourceRegistry.ENERGY_SOURCE.get());
        RoboPetEntity dog = spawn(h, RoboPetRegistry.DOG.get(), new BlockPos(6, 4, 6));
        dog.setEnergy(1000);
        dog.charge(h.getLevel());
        if (dog.energy() <= 1000) throw new GameTestAssertException("Hund lud nicht");
        dog.discard();
        h.setBlock(src, Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void pettingSitAndWrench(GameTestHelper h) {
        ServerPlayer p = owner(h, "pet", new BlockPos(6, 4, 8));
        RoboPetEntity dog = spawn(h, RoboPetRegistry.DOG.get(), new BlockPos(6, 4, 6));
        dog.tame(p);
        dog.setEnergy(777);
        if (!dog.isOwnedBy(p)) throw new GameTestAssertException("Besitzer nicht erkannt");
        dog.mobInteract(p, InteractionHand.MAIN_HAND);
        if (dog.pet() <= 0 || dog.isOrderedToSit()) throw new GameTestAssertException("Streicheln: pet=" + dog.pet() + " sitzt=" + dog.isOrderedToSit());
        p.setShiftKeyDown(true);
        dog.mobInteract(p, InteractionHand.MAIN_HAND);
        if (!dog.isOrderedToSit()) throw new GameTestAssertException("Schleichen + leere Hand setzt nicht Sitz");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.sqc.nuclearsq.transport.TransportRegistry.WRENCH.get()));
        dog.mobInteract(p, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        if (dog.isAlive()) throw new GameTestAssertException("Schraubenschluessel baut das Tier nicht ab");
        var kits = h.getLevel().getEntitiesOfClass(ItemEntity.class, dog.getBoundingBox().inflate(2), e -> e.getItem().is(RoboPetRegistry.DOG_KIT.get()));
        if (kits.size() != 1 || RoboPetKitItem.energy(kits.get(0).getItem()) != 777) throw new GameTestAssertException("Kit fehlt oder ohne Akkustand");
        kits.forEach(Entity::discard);
        leave(h, p);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void toysThrowBallAndWaveWand(GameTestHelper h) {
        ServerPlayer p = owner(h, "toy", new BlockPos(6, 4, 6));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RoboPetRegistry.BALL.get()));
        RoboPetRegistry.BALL.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        var balls = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4), e -> e.getItem().is(RoboPetRegistry.BALL.get()));
        if (balls.size() != 1 || balls.get(0).getOwner() != p || balls.get(0).getAge() != -32768) throw new GameTestAssertException("Ball nicht geworfen: " + balls.size());
        if (!p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) throw new GameTestAssertException("Ball blieb in der Hand");
        balls.forEach(Entity::discard);
        RoboPetEntity mine = spawn(h, RoboPetRegistry.CAT.get(), new BlockPos(7, 4, 6)), other = spawn(h, RoboPetRegistry.CAT.get(), new BlockPos(5, 4, 6));
        mine.tame(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RoboPetRegistry.WAND.get()));
        RoboPetRegistry.WAND.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        if (!mine.playing() || other.playing()) throw new GameTestAssertException("Federangel: eigene " + mine.playing() + ", fremde " + other.playing());
        if (!p.getItemInHand(InteractionHand.MAIN_HAND).is(RoboPetRegistry.WAND.get())) throw new GameTestAssertException("Federangel verbraucht");
        mine.discard();
        other.discard();
        leave(h, p);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 20)
    public static void dogSniffsDangerousRadiation(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        RoboPetEntity dog = spawn(h, RoboPetRegistry.DOG.get(), new BlockPos(6, 4, 6));
        BlockPos spot = h.absolutePos(new BlockPos(8, 4, 6));
        Contamination.add(lv, spot, 0.05f);
        dog.sniff(lv);
        int seen = dog.sniff();
        Contamination.clean(lv, spot, 1, 1f);
        dog.sniff(lv);
        if (seen != 2) throw new GameTestAssertException("Spuernase meldete Stufe " + seen + " statt 2");
        if (dog.sniff() != 0) throw new GameTestAssertException("Spuernase meldet nach Reinigung noch " + dog.sniff());
        dog.discard();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 60)
    public static void collarFitsReplacesAndLampLights(GameTestHelper h) {
        ServerPlayer p = owner(h, "collar", new BlockPos(3, 4, 6));
        RoboPetEntity dog = spawn(h, RoboPetRegistry.DOG.get(), new BlockPos(6, 4, 6));
        dog.tame(p);
        dog.setOrderedToSit(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RoboPetRegistry.COLLAR_TIDY.get()));
        dog.interact(p, InteractionHand.MAIN_HAND);
        if (dog.collar() != 0) throw new GameTestAssertException("Katzen-Modul passte an den Hund");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RoboPetRegistry.COLLAR_SNIFFER.get()));
        dog.interact(p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RoboPetRegistry.COLLAR_LAMP.get()));
        dog.interact(p, InteractionHand.MAIN_HAND);
        if (dog.collar() != RoboCollarItem.LAMP) throw new GameTestAssertException("Lampe nicht angelegt: " + dog.collar());
        if (h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, dog.getBoundingBox().inflate(3), e -> e.getItem().is(RoboPetRegistry.COLLAR_SNIFFER.get())).isEmpty())
            throw new GameTestAssertException("altes Modul fiel nicht heraus");
        BlockPos at = dog.blockPosition();
        h.runAfterDelay(25, () -> {
            if (!h.getLevel().getBlockState(at).is(net.minecraft.world.level.block.Blocks.LIGHT)) throw new GameTestAssertException("Lampe leuchtet nicht");
            dog.discard();
            if (h.getLevel().getBlockState(at).is(net.minecraft.world.level.block.Blocks.LIGHT)) throw new GameTestAssertException("Licht blieb nach dem Entfernen");
            leave(h, p);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robo_pet", timeoutTicks = 400)
    public static void tidyCatStoresInChest(GameTestHelper h) {
        ServerPlayer p = owner(h, "tidy", new BlockPos(2, 4, 2));
        BlockPos chest = new BlockPos(10, 4, 10);
        h.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
        RoboPetEntity cat = spawn(h, RoboPetRegistry.CAT.get(), new BlockPos(6, 4, 6));
        cat.tame(p);
        ItemStack mod = new ItemStack(RoboPetRegistry.COLLAR_TIDY.get());
        mod.set(RoboPetRegistry.CHEST.get(), h.absolutePos(chest));
        p.setItemInHand(InteractionHand.MAIN_HAND, mod);
        cat.interact(p, InteractionHand.MAIN_HAND);
        if (cat.collar() != RoboCollarItem.TIDY || !h.absolutePos(chest).equals(cat.chest())) throw new GameTestAssertException("Ordnung-Modul ohne Kiste angelegt");
        var drop = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), cat.getX() + 1.5, cat.getY(), cat.getZ(), new ItemStack(net.minecraft.world.item.Items.APPLE));
        h.getLevel().addFreshEntity(drop);
        h.succeedWhen(() -> {
            var c = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(chest));
            if (c == null || c.countItem(net.minecraft.world.item.Items.APPLE) != 1) throw new GameTestAssertException("Apfel nicht in der Kiste (traegt " + cat.carried() + ")");
            cat.discard();
            leave(h, p);
        });
    }
}
