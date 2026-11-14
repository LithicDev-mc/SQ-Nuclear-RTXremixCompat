package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoEvents;
import com.sqc.nuclearsq.exo.ExoJetpack;
import com.sqc.nuclearsq.exo.ExoMining;
import com.sqc.nuclearsq.exo.ExoModule;
import com.sqc.nuclearsq.exo.ExoOre;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.exo.ModuleEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExoModuleMiningTests {
    private ExoModuleMiningTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "exo_modules_mining", timeoutTicks = 20)
    public static void areaShape(GameTestHelper h) {
        BlockPos o = new BlockPos(10, 20, 30);
        List<BlockPos> a3 = ExoMining.shape(o, Direction.UP, Vec3.ZERO, 3);
        List<BlockPos> a5 = ExoMining.shape(o, Direction.NORTH, Vec3.ZERO, 5);
        List<BlockPos> a2 = ExoMining.shape(o, Direction.UP, new Vec3(0.3, 0.5, -0.3), 2);
        if (a3.size() != 8 || a5.size() != 24 || a2.size() != 3) throw new GameTestAssertException("Groessen falsch: " + a3.size() + "/" + a5.size() + "/" + a2.size());
        if (a3.stream().anyMatch(p -> p.getY() != 20 || p.equals(o) || Math.abs(p.getX() - 10) > 1 || Math.abs(p.getZ() - 30) > 1)) throw new GameTestAssertException("3x3 liegt nicht in der Ebene");
        if (a5.stream().anyMatch(p -> p.getZ() != 30 || Math.abs(p.getX() - 10) > 2 || Math.abs(p.getY() - 20) > 2)) throw new GameTestAssertException("5x5 an der Nordseite liegt nicht in der Ebene");
        if (!a2.contains(o.east()) || !a2.contains(o.north()) || !a2.contains(o.north().east())) throw new GameTestAssertException("2x2 liegt nicht auf der Seite des Treffpunkts");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_mining", timeoutTicks = 20)
    public static void levelsAndLimits(GameTestHelper h) {
        if (ExoModule.of("exo_module_area_3_sqc") == null || ExoModule.of("exo_module_area_4_sqc") != null) throw new GameTestAssertException("Flaeche: genau Stufe 1 bis 3 erwartet");
        if (ExoModule.of("exo_module_claws_1_sqc") == null || ExoModule.of("exo_module_claws_2_sqc") != null) throw new GameTestAssertException("Krallen: genau eine Stufe erwartet");
        if (ExoModule.of("exo_module_vein_5_sqc") == null || ExoModule.of("exo_module_ore_5_sqc") == null) throw new GameTestAssertException("Verbund und Erzfinder: Stufe 5 fehlt");
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        ItemStack chest = new ItemStack(ExoRegistry.ARMOR.get(1).get());
        chest.set(ExoRegistry.MODULES.get(), List.of(new ModuleEntry("exo_module_vein_3_sqc", 3), new ModuleEntry("exo_module_claws_1_sqc", 1)));
        chest.set(ExoRegistry.ACTIVE.get(), true);
        sp.setItemSlot(EquipmentSlot.CHEST, chest);
        if (ExoEvents.level(sp, ExoModule.Kind.VEIN) != 3 || ExoEvents.level(sp, ExoModule.Kind.CLAWS) != 1) throw new GameTestAssertException("Stufen am getragenen Teil falsch");
        if (ExoMining.VEIN_LIMIT[3] != 24) throw new GameTestAssertException("Verbund III: 24 Bloecke erwartet");
        chest.set(ExoRegistry.ACTIVE.get(), false);
        if (ExoEvents.level(sp, ExoModule.Kind.VEIN) != 0) throw new GameTestAssertException("Verbund wirkt ohne Strom");
        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_mining", timeoutTicks = 20)
    public static void clawsDetectWall(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        Vec3 air = h.absoluteVec(new Vec3(7.7, 6, 7.5));
        sp.setPos(air.x, air.y, air.z);
        if (ExoJetpack.touchesWall(sp)) throw new GameTestAssertException("Wand erkannt, obwohl in der Luft keine da ist");
        Vec3 ground = h.absoluteVec(new Vec3(7.5, 4, 7.5));
        sp.setPos(ground.x, ground.y, ground.z);
        if (ExoJetpack.touchesWall(sp)) throw new GameTestAssertException("Der Boden unter den Fuessen zaehlt als Wand");
        sp.setPos(air.x, air.y, air.z);
        h.setBlock(new BlockPos(8, 6, 7), Blocks.STONE);
        if (!ExoJetpack.touchesWall(sp)) throw new GameTestAssertException("Wand neben dem Spieler nicht erkannt");
        h.setBlock(new BlockPos(8, 6, 7), Blocks.AIR);
        if (ExoJetpack.touchesWall(sp)) throw new GameTestAssertException("Wand erkannt, nachdem sie entfernt wurde");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "exo_modules_mining", timeoutTicks = 20)
    public static void oreFinderFindsNearest(GameTestHelper h) {
        BlockPos near = new BlockPos(4, 2, 4), far = new BlockPos(9, 2, 9);
        h.setBlock(near, Blocks.IRON_ORE);
        h.setBlock(far, Blocks.COAL_ORE);
        BlockPos center = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos found = ExoOre.nearest(h.getLevel(), center, 16);
        if (found == null || !found.equals(h.absolutePos(near))) throw new GameTestAssertException("Naechstes Erz nicht gefunden: " + found);
        if (ExoOre.nearest(h.getLevel(), center, 1) != null) throw new GameTestAssertException("Erz ausserhalb der Reichweite gefunden");
        h.setBlock(near, Blocks.AIR);
        h.setBlock(far, Blocks.AIR);
        h.succeed();
    }
}
