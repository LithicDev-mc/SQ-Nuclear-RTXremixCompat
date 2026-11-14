package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.radiation.ContaminationMonitorBlock;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.storage.StorageRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PeripheralGapTests {
    private PeripheralGapTests() {}

    @GameTest(template = "floor", timeoutTicks = 40)
    public static void wifiAdapterAndContaminationMonitorHavePorts(GameTestHelper h) {
        BlockPos wifi = new BlockPos(1, 1, 1), monitor = new BlockPos(3, 1, 1);
        h.setBlock(wifi, StorageRegistry.WIFI_ADAPTER.get().defaultBlockState());
        h.setBlock(monitor, ProtectionRegistry.MONITOR.get().defaultBlockState());
        for (BlockPos rel : new BlockPos[]{wifi, monitor}) {
            DataSource s = DataSources.at(h.getLevel(), h.absolutePos(rel));
            if (s == null) throw new GameTestAssertException("Keine Datenquelle bei " + rel);
            if (s.value("enable") == null && rel.equals(wifi)) throw new GameTestAssertException("WLAN-Adapter ohne enable");
            if (ModList.get().isLoaded("computercraft")) {
                String p = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(rel));
                if (!"sqc_machine".equals(p)) throw new GameTestAssertException("Peripheral bei " + rel + ": " + p);
            }
        }
        DataSource mon = DataSources.at(h.getLevel(), h.absolutePos(monitor));
        boolean alarm = h.getBlockState(monitor).getValue(ContaminationMonitorBlock.ALARM);
        if ((mon.state() == DataSource.ALARM) != alarm) throw new GameTestAssertException("Zustand passt nicht zur Lampe");
        h.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 40)
    public static void controlElementsHaveControlPeripheral(GameTestHelper h) {
        BlockPos button = new BlockPos(1, 1, 1);
        h.setBlock(button, com.sqc.nuclearsq.data.DataRegistry.BUTTON.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(button)) instanceof com.sqc.nuclearsq.data.DataControlBlockEntity be)) {
            throw new GameTestAssertException("Kein Bedienelement");
        }
        int[] seen = {0};
        be.listeners.put(seen, (kind, value, player) -> {
            if (kind.equals("button") && value == 1) seen[0]++;
        });
        be.press(h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
        if (seen[0] != 1) throw new GameTestAssertException("Benutzung nicht gemeldet");
        if (ModList.get().isLoaded("computercraft")) {
            String p = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(button));
            if (!"sqc_control".equals(p)) throw new GameTestAssertException("Peripheral am Taster: " + p);
        }
        h.succeed();
    }
}
