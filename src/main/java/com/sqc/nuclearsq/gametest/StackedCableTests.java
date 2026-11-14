package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.transport.CableBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StackedCableTests {
    private StackedCableTests() {}

    private static final int DOWN = 1 << Direction.DOWN.ordinal();

    private static int hold(Level lv, BlockPos p) {
        return lv.getBlockEntity(p) instanceof ConduitBlockEntity be ? be.hold() : -1;
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    @GameTest(template = "floor", batch = "stacked_cable_gap", timeoutTicks = 60)
    public static void upperPostStandsOnLowerCable(GameTestHelper h) {
        Level lv = h.getLevel();
        BlockState power = TransportRegistry.CABLES.get(TransportTier.BASIC).get().defaultBlockState();
        BlockPos base = h.absolutePos(new BlockPos(6, 5, 7));
        BlockPos mid = (base.getX() & 3) != 0 ? base : base.east();
        for (int dx = -1; dx <= 1; dx++) lv.setBlockAndUpdate(mid.offset(dx, 0, 0), power);
        BlockPos top = mid.above(3);
        lv.setBlockAndUpdate(top, power);
        h.runAfterDelay(5, () -> {
            int t = hold(lv, top), m = hold(lv, mid);
            if ((t & DOWN) == 0 || t >>> 6 != 2 * CableBlock.POST_CABLE + 3) fail("Oberes Kabel steht nicht auf dem unteren: " + t);
            if ((m & DOWN) == 0 || m >>> 6 < 1 || m >>> 6 >= CableBlock.POST_CABLE) fail("Unteres Kabel hat unter der Saeule keine Stuetze zum Boden: " + m);
            lv.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
            h.runAfterDelay(3, () -> {
                if (hold(lv, mid) != 0) fail("Stuetze bleibt ohne oberes Kabel: " + hold(lv, mid));
                h.succeed();
            });
        });
    }

    @GameTest(template = "floor", batch = "stacked_cable_direct", timeoutTicks = 60)
    public static void directlyStackedCables(GameTestHelper h) {
        Level lv = h.getLevel();
        BlockPos low = h.absolutePos(new BlockPos(7, 5, 7)), up = low.above();
        lv.setBlockAndUpdate(low, TransportRegistry.CABLES.get(TransportTier.BASIC).get().defaultBlockState());
        lv.setBlockAndUpdate(up, DataRegistry.CABLE.get().defaultBlockState());
        h.runAfterDelay(5, () -> {
            int u = hold(lv, up), l = hold(lv, low);
            if ((u & DOWN) == 0 || u >>> 6 != CableBlock.POST_CABLE + 1) fail("Datenkabel steht nicht auf dem Stromkabel: " + u);
            if ((l & DOWN) == 0 || l >>> 6 >= CableBlock.POST_CABLE) fail("Stromkabel ohne Stuetze zum Boden: " + l);
            h.succeed();
        });
    }
}
