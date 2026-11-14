package com.sqc.nuclearsq.robotics;

import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.robotics.RodGripperBlockEntity.Input;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RodHook {
    public int depth;
    public boolean holding, rodHere;
    public String problem = "idle";
    private boolean clamped;

    public BlockPos at(BlockPos block) {
        return block.below(1 + depth);
    }

    public void scan(ServerLevel sl, BlockPos block) {
        BlockPos h = at(block);
        boolean rod = RodGripperBlockEntity.rodAt(sl, h);
        clamped = rod && BigMachineBlockEntity.rodClamped(sl, h);
        rodHere = rod && !clamped;
        if (holding && !rodHere) holding = false;
    }

    private static boolean has(int in, Input i) {
        return (in & 1 << i.ordinal()) != 0;
    }

    public boolean act(ServerLevel sl, BlockPos block, int in, boolean powered) {
        boolean lift = has(in, Input.LIFT), lower = has(in, Input.LOWER);
        boolean pull = has(in, Input.PULL), grip = pull || has(in, Input.GRIP);
        if (!grip) holding = false;
        else if (!holding) holding = rodHere;
        int dir = pull ? (holding ? -1 : 1) : lift == lower ? 0 : lift ? -1 : 1;
        problem = in == 0 ? "idle" : grip && !holding && !pull ? (clamped ? "clamped" : "no_rod") : "";
        if (dir == 0) return false;
        int next = depth + dir;
        if (next < 0 || next > RodGripperBlockEntity.MAX_DEPTH) {
            problem = pull ? (holding ? "" : "no_rod") : "blocked";
        } else if (!powered) {
            problem = "power";
        } else if (move(sl, block, dir)) {
            depth = next;
            problem = "";
            return true;
        } else {
            problem = clamped ? "clamped" : "blocked";
        }
        return false;
    }

    private boolean move(ServerLevel sl, BlockPos block, int dir) {
        BlockPos hook = at(block);
        if (!holding) {
            BlockState s = sl.getBlockState(hook.below(dir));
            return s.canBeReplaced() || s.getBlock() instanceof FuelRodHeadBlock;
        }
        if (!sl.getBlockState(dir > 0 ? hook.below(1 + FuelRodHeadBlock.ROD_BLOCKS) : hook.above()).canBeReplaced()) return false;
        BlockState[] st = new BlockState[FuelRodHeadBlock.ROD_BLOCKS + 1];
        CompoundTag[] tag = new CompoundTag[st.length];
        for (int i = 0; i < st.length; i++) {
            BlockPos p = hook.below(i);
            st[i] = sl.getBlockState(p);
            BlockEntity be = sl.getBlockEntity(p);
            tag[i] = be == null ? null : be.saveWithoutMetadata(sl.registryAccess());
            sl.setBlock(p, st[i].setValue(ReactorProps.ROD_FORMED, false), 2);
        }
        for (int i = 0; i < st.length; i++) sl.setBlock(hook.below(i), Blocks.AIR.defaultBlockState(), 2);
        for (int i = 0; i < st.length; i++) {
            BlockPos p = hook.below(i + dir);
            sl.setBlock(p, st[i], 2);
            BlockEntity be = sl.getBlockEntity(p);
            if (be != null && tag[i] != null) be.loadWithComponents(tag[i], sl.registryAccess());
        }
        return true;
    }
}
