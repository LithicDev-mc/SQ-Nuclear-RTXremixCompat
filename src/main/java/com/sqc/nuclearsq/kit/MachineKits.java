package com.sqc.nuclearsq.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.multiblock.Waterlog;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import com.sqc.nuclearsq.turbine.TurbineShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

public final class MachineKits {
    private MachineKits() {}

    public record Def(String id, MultiblockStructure structure, int[][] shellCells, @Nullable Block shell) {
        public Block controller() {
            return structure.block('@');
        }
    }

    public record Placement(Def def, BlockPos anchor, int rot) {
        public List<MultiblockStructure.Cell> cells() {
            return def.structure.cells(anchor, rot);
        }

        public List<BlockPos> shellCells() {
            List<BlockPos> out = new ArrayList<>();
            for (int[] c : def.shellCells) {
                if (def.structure.charAt(c[0], c[1], c[2]) != ' ') continue;
                out.add(def.structure.world(anchor, rot, c[0], c[1], c[2]));
            }
            return out;
        }

        public List<BlockPos> footprint() {
            List<BlockPos> out = new ArrayList<>();
            for (MultiblockStructure.Cell c : cells()) out.add(c.pos());
            out.addAll(shellCells());
            return out;
        }
    }

    private static Map<String, Def> defs;

    public static synchronized Map<String, Def> all() {
        if (defs == null) {
            Map<String, Def> m = new LinkedHashMap<>();
            m.put("turbine", new Def("turbine", MultiblockStructure.TURBINE, TurbineShapes.CELLS, MultiblockRegistry.TURBINE_SHELL.get()));
            for (BigMachine bm : BigMachine.values()) {
                m.put(bm.id, new Def(bm.id, bm.structure, bm.cells, bm.shellBlock()));
            }
            defs = m;
        }
        return defs;
    }

    @Nullable
    public static Def def(String id) {
        return all().get(id);
    }

    public static Placement placement(Def def, BlockPos target, Direction look) {
        int rot = MultiblockStructure.rotFor(look);
        MultiblockStructure s = def.structure;
        BlockPos off = s.world(BlockPos.ZERO, rot, s.width() / 2, 0, 0);
        return new Placement(def, target.subtract(off), rot);
    }

    public static Placement placement(Level level, Def def, BlockPos target, Direction look) {
        if (def.id.equals(BigMachine.CONTAINMENT.id)) {
            BigMachineBlockEntity drive = BigMachineBlockEntity.driveUnder(level, target);
            if (drive != null) {
                return new Placement(def, BigMachineBlockEntity.containmentAnchor(drive.getBlockPos(), drive.formedRot()), drive.formedRot());
            }
        }
        if (def.id.equals(BigMachine.CONTROL_ROD_DRIVE.id)) {
            BigMachineBlockEntity hull = BigMachineBlockEntity.containmentAround(level, target);
            if (hull != null) return new Placement(def, BigMachineBlockEntity.driveAnchorIn(hull.getBlockPos(), hull.formedRot()), hull.formedRot());
        }
        return placement(def, target, look);
    }

    public static boolean free(Level level, BlockPos p) {
        if (level.isOutsideBuildHeight(p)) return false;
        BlockState st = level.getBlockState(p);
        return st.isAir() || st.canBeReplaced() || st.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
    }

