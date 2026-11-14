package com.sqc.nuclearsq.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class MachineModules {
    private ModuleBonus bonus = ModuleBonus.NONE;

    public ModuleBonus bonus() {
        return bonus;
    }

    public ModuleBonus scan(Level level, BlockPos a, BlockPos b, Component machineName) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Map<ModuleKind, List<ModuleBlockEntity>> found = new EnumMap<>(ModuleKind.class);
        for (ModuleKind k : ModuleKind.values()) found.put(k, new ArrayList<>());
        for (BlockPos p : BlockPos.betweenClosed(min.offset(-1, -1, -1), max.offset(1, 1, 1))) {
            boolean inside = p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
            if (inside || !level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) instanceof ModuleBlockEntity be) found.get(be.kind()).add(be);
        }
        List<ModuleBlockEntity> fans = found.get(ModuleKind.FAN), clocks = found.get(ModuleKind.OVERCLOCK),
                savers = found.get(ModuleKind.ENERGY_SAVER);
        bonus = new ModuleBonus(fans.size(), clocks.size(), savers.size());
        int oc = bonus.activeOverclocks();
        for (int i = 0; i < clocks.size(); i++) {
            clocks.get(i).report(machineName, i < oc, i < oc ? null : i >= ModuleKind.MAX ? "max" : "no_fan");
        }
        for (int i = 0; i < fans.size(); i++) fans.get(i).report(machineName, i < oc, i < oc ? null : "fan_idle");
        for (int i = 0; i < savers.size(); i++) savers.get(i).report(machineName, i < ModuleKind.MAX, i < ModuleKind.MAX ? null : "max");
        return bonus;
    }
}
