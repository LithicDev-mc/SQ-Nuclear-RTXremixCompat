package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.multiblock.PartBlockItem;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PartPlacementTests {
    private PartPlacementTests() {}

    private static final String FLOOR = "floor";
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    private static Item cat(String id) {
        return CatalogRegistry.BLOCK_ITEM_MAP.get(id).get();
    }

    private static boolean tryPlace(GameTestHelper h, Item item, GameType mode) {
        h.setBlock(GROUND, Blocks.STONE);
        h.setBlock(GROUND.above(), Blocks.AIR);
        BlockPos abs = h.absolutePos(GROUND);
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.setGameMode(mode);
        ItemStack held = new ItemStack(item);
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        try {
            sp.gameMode.useItemOn(sp, h.getLevel(), held, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            boolean placed = !h.getLevel().getBlockState(abs.above()).isAir();
            if (!placed && held.getCount() != 1) throw new GameTestAssertException(item + ": Stapel verbraucht, ohne zu setzen");
            return placed;
        } finally {
            h.setBlock(GROUND.above(), Blocks.AIR);
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            sp.setGameMode(GameType.SURVIVAL);
        }
    }

    @GameTest(template = FLOOR, batch = "part_placement", timeoutTicks = 20)
    public static void lockedPartsDoNotPlace(GameTestHelper h) {
        List<Item> locked = List.of(cat("pump_basic_sqc"), cat("translator_basic_sqc"), cat("vent_grille_sqc"),
                cat("moderator_coal_sqc"), cat("pressure_chamber_sqc"), cat("steam_purifier_sqc"),
                cat("cooling_tower_segment_sqc"), ReactorRegistry.CASING_ITEM.get(), MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get(),
                BigMachineRegistry.CONTROLLER_ITEMS.get(BigMachine.CONTROL_ROD_DRIVE).get(),
                BigMachineRegistry.PORT_ITEMS.get(BigPortKind.ENERGY).get());
        for (Item it : locked) {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                if (tryPlace(h, it, mode)) throw new GameTestAssertException(it + " laesst sich setzen (" + mode + ")");
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "part_placement", timeoutTicks = 20)
    public static void loadingAndAddOnsStillPlace(GameTestHelper h) {
        for (Item it : List.of(ReactorRegistry.FUEL_ROD_HEAD_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_REINFORCED_ITEM.get(),
                ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get(),
                ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(), ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(),
                ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get(), MultiblockRegistry.SHAFT_COUPLING_ITEM.get())) {
            if (!tryPlace(h, it, GameType.SURVIVAL)) throw new GameTestAssertException(it + " laesst sich nicht mehr setzen");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "part_placement", timeoutTicks = 20)
    public static void lockedSetMatchesList(GameTestHelper h) {
        for (String id : PartBlockItem.CATALOG) {
            Item it = cat(id);
            if (!(it instanceof PartBlockItem pb)) throw new GameTestAssertException(id + " ist nicht gesperrt");
            if (pb.getBlock().asItem() != it) throw new GameTestAssertException(id + ": Block zeigt nicht auf sein Item");
        }
        for (BigMachine m : BigMachine.values()) {
            if (!(BigMachineRegistry.CONTROLLER_ITEMS.get(m).get() instanceof PartBlockItem)) throw new GameTestAssertException(m + "-Controller nicht gesperrt");
        }
        for (var d : BigMachineRegistry.PORT_ITEMS.values()) if (!(d.get() instanceof PartBlockItem)) throw new GameTestAssertException(d.getId() + " nicht gesperrt");
        for (var d : MultiblockRegistry.TURBINE_PORT_ITEMS.values()) if (!(d.get() instanceof PartBlockItem)) throw new GameTestAssertException(d.getId() + " nicht gesperrt");
        for (Item it : List.of(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get(), ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(),
                ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get(),
                ReactorRegistry.FUEL_ROD_HEAD_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_REINFORCED_ITEM.get(),
                ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get(),
                MultiblockRegistry.SHAFT_COUPLING_ITEM.get())) {
            if (it instanceof PartBlockItem) throw new GameTestAssertException(it + " ist gesperrt, soll aber platzierbar bleiben");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "part_placement", timeoutTicks = 20)
    public static void placedPartsKeepWorking(GameTestHelper h) {
        BlockPos p = GROUND.above();
        h.setBlock(p, CatalogRegistry.BLOCK_MAP.get("pump_basic_sqc").get());
        h.getLevel().destroyBlock(h.absolutePos(p), true);
        h.succeedWhen(() -> h.assertItemEntityPresent(cat("pump_basic_sqc"), p, 2.0));
    }
}