    public static List<BlockPos> blocked(Level level, Placement pl, @Nullable Player player) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : pl.footprint()) {
            if (!level.isLoaded(p) || !free(level, p) || KitMarks.marked(level, p) || (player != null && !level.mayInteract(player, p))) out.add(p);
        }
        return out;
    }

    public static void place(Level level, Placement pl) {
        place(level, pl, null);
    }

    public static boolean place(Level level, Placement pl, @Nullable Entity placer) {
        int flags = pl.def.structure.size >= BigMachineBlockEntity.QUIET_CELLS ? BigMachineBlockEntity.QUIET : Block.UPDATE_ALL;
        List<BlockSnapshot> snaps = new ArrayList<>();
        if (placer != null) {
            for (BlockPos p : pl.footprint()) snaps.add(BlockSnapshot.create(level.dimension(), level, p));
        }
        MultiblockStructure.Cell ctrl = null;
        for (MultiblockStructure.Cell c : pl.cells()) {
            if (c.c() == '@') {
                ctrl = c;
                continue;
            }
            level.setBlock(c.pos(), Waterlog.at(c.block().defaultBlockState(), level, c.pos()), flags);
        }
        if (ctrl != null) level.setBlock(ctrl.pos(), Waterlog.at(ctrl.block().defaultBlockState(), level, ctrl.pos()), Block.UPDATE_ALL);
        if (placer != null && !snaps.isEmpty() && EventHooks.onMultiBlockPlace(placer, snaps, Direction.UP)) {
            for (int i = snaps.size() - 1; i >= 0; i--) snaps.get(i).restore(flags);
            return false;
        }
        if (ctrl != null && placer instanceof net.minecraft.world.entity.player.Player pp
                && level.getBlockEntity(ctrl.pos()) instanceof BigMachineBlockEntity be) be.setOwner(pp.getUUID());
        KitMarks.mark(level, pl);
        level.playSound(null, pl.anchor(), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
        return true;
    }

    private static Set<Block> parts;

    public static synchronized boolean isPart(BlockState st) {
        if (parts == null) {
            Set<Block> s = new HashSet<>();
            for (Def d : all().values()) {
                for (MultiblockStructure.Cell c : d.structure.cells(BlockPos.ZERO, 0)) {
                    s.add(c.block());
                    if (c.alt() != net.minecraft.world.level.block.Blocks.AIR) s.add(c.alt());
                }
                if (d.shell != null) s.add(d.shell);
            }
            parts = s;
        }
        return parts.contains(st.getBlock());
    }

    @Nullable
    public static Placement owner(Level level, BlockPos pos) {
        return KitMarks.owner(level, pos);
    }

    public static int storedEnergy(Level level, BlockPos ctrl) {
        return level.getBlockEntity(ctrl) instanceof BigMachineBlockEntity be && be.battery() != null ? be.battery().store().getEnergyStored() : 0;
    }

    public static void restoreEnergy(Level level, Placement pl, int fe) {
        if (fe > 0 && level.getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be && be.battery() != null) {
            be.battery().store().setStored(fe);
            be.setChanged();
        }
    }

    public static boolean mayDismantle(ServerLevel level, Placement pl, Player player, BlockPos clicked) {
        return player.mayBuild() && level.mayInteract(player, clicked) && level.mayInteract(player, pl.anchor()) && mayTake(level, pl, player);
    }

    public static boolean mayTake(Level level, Placement pl, @Nullable Player player) {
        return !(player instanceof net.minecraft.server.level.ServerPlayer sp) || !(level.getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be)
                || be.access().can(sp, be.owner(), com.sqc.nuclearsq.machine.MachineAccess.ADMIN);
    }

    public static void dismantle(ServerLevel level, Placement pl, boolean dropKit) {
        BlockPos ctrl = pl.anchor();
        int fe = storedEnergy(level, ctrl);
        KitMarks.unmark(level, pl);
        KitMarks.remember(level, pl);
        level.setBlock(ctrl, Waterlog.leftover(level.getBlockState(ctrl)), Block.UPDATE_ALL);
        for (MultiblockStructure.Cell c : pl.cells()) {
            BlockState st = level.getBlockState(c.pos());
            if (c.ok(st)) level.setBlock(c.pos(), Waterlog.leftover(st), Block.UPDATE_ALL);
        }
        if (pl.def.shell != null) {
            for (BlockPos p : pl.shellCells()) {
                BlockState st = level.getBlockState(p);
                if (st.is(pl.def.shell)) level.setBlock(p, Waterlog.leftover(st), Block.UPDATE_ALL);
            }
        }
        if (dropKit) {
            var kit = KitRegistry.KITS.get(pl.def.id);
            if (kit != null) {
                ItemStack stack = new ItemStack(kit.get());
                if (fe > 0) stack.set(ModDataComponents.STORED_ENERGY.get(), fe);
                Block.popResource(level, ctrl, stack);
            }
        }
        level.playSound(null, ctrl, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.7f, 1.1f);
    }
}
