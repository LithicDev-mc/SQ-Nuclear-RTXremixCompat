package com.sqc.nuclearsq.robotics;

import java.util.Arrays;

import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

public class RodGripperBlockEntity extends BlockEntity implements MenuProvider {
    public enum Input {
        GRIP, LIFT, LOWER, PULL;

        public Direction side(Direction facing) {
            return switch (this) {
                case GRIP -> facing.getOpposite();
                case LIFT -> facing.getClockWise();
                case LOWER -> facing.getCounterClockWise();
                case PULL -> Direction.UP;
            };
        }
    }

    public static final int MAX_DEPTH = 24, STEP_TICKS = 5, COST = 300, BUFFER = 30000;
    public static final String[] PROBLEMS = {"", "paused", "power", "no_rod", "blocked", "idle", "clamped"};
    public static final int DATA_SIZE = 7;

    private static final class GripperEnergy extends EnergyStorage {
        GripperEnergy() {
            super(BUFFER, 2000, 0);
        }

        void consume(int fe) {
            energy = Math.max(0, energy - fe);
        }

        void set(int fe) {
            energy = Math.max(0, Math.min(capacity, fe));
        }
    }

    private final GripperEnergy energy = new GripperEnergy();
    private final RodHook hook = new RodHook();
    private boolean enabled = true;
    private int inputs, cooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored();
                case 1 -> enabled ? 1 : 0;
                case 2 -> hook.depth;
                case 3 -> hook.holding ? 1 : 0;
                case 4 -> Math.max(0, Arrays.asList(PROBLEMS).indexOf(hook.problem));
                case 5 -> inputs;
                case 6 -> hook.rodHere ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public RodGripperBlockEntity(BlockPos pos, BlockState state) {
        super(RoboticsRegistry.GRIPPER_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RodGripperMenu(id, inv, this, data);
    }

    public EnergyStorage energy() {
        return energy;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setChanged();
    }

    public int depth() {
        return hook.depth;
    }

    public boolean holding() {
        return hook.holding;
    }

    public int inputs() {
        return inputs;
    }

    public String problem() {
        return hook.problem;
    }

    public BlockPos hook() {
        return hook.at(worldPosition);
    }

    public static boolean rodAt(Level level, BlockPos head) {
        if (!formed(level.getBlockState(head), true)) return false;
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) if (!formed(level.getBlockState(head.below(i)), false)) return false;
        return true;
    }

    private static boolean formed(BlockState s, boolean head) {
        return (head ? s.getBlock() instanceof FuelRodHeadBlock : s.getBlock() instanceof FuelRodBlock)
                && s.getValue(ReactorProps.ROD_FORMED) && !s.getValue(ReactorProps.DRIVEN);
    }

    public static int signals(ServerLevel sl, BlockPos pos, Direction facing) {
        int in = 0;
        for (Input i : Input.values()) {
            Direction s = i.side(facing);
            if (sl.getSignal(pos.relative(s), s) > 0) in |= 1 << i.ordinal();
        }
        return in;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        inputs = signals(sl, worldPosition, getBlockState().getValue(RodGripperBlock.FACING));
        hook.scan(sl, worldPosition);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!enabled) {
            hook.problem = "paused";
            return;
        }
        if (hook.act(sl, worldPosition, inputs, energy.getEnergyStored() >= COST)) {
            energy.consume(COST);
            cooldown = STEP_TICKS;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("holding", hook.holding);
        tag.putInt("depth", hook.depth);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        energy.set(tag.getInt("energy"));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        hook.holding = tag.getBoolean("holding");
        hook.depth = Math.max(0, Math.min(MAX_DEPTH, tag.getInt("depth")));
    }
}
