package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.Contamination;
import com.sqc.nuclearsq.radiation.ContaminationMonitorBlock;
import com.sqc.nuclearsq.radiation.ContaminationMonitorBlockEntity;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class MonitorTests {
    private MonitorTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "contamination_monitor", timeoutTicks = 20)
    public static void monitorDetectsContamination(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 3, 4);
        h.setBlock(pos, ProtectionRegistry.MONITOR.get().defaultBlockState().setValue(ContaminationMonitorBlock.FACING, Direction.NORTH));
        ContaminationMonitorBlockEntity be = h.getBlockEntity(pos);
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        BlockPos abs = h.absolutePos(pos);
        try {
            sp.getInventory().clearContent();
            Contamination.setBody(sp, 0.001);
            if (!ContaminationMonitorBlock.isContaminated(sp)) throw new GameTestAssertException("1 mSv/h gilt nicht als kontaminiert");
            if (!be.check(List.of(sp))) throw new GameTestAssertException("Kein Alarm bei kontaminiertem Spieler");
            if (!h.getBlockState(pos).getValue(ContaminationMonitorBlock.ALARM)) throw new GameTestAssertException("Lampe nicht rot");
            int sig = h.getBlockState(pos).getSignal(h.getLevel(), abs, Direction.SOUTH);
            if (sig != 15) throw new GameTestAssertException("Redstone-Signal bei Alarm: " + sig);

            Contamination.setBody(sp, 0);
            if (ContaminationMonitorBlock.isContaminated(sp)) throw new GameTestAssertException("Sauberer Spieler gilt als kontaminiert");
            if (be.check(List.of(sp))) throw new GameTestAssertException("Alarm bei sauberem Spieler");
            if (h.getBlockState(pos).getValue(ContaminationMonitorBlock.ALARM)) throw new GameTestAssertException("Lampe bleibt rot");
            sig = h.getBlockState(pos).getSignal(h.getLevel(), abs, Direction.SOUTH);
            if (sig != 0) throw new GameTestAssertException("Redstone-Signal ohne Alarm: " + sig);
        } finally {
            Contamination.setBody(sp, 0);
            h.setBlock(pos, Blocks.AIR);
        }
        h.succeed();
    }
}
