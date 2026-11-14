package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlockEntity;
import com.sqc.nuclearsq.machine.CreativeSourceBlockEntity;
import com.sqc.nuclearsq.radiation.DeconShowerBlockEntity;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.registry.PowerRegistry;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.GridNodeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class MachineDataTests {
    private MachineDataTests() {}

    private static final String FLOOR = "floor";

    private static <T extends BlockEntity> T be(GameTestHelper h, BlockPos rel, Class<T> type) {
        BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(rel));
        if (!type.isInstance(be)) throw new GameTestAssertException("Keine " + type.getSimpleName() + " bei " + rel + ": " + be);
        return type.cast(be);
    }

    private static int index(List<DataSource> net, BlockPos pos) {
        for (int i = 0; i < net.size(); i++) if (net.get(i).pos().equals(pos)) return i + 1;
        throw new GameTestAssertException("Anlage bei " + pos + " fehlt im Netz (" + net.size() + " gefunden)");
    }

    @GameTest(template = FLOOR, batch = "machine_data_net", timeoutTicks = 80)
    public static void singleMachinesJoinDataNet(GameTestHelper h) {
        BlockPos console = new BlockPos(2, 2, 2);
        h.setBlock(console, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        for (int x = 3; x <= 9; x++) h.setBlock(new BlockPos(x, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        BlockPos shower = new BlockPos(3, 2, 3), shower2 = new BlockPos(6, 2, 3), solar = new BlockPos(9, 2, 3);
        h.setBlock(shower, ProtectionRegistry.SHOWER.get());
        h.setBlock(shower2, ProtectionRegistry.SHOWER.get());
        h.setBlock(solar, PowerRegistry.SOLAR_PANEL.get());
        h.runAfterDelay(3, () -> {
            BlockPos c = h.absolutePos(console);
            List<DataSource> net = DataNet.scan(h.getLevel(), c);
            if (net.size() != 3) throw new GameTestAssertException("Netz soll 3 Einzelmaschinen haben, hat " + net.size());
            for (BlockPos p : List.of(shower, shower2, solar)) index(net, h.absolutePos(p));

            DeconShowerBlockEntity sh = be(h, shower, DeconShowerBlockEntity.class);
            int n = index(net, h.absolutePos(shower));
            DataConsole.run(h.getLevel(), c, null, "set " + n + " enable 0");
            if (sh.enabled()) throw new GameTestAssertException("set " + n + " enable 0 schaltet die Dusche nicht aus");
            DataConsole.run(h.getLevel(), c, null, "set " + n + " enable 1");
            if (!sh.enabled()) throw new GameTestAssertException("set " + n + " enable 1 schaltet die Dusche nicht ein");
            sh.tank().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
            DataConsole.run(h.getLevel(), c, null, "set " + n + " dump.water 1");
            if (!sh.tank().isEmpty()) throw new GameTestAssertException("dump.water leert den Tank nicht");

            DeconShowerBlockEntity sh2 = be(h, shower2, DeconShowerBlockEntity.class);
            DataConsole.run(h.getLevel(), c, null, "set " + index(net, h.absolutePos(shower2)) + " enable 0");
            if (sh2.enabled() || !sh.enabled()) throw new GameTestAssertException("enable 0 trifft die falsche Dusche");

            DataSource so = DataSources.at(h.getLevel(), h.absolutePos(solar));
            if (so == null || so.value("fe_rate") == null || !so.settable().isEmpty()) throw new GameTestAssertException("Solarmodul liest falsch: " + so);
            List<Component> err = DataConsole.run(h.getLevel(), c, null, "set " + index(net, h.absolutePos(solar)) + " enable 0");
            if (err.isEmpty()) throw new GameTestAssertException("Solarmodul nimmt enable an");
            h.succeed();
        });
    }

    private static boolean notAMachine(BlockEntity be) {
        return be instanceof ConduitBlockEntity || be instanceof GridNodeBlockEntity || be instanceof CreativeSourceBlockEntity
                || be instanceof com.sqc.nuclearsq.transport.PowerGateBlockEntity || be instanceof FuelRodBlockEntity || be instanceof NuclearBombBlockEntity;
    }

    @GameTest(template = FLOOR, batch = "machine_data_all", timeoutTicks = 40)
    public static void everyMachineHasDataPort(GameTestHelper h) {
        BlockPos rel = new BlockPos(6, 3, 6);
        BlockPos pos = h.absolutePos(rel);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(b);
            if (!id.getNamespace().equals(NuclearSQ.MODID) || !(b instanceof EntityBlock)) continue;
            h.getLevel().setBlock(pos, b.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockEntity be = h.getLevel().getBlockEntity(pos);
            if (be != null && !notAMachine(be)) {
                boolean machine = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null
                        || h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
                for (Direction d : Direction.values()) {
                    machine |= h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, d) != null
                            || h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, d) != null;
                }
                if (machine) {
                    checked++;
                    if (!DataSources.isPort(h.getLevel(), pos)) missing.add(id.getPath());
                    else if (!(be instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity)
                            && !(be instanceof com.sqc.nuclearsq.turbine.TurbinePortBlockEntity)) {
                        DataSource s = DataSources.at(h.getLevel(), pos);
                        if (s == null || s.values().isEmpty()) missing.add(id.getPath() + " (keine Werte)");
                    }
                }
            }
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (checked < 4) throw new GameTestAssertException("Zu wenige Maschinen geprueft: " + checked);
        if (!missing.isEmpty()) throw new GameTestAssertException("Maschinen ohne Datenanschluss: " + missing);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "machine_data_cc", timeoutTicks = 40)
    public static void machinePeripheralEverywhere(GameTestHelper h) {
        if (!ModList.get().isLoaded("computercraft")) {
            h.succeed();
            return;
        }
        BlockPos pump = new BlockPos(3, 2, 3), console = new BlockPos(6, 2, 3), cable = new BlockPos(9, 2, 3);
        h.setBlock(pump, ProtectionRegistry.SHOWER.get());
        h.setBlock(console, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        h.setBlock(cable, DataRegistry.CABLE.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            String p = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(pump));
            String c = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(console));
            String k = com.sqc.nuclearsq.compat.cc.DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(cable));
            if (!"sqc_machine".equals(p)) throw new GameTestAssertException("Dekon-Dusche: Peripheral " + p);
            if (!"sqc_network".equals(c)) throw new GameTestAssertException("Konsole: Peripheral " + c);
            if (k != null) throw new GameTestAssertException("Kabel hat ein Peripheral: " + k);
            h.succeed();
        });
    }
}
