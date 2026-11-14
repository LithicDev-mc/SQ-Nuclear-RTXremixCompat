package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RodShaft {
    public static final int FULL_HEAD_Y = 5, MAX_HEAD_Y = 12;
    private static final int LONG_TRAVEL = FuelRodHeadBlock.ROD_BLOCKS;
    private static final float SPEED = 1f / (3.2f * 20f);
    private static final int HOLD_TICKS = 25;
    private static final int SETTLE_TICKS = 15;
    public static final float CLAMP_SPEED = 1f / 10f;

    public final int cellX, cellZ;
    private int headY = -1, drivenHeadY = -1, hold, settle;
    @Nullable
    private BlockPos drivenBase;
    private float shown, prevShown;
    private float open = 1f, prevOpen = 1f;
    private boolean locked;
    private boolean clientDriven;

    public RodShaft(int cellX, int cellZ) {
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public int headY() { return headY; }
    public boolean isDriven(boolean client) { return client ? clientDriven : drivenHeadY >= 0; }
    public float shown(float partialTick) { return Mth.lerp(partialTick, prevShown, shown); }
    public float shown() { return shown; }
    public float open(float partialTick) { return Mth.lerp(partialTick, prevOpen, open); }
    public float open() { return open; }
    public boolean locked() { return locked; }

    boolean setLocked(boolean value) {
        boolean v = value && headY >= 0;
        if (v == locked) return false;
        locked = v;
        return true;
    }

    void scan(Level level, BlockPos base, boolean formed, int maxHead) {
        headY = -1;
        if (formed) {
            for (int y = FULL_HEAD_Y; y <= maxHead; y++) {
                BlockState st = level.getBlockState(base.above(y));
                if (st.getBlock() instanceof FuelRodHeadBlock && st.getValue(ReactorProps.ROD_FORMED)) {
                    headY = y;
                    break;
                }
            }
        }
        if (headY < 0 && formed) {
            shown = 0f;
            locked = false;
        }
    }

    private float target(int rod) {
        return headY < 0 || !locked ? 0f : Math.max(0, headY - FULL_HEAD_Y) * (float) rod / BigMachineBlockEntity.ROD_MAX;
    }

    private boolean wantOpen(float t) {
        return headY < 0 || !locked || Math.abs(t - shown) > 0.0001f || settle > 0;
    }

    private boolean step(int rod) {
        float t = target(rod), old = shown;
        open = wantOpen(t) ? Math.min(1f, open + CLAMP_SPEED) : Math.max(0f, open - CLAMP_SPEED);
        if (open >= 1f) {
            int travel = headY - FULL_HEAD_Y;
            float speed = travel > LONG_TRAVEL ? SPEED * travel / FuelRodHeadBlock.ROD_BLOCKS : SPEED;
            if (t > shown) shown = Math.min(t, shown + speed);
            else if (shown > t) shown = Math.max(t, shown - speed);
        }
        if (shown != old) settle = SETTLE_TICKS;
        else if (settle > 0) settle--;
        return shown != old;
    }

    public boolean busy(int rod) {
        return headY >= 0 && (Math.abs(target(rod) - shown) > 0.0001f || settle > 0 || open > 0f && open < 1f);
    }

    boolean update(Level level, BlockPos base, boolean formed, int rod) {
        float wasOpen = open;
        boolean stepped = step(rod);
        boolean clampDone = open != wasOpen && (open == 0f || open == 1f);
        boolean moving = shown > 0.001f;
        if (moving) hold = HOLD_TICKS;
        else if (hold > 0) hold--;
        boolean changed = syncDriven(level, base, formed && headY > FULL_HEAD_Y && (moving || hold > 0));
        return changed || clampDone || stepped && shown == target(rod);
    }

    void clientTick(int rod) {
        prevShown = shown;
        prevOpen = open;
        step(rod);
    }

    boolean syncDriven(Level level, BlockPos base, boolean want) {
        boolean changed = false;
        if (drivenHeadY >= 0 && (!want || drivenHeadY != headY || !base.equals(drivenBase))) {
            if (drivenBase != null) applyDriven(level, drivenBase, drivenHeadY, false);
            drivenHeadY = -1;
            drivenBase = null;
            changed = true;
        }
        if (want && drivenHeadY < 0 && headY >= 0) {
            applyDriven(level, base, headY, true);
            drivenHeadY = headY;
            drivenBase = base;
            changed = true;
        }
        return changed;
    }

    private static void applyDriven(Level level, BlockPos base, int y, boolean value) {
        for (int i = 0; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            BlockPos p = base.above(y - i);
            BlockState st = level.getBlockState(p);
            if (st.hasProperty(ReactorProps.DRIVEN) && st.getValue(ReactorProps.DRIVEN) != value) {
                level.setBlock(p, st.setValue(ReactorProps.DRIVEN, value), 3);
            }
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("head", headY);
        t.putInt("drivenHead", drivenHeadY);
        if (drivenBase != null) t.putLong("drivenBase", drivenBase.asLong());
        t.putFloat("shown", shown);
        t.putFloat("open", open);
        t.putBoolean("locked", locked);
        return t;
    }

    void load(CompoundTag t) {
        headY = t.contains("head") ? t.getInt("head") : -1;
        drivenHeadY = t.contains("drivenHead") ? t.getInt("drivenHead") : -1;
        drivenBase = t.contains("drivenBase") ? BlockPos.of(t.getLong("drivenBase")) : null;
        shown = prevShown = t.getFloat("shown");
        open = prevOpen = t.contains("open") ? t.getFloat("open") : 1f;
        locked = t.getBoolean("locked");
    }

    CompoundTag syncTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("head", headY);
        t.putBoolean("driven", drivenHeadY >= 0);
        t.putFloat("shown", shown);
        t.putFloat("open", open);
        t.putBoolean("locked", locked);
        return t;
    }

    void readSync(CompoundTag t) {
        headY = t.contains("head") ? t.getInt("head") : -1;
        clientDriven = t.getBoolean("driven");
        shown = prevShown = t.getFloat("shown");
        open = prevOpen = t.getFloat("open");
        locked = t.getBoolean("locked");
    }
}
