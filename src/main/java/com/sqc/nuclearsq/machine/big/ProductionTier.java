package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.data.ComputeNet;
import com.sqc.nuclearsq.storage.StorageValues;
import net.minecraft.world.level.Level;

public final class ProductionTier {
    private ProductionTier() {}

    public static boolean handles(BigMachine m) {
        return m == BigMachine.REACTOR_ASSEMBLER || m == BigMachine.SMALL_PARTS_ASSEMBLER || m == BigMachine.COMPUTE_ASSEMBLER;
    }

    public static boolean turbo(BigMachine m) {
        return m == BigMachine.COMPUTE_ASSEMBLER;
    }

    public static int stage(int cpu, int ram) {
        StorageValues.Production p = StorageValues.production();
        int best = 0;
        for (int i = 0; i < p.stages(); i++) {
            if (cpu >= p.cpu()[i] && ram >= p.ram()[i]) best = i;
        }
        return best;
    }

    public static int top() {
        return StorageValues.production().stages() - 1;
    }

    private static int clamp(int stage) {
        return Math.max(0, Math.min(top(), stage));
    }

    public static int speedPct(BigMachine m, int stage) {
        StorageValues.Production p = StorageValues.production();
        return (turbo(m) ? p.turboSpeed() : p.normalSpeed())[clamp(stage)];
    }

    public static int powerPct(BigMachine m, int stage) {
        StorageValues.Production p = StorageValues.production();
        return (turbo(m) ? p.turboPower() : p.normalPower())[clamp(stage)];
    }

    public static int stageOf(Level level, BigMachineBlockEntity be) {
        if (!ComputeNet.required()) return top();
        ComputeNet.Share s = ComputeNet.share(level, be);
        return stage(s.cpu(), s.ram());
    }
}
