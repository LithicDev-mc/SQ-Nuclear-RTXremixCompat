package com.sqc.nuclearsq.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.compat.cc.DataCcCompat;
import com.sqc.nuclearsq.compat.cc.StoragePeripheral;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StoragePeripheralTests {
    private StoragePeripheralTests() {}

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = "floor", batch = "storage_peripheral", timeoutTicks = 120)
    public static void storagePeripheralReadsNetwork(GameTestHelper h) {
        if (!ModList.get().isLoaded("computercraft")) {
            h.succeed();
            return;
        }
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2));
        for (MultiblockStructure.Cell c : BigMachine.SERVER_RACK_SMALL.structure.cells(ctrl, 0)) {
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        }
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity rack) || rack.rack() == null) {
            throw new GameTestAssertException("kein Rack-Controller");
        }
        BlockPos port = rack.machine().structure.world(ctrl, 0, 0, 0, 1);
        BlockPos terminal = port.offset(port.subtract(ctrl));
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        rack.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        rack.energy().add(rack.energy().getMaxEnergyStored());
        h.runAfterDelay(25, () -> {
            check(rack.rack().online(), "Rack nicht online");
            check("sqc_storage".equals(DataCcCompat.peripheralType(h.getLevel(), terminal)), "Peripheral sqc_storage am Terminal erwartet");
            var s = StorageNet.access(h.getLevel(), terminal);
            check(s.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 100, false) == 100, "Einlagern");
            check(s.insert(ItemKey.of(new ItemStack(Items.DIAMOND)), 5, false) == 5, "Einlagern Diamant");
            StoragePeripheral p = new StoragePeripheral(h.getLevel(), terminal);
            try {
                Map<String, Object> info = p.getInfo();
                check(((Number) info.get("racks")).intValue() == 1 && ((Number) info.get("drives")).intValue() == 1, "getInfo: " + info);
                check(((Number) info.get("used")).longValue() == 105, "belegt: " + info.get("used"));
                check(p.getItemCount() == 2, "Sorten: " + p.getItemCount());
                List<Map<String, Object>> all = p.list(Optional.empty(), Optional.empty());
                check(all.size() == 2, "list: " + all.size());
                check(p.list(Optional.of(1), Optional.of(1)).size() == 1, "list mit offset und limit");
                List<Map<String, Object>> hit = p.find("DIAM", Optional.empty());
                check(hit.size() == 1 && ((Number) hit.get(0).get("count")).longValue() == 5, "find diam: " + hit);
                check(((Number) p.getItem("minecraft:cobblestone").get("count")).longValue() == 100, "getItem Bruchstein");
                check(((Number) p.getItem("minecraft:stick").get("count")).longValue() == 0, "getItem fehlende Sorte");
                List<Map<String, Object>> racks = p.getRacks();
                check(racks.size() == 1 && Boolean.TRUE.equals(racks.get(0).get("online")), "getRacks: " + racks);
            } catch (dan200.computercraft.api.lua.LuaException e) {
                throw new GameTestAssertException("Lua-Fehler: " + e.getMessage());
            }
            h.succeed();
        });
    }
}
