package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.big.BigBattery;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class TerminalDataTests {
    private TerminalDataTests() {}

    private static final String FLOOR = "floor";

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    private static ContainerData serverData(BigMachineBlockEntity be) throws ReflectiveOperationException {
        BigMachine m = be.machine();
        if (m == BigMachine.CONTROL_ROD_DRIVE) {
            var t = BigMachineBlockEntity.class.getDeclaredMethod("terminalData");
            t.setAccessible(true);
            return (ContainerData) t.invoke(be);
        }
        if (be.battery() != null) return be.battery().menuData(m);
        var t = BigMachineBlockEntity.class.getDeclaredMethod("machineData");
        t.setAccessible(true);
        return (ContainerData) t.invoke(be);
    }

    @GameTest(template = FLOOR, batch = "terminal_data", timeoutTicks = 40)
    public static void everyBigMachineTerminalStaysInsideItsData(GameTestHelper h) {
        for (BigMachine m : BigMachine.values()) {
            try {
                var be = new BigMachineBlockEntity(BlockPos.ZERO, BigMachineRegistry.CONTROLLERS.get(m).get().defaultBlockState());
                int kind = m == BigMachine.CONTROL_ROD_DRIVE ? DeviceMenu.ROD_DRIVE : be.battery() != null ? DeviceMenu.BIG_BATTERY : DeviceMenu.BIG;
                int count = DeviceMenu.count(kind);
                ContainerData server = serverData(be);
                if (server.getCount() != count) fail(m.id + ": Server liefert " + server.getCount() + " Datenfelder, Client legt " + count + " an");
                ContainerData client = new SimpleContainerData(count);
                for (int i = 0; i < count; i++) client.set(i, server.get(i) & 0xFFFF);
                if (kind == DeviceMenu.BIG) {
                    int rows = BigMachineBlockEntity.termRows(m);
                    for (int row = 0; row < rows; row++) {
                        int base = BigMachineBlockEntity.TERM_PORTS + BigMachineBlockEntity.PORT_VALUES * row;
                        for (int k = 0; k < BigMachineBlockEntity.PORT_VALUES; k++) client.get(base + k);
                    }
                    if (m == BigMachine.FUSION_REACTOR) client.get(BigMachineBlockEntity.FUSION_DATA + 6);
                }
                if (kind == DeviceMenu.BIG_BATTERY && count != BigBattery.DATA) fail(m.id + ": Akku-Daten " + count + " statt " + BigBattery.DATA);
            } catch (ReflectiveOperationException | RuntimeException e) {
                fail(m.id + ": Terminal-Daten nicht lesbar: " + e);
            }
        }
        h.succeed();
    }
}
