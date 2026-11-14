package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ReactorPeripheralTests {
    private ReactorPeripheralTests() {}

    @GameTest(template = "floor", timeoutTicks = 80)
    public static void rodDrivePeripheralsAnswerWithNineRods(GameTestHelper h) {
        BigMachine m = BigMachine.CONTROL_ROD_DRIVE;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Antrieb ohne Controller");
        for (int k = 0; k < 9; k++) {
            BlockPos head = be.rodBase(be.shafts()[k]).above(9);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
                h.getLevel().setBlockAndUpdate(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
            }
            h.getLevel().setBlockAndUpdate(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true));
        }
        Map<String, BlockPos> ports = new LinkedHashMap<>();
        for (String n : new String[] {"rod_terminal", "control_signal", "flux_signal", "energy_in", "hydraulic_in", "primary_cold_in", "primary_hot_out"}) {
            BigMachine.Port p = m.port(n);
            if (p == null) throw new GameTestAssertException("Port " + n + " fehlt");
            ports.put(n, m.structure.world(ctrl, 0, p.x(), p.y(), p.z()));
        }
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Antrieb nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            if (be.shafts().length != 9 || be.rodCount() != 9) throw new GameTestAssertException(be.shafts().length + " Schaechte, " + be.rodCount() + " Staebe");
            for (Map.Entry<String, BlockPos> e : ports.entrySet()) {
                if (!(h.getLevel().getBlockEntity(e.getValue()) instanceof BigPortBlockEntity pb) || pb.findController() != be) {
                    throw new GameTestAssertException("Port " + e.getKey() + " gehoert nicht zum Antrieb");
                }
            }
            if (ModList.get().isLoaded("computercraft")) {
                try {
                    Cc.check(h, be, ctrl, ports);
                } catch (GameTestAssertException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new GameTestAssertException("Peripheral: " + ex);
                }
            }
            be.scram();
            h.succeed();
        });
    }

    private static final class Cc {
        static void check(GameTestHelper h, BigMachineBlockEntity be, BlockPos ctrl, Map<String, BlockPos> ports) throws Exception {
            var level = h.getLevel();
            String c = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(level, ctrl);
            if (!"sqc_reactor".equals(c)) throw new GameTestAssertException("Controller: Peripheral " + c);
            for (Map.Entry<String, BlockPos> e : ports.entrySet()) {
                boolean reactor = e.getKey().equals("rod_terminal") || e.getKey().endsWith("_signal");
                String want = reactor ? "sqc_reactor" : "sqc_machine", got = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(level, e.getValue());
                if (!want.equals(got)) throw new GameTestAssertException("Port " + e.getKey() + ": Peripheral " + got + " statt " + want);
            }
            for (BlockPos p : new BlockPos[] {ctrl, ports.get("rod_terminal"), ports.get("control_signal"), ports.get("flux_signal")}) {
                var r = new com.sqc.nuclearsq.compat.cc.ReactorPeripheral(level.getBlockEntity(p));
                if (!r.isFormed() || !"ok".equals(r.getStatus()) || !r.hasRod() || r.getFuel() != 100 || r.getMaxPower() <= 0 || r.getPower() < 0 || r.getHeat() < 0) {
                    throw new GameTestAssertException("sqc_reactor bei " + p + " antwortet falsch: " + r.getStatus() + ", Stab " + r.hasRod() + ", Brennstoff " + r.getFuel());
                }
            }
            long n = java.util.Arrays.stream(com.sqc.nuclearsq.compat.cc.ReactorPeripheral.class.getMethods())
                    .filter(f -> f.isAnnotationPresent(dan200.computercraft.api.lua.LuaFunction.class)).count();
            if (n < 5) throw new GameTestAssertException("sqc_reactor hat nur " + n + " Funktionen");
        }
    }
}
