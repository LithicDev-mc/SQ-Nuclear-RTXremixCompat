package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.module.ModuleBlock;
import com.sqc.nuclearsq.module.ModuleBonus;
import com.sqc.nuclearsq.module.ModuleKind;
import com.sqc.nuclearsq.module.ModuleRegistry;
import com.sqc.nuclearsq.module.ModuleState;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ModuleTests {
    private ModuleTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.SMALL_PARTS_ASSEMBLER;
    private static final BlockPos MIN = new BlockPos(2, 2, 2);

    private static BigMachineBlockEntity build(GameTestHelper h) {
        BlockPos ctrl = h.absolutePos(MIN).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        return be;
    }

    private static BlockState state(ModuleKind k) {
        return ModuleRegistry.BLOCK.get(k).get().defaultBlockState().setValue(ModuleBlock.FACING, Direction.WEST);
    }

    private static void put(GameTestHelper h, ModuleKind k, BlockPos rel) {
        h.setBlock(rel, state(k));
        if (k.tall) h.setBlock(rel.above(), state(k).setValue(ModuleBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static ModuleState lamp(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(ModuleBlock.STATE);
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(1000, false) > 0) { }
    }

    @GameTest(template = FLOOR, batch = "modules", timeoutTicks = 300)
    public static void modulesSpeedUpAndSaveEnergy(GameTestHelper h) {
        BigMachineBlockEntity be = build(h);
        put(h, ModuleKind.OVERCLOCK, new BlockPos(1, 2, 2));
        put(h, ModuleKind.OVERCLOCK, new BlockPos(1, 2, 3));
        put(h, ModuleKind.FAN, new BlockPos(1, 2, 4));
        put(h, ModuleKind.FAN, new BlockPos(5, 2, 2));
        put(h, ModuleKind.ENERGY_SAVER, new BlockPos(5, 2, 3));
        put(h, ModuleKind.ENERGY_SAVER, new BlockPos(5, 2, 4));
        h.runAfterDelay(45, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Kleinteil-Assembler wird nicht als aufgebaut erkannt");
            BigCrafter c = be.crafter();
            ModuleBonus b = c.modules();
            if (b.fans() != 2 || b.overclocks() != 2 || b.savers() != 2) throw new GameTestAssertException("Module gezaehlt: " + b);
            if (b.speedPct() != 150) throw new GameTestAssertException("Tempo 150 % erwartet, ist " + b.speedPct());
            if (b.energyPct() != 108) throw new GameTestAssertException("Strom 108 % erwartet, ist " + b.energyPct());
            for (BlockPos p : new BlockPos[] {new BlockPos(1, 2, 2), new BlockPos(1, 2, 4), new BlockPos(5, 2, 3), new BlockPos(5, 3, 3)}) {
                if (lamp(h, p) != ModuleState.ACTIVE) throw new GameTestAssertException("Lampe bei " + p + " nicht gruen: " + lamp(h, p));
            }
            be.items().insertItem(0, new ItemStack(Items.IRON_INGOT, 6), false);
            be.items().insertItem(1, new ItemStack(Items.REDSTONE, 4), false);
            be.items().insertItem(2, new ItemStack(Items.COPPER_INGOT, 4), false);
            be.items().insertItem(3, new ItemStack(Items.CRAFTING_TABLE), false);
            be.items().insertItem(4, new ItemStack(Items.BLAST_FURNACE), false);
            charge(be);
            h.runAfterDelay(5, () -> {
                int want = b.energy(480) * 91 / 100;
                if (c.energyPerTick() != want) throw new GameTestAssertException(want + " FE/t erwartet, ist " + c.energyPerTick());
                h.runAfterDelay(85, () -> {
                    if (!be.items().getStackInSlot(9).is(KitRegistry.KITS.get("reactor_assembler").get())) {
                        throw new GameTestAssertException("Mit Takt-Modulen nicht schneller fertig: " + be.items().getStackInSlot(9) + ", Fortschritt " + c.progress());
                    }
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "modules", timeoutTicks = 100)
    public static void overclockWithoutFanDoesNothing(GameTestHelper h) {
        BigMachineBlockEntity be = build(h);
        put(h, ModuleKind.OVERCLOCK, new BlockPos(1, 2, 2));
        h.runAfterDelay(45, () -> {
            ModuleBonus b = be.crafter().modules();
            if (b.overclocks() != 1) throw new GameTestAssertException("Takt-Modul nicht gefunden: " + b);
            if (b.speedPct() != 100 || b.energyPct() != 100) throw new GameTestAssertException("Ohne Luefter darf nichts wirken: " + b);
            if (lamp(h, new BlockPos(1, 2, 2)) != ModuleState.WARN) throw new GameTestAssertException("Lampe nicht gelb: " + lamp(h, new BlockPos(1, 2, 2)));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "modules", timeoutTicks = 60)
    public static void loneModuleLampStaysOff(GameTestHelper h) {
        put(h, ModuleKind.FAN, new BlockPos(3, 2, 3));
        h.runAfterDelay(45, () -> {
            if (lamp(h, new BlockPos(3, 2, 3)) != ModuleState.IDLE) throw new GameTestAssertException("Lampe ohne Anlage nicht aus");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "modules", timeoutTicks = 40)
    public static void energySaverPlacesAndBreaksAsOne(GameTestHelper h) {
        BlockPos ground = new BlockPos(3, 1, 3), low = ground.above(), up = low.above();
        h.setBlock(ground, Blocks.STONE);
        h.setBlock(low, Blocks.AIR);
        h.setBlock(up, Blocks.AIR);
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.setGameMode(GameType.SURVIVAL);
        ItemStack held = new ItemStack(ModuleRegistry.ITEM.get(ModuleKind.ENERGY_SAVER).get());
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(ground);
        sp.gameMode.useItemOn(sp, h.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        BlockState lo = h.getBlockState(low), hi = h.getBlockState(up);
        if (!lo.is(ModuleRegistry.BLOCK.get(ModuleKind.ENERGY_SAVER).get()) || lo.getValue(ModuleBlock.HALF) != DoubleBlockHalf.LOWER)
            throw new GameTestAssertException("Unterer Teil fehlt: " + lo);
        if (!hi.is(ModuleRegistry.BLOCK.get(ModuleKind.ENERGY_SAVER).get()) || hi.getValue(ModuleBlock.HALF) != DoubleBlockHalf.UPPER)
            throw new GameTestAssertException("Oberer Teil fehlt: " + hi);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        sp.gameMode.destroyBlock(h.absolutePos(up));
        h.runAfterDelay(2, () -> {
            if (!h.getBlockState(low).isAir() || !h.getBlockState(up).isAir()) throw new GameTestAssertException("Schrank nicht ganz abgebaut");
            AABB box = new AABB(h.absolutePos(ground)).inflate(3);
            int n = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .filter(e -> e.getItem().is(ModuleRegistry.ITEM.get(ModuleKind.ENERGY_SAVER).get())).mapToInt(e -> e.getItem().getCount()).sum();
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (n != 1) throw new GameTestAssertException("Genau ein Schrank als Item erwartet, sind " + n);
            h.succeed();
        });
    }
}
