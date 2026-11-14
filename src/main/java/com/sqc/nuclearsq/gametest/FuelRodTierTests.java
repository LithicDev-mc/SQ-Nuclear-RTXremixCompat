package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class FuelRodTierTests {
    private FuelRodTierTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("5b1e0f3a-6c1d-4e55-9a1b-1f0c2d3e4f51"), "nsq_rod_tiers");

    @GameTest(template = FLOOR, batch = "rod_tiers", timeoutTicks = 20)
    public static void hullTiersHaveOutputAndCharge(GameTestHelper h) {
        List<Block> hulls = List.of(ReactorRegistry.FUEL_ROD_BLOCK.get(), ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED.get(),
                ReactorRegistry.FUEL_ROD_BLOCK_HARDENED.get(), ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL.get());
        float[] out = {1.0f, 3.75f, 7.5f, 12.5f};
        for (int i = 0; i < hulls.size(); i++) {
            BlockState st = hulls.get(i).defaultBlockState();
            if (FuelRodBlock.tier(st) != i + 1) throw new GameTestAssertException(hulls.get(i) + ": Stufe " + FuelRodBlock.tier(st));
            if (FuelRodBlock.output(st) != out[i]) throw new GameTestAssertException(hulls.get(i) + ": Faktor " + FuelRodBlock.output(st));
            BlockPos p = new BlockPos(1 + i, 2, 1);
            h.setBlock(p, st);
            if (!(h.getBlockEntity(p) instanceof FuelRodBlockEntity)) throw new GameTestAssertException(hulls.get(i) + " ohne Ladung (BlockEntity)");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rod_tiers", timeoutTicks = 40)
    public static void hardenedHullDropsWithCharge(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        for (Block b : List.of(ReactorRegistry.FUEL_ROD_BLOCK_HARDENED.get(), ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL.get())) {
            h.setBlock(p, b);
            if (!(h.getBlockEntity(p) instanceof FuelRodBlockEntity f)) throw new GameTestAssertException(b + " ohne BlockEntity");
            f.setRemaining(420);
            h.getLevel().destroyBlock(h.absolutePos(p), true);
            BlockPos a = h.absolutePos(p);
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(2));
            ItemEntity drop = drops.stream().filter(e -> e.getItem().is(b.asItem())).findFirst()
                    .orElseThrow(() -> new GameTestAssertException(b + " wirft kein Item ab"));
            Integer charge = drop.getItem().get(ModDataComponents.FUEL_CHARGE.get());
            if (charge == null || charge != 420) throw new GameTestAssertException(b + ": Ladung im Item " + charge + " statt 420");
            drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rod_tiers", timeoutTicks = 20)
    public static void dockerMustCarryHullTier(GameTestHelper h) {
        BlockPos head = new BlockPos(2, 6, 2);
        stack(h, head, ReactorRegistry.FUEL_ROD_BLOCK_HARDENED.get(), ReactorRegistry.FUEL_ROD_HEAD.get());
        if (click(h, head)) throw new GameTestAssertException("normaler Docker baut gehaertete Huellen zusammen");
        stack(h, head, ReactorRegistry.FUEL_ROD_BLOCK_HARDENED.get(), ReactorRegistry.FUEL_ROD_HEAD_HARDENED.get());
        if (!click(h, head)) throw new GameTestAssertException("gehaerteter Docker baut gehaertete Huellen nicht zusammen");
        if (h.getBlockState(head).getValue(FuelRodHeadBlock.HULL) != 3) throw new GameTestAssertException("Modell zeigt nicht Stufe 3");
        click(h, head);
        stack(h, head, ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL.get(), ReactorRegistry.FUEL_ROD_HEAD_HARDENED.get());
        if (click(h, head)) throw new GameTestAssertException("gehaerteter Docker baut experimentelle Huellen zusammen");
        stack(h, head, ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL.get(), ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL.get());
        if (!click(h, head)) throw new GameTestAssertException("experimenteller Docker baut experimentelle Huellen nicht zusammen");
        click(h, head);
        stack(h, head, ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED.get(), ReactorRegistry.FUEL_ROD_HEAD.get());
        h.setBlock(head.below(2), ReactorRegistry.FUEL_ROD_BLOCK.get());
        if (!click(h, head)) throw new GameTestAssertException("normaler Docker baut Stufe 1 + 2 nicht mehr zusammen");
        if (h.getBlockState(head).getValue(FuelRodHeadBlock.HULL) != 1) throw new GameTestAssertException("gemischter Stab zeigt nicht Stufe 1");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rod_tiers", timeoutTicks = 20)
    public static void upgradeNeedsFullHullAndReloadFillsIt(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        List<net.minecraft.world.item.Item> hulls = List.of(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get(), ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(),
                ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get());
        for (int i = 0; i < hulls.size(); i++) {
            net.minecraft.world.item.ItemStack full = new net.minecraft.world.item.ItemStack(hulls.get(i));
            net.minecraft.world.item.ItemStack burned = full.copy();
            burned.set(ModDataComponents.FUEL_CHARGE.get(), 300);
            net.minecraft.world.item.ItemStack spent = full.copy();
            spent.set(ModDataComponents.FUEL_CHARGE.get(), 0);
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hulls.get(i)).getPath();
            var reload = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "assembly/" + id + "_reload"))
                    .orElseThrow(() -> new GameTestAssertException(id + ": kein Rezept zum Neubeladen"));
            var need = ((com.sqc.nuclearsq.assembler.AssemblyRecipe) reload.value()).ingredients().get(0);
            if (!need.test(burned) || !need.test(spent) || need.test(full)) throw new GameTestAssertException(id + ": Neubeladen nimmt die falschen Huellen");
            if (reload.value().getResultItem(h.getLevel().registryAccess()).has(ModDataComponents.FUEL_CHARGE.get()))
                throw new GameTestAssertException(id + ": neu beladene Huelle ist nicht voll");
            if (i == hulls.size() - 1) continue;
            String next = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hulls.get(i + 1)).getPath();
            var up = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "assembly/" + next))
                    .orElseThrow(() -> new GameTestAssertException(next + ": kein Aufruest-Rezept"));
            var upNeed = ((com.sqc.nuclearsq.assembler.AssemblyRecipe) up.value()).ingredients().stream()
                    .filter(n -> n.test(full)).findFirst().orElseThrow(() -> new GameTestAssertException(next + ": volle Huelle passt nicht"));
            if (upNeed.test(burned) || upNeed.test(spent)) throw new GameTestAssertException(next + ": Aufruesten nimmt angebrannte Huellen");
            net.minecraft.world.item.ItemStack made = up.value().getResultItem(h.getLevel().registryAccess());
            if (!com.sqc.nuclearsq.reactor.FuelRodBlockItem.unloaded(made) || com.sqc.nuclearsq.reactor.FuelRodBlockItem.charge(made) != 0)
                throw new GameTestAssertException(next + ": aufgeruestete Huelle kommt nicht unbeladen heraus");
            if (com.sqc.nuclearsq.spent.SpentFuel.isSpentRod(made)) throw new GameTestAssertException(next + ": unbeladene Huelle gilt als abgebrannt");
            if (!need.test(made) && !((com.sqc.nuclearsq.assembler.AssemblyRecipe) recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    NuclearSQ.MODID, "assembly/" + next + "_reload")).orElseThrow().value()).ingredients().get(0).test(made))
                throw new GameTestAssertException(next + ": unbeladene Huelle laesst sich nicht beladen");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rod_tiers", timeoutTicks = 40)
    public static void unloadedHullStaysUnloaded(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        net.minecraft.world.item.ItemStack item = new net.minecraft.world.item.ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get());
        item.set(ModDataComponents.FUEL_CHARGE.get(), 0);
        item.set(ModDataComponents.FUEL_UNLOADED.get(), net.minecraft.util.Unit.INSTANCE);
        h.setBlock(p, ReactorRegistry.FUEL_ROD_BLOCK_HARDENED.get());
        if (!(h.getBlockEntity(p) instanceof FuelRodBlockEntity f)) throw new GameTestAssertException("ohne BlockEntity");
        f.applyComponentsFromItemStack(item);
        if (!f.isUnloaded() || f.getRemaining() != 0) throw new GameTestAssertException("Block uebernimmt 'unbeladen' nicht");
        h.getLevel().destroyBlock(h.absolutePos(p), true);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(p)).inflate(2));
        ItemEntity drop = drops.stream().filter(e -> e.getItem().is(ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get())).findFirst()
                .orElseThrow(() -> new GameTestAssertException("kein Item abgeworfen"));
        if (!com.sqc.nuclearsq.reactor.FuelRodBlockItem.unloaded(drop.getItem())) throw new GameTestAssertException("abgebaut nicht mehr unbeladen");
        h.succeed();
    }

    private static void stack(GameTestHelper h, BlockPos head, Block hull, Block docker) {
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) h.setBlock(head.below(i), hull);
        h.setBlock(head, docker);
    }

    private static boolean click(GameTestHelper h, BlockPos head) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), PROFILE);
        BlockPos abs = h.absolutePos(head);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), sp, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        return h.getLevel().getBlockState(abs).getValue(ReactorProps.ROD_FORMED);
    }
}
