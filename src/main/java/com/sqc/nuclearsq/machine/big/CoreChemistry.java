package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.reactor.RecombinerBlockEntity;
import com.sqc.nuclearsq.reactor.XenonFilterBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CoreChemistry {
    public static final float GAS_RISE = 0.02f, RECOMBINER_RATE = 0.012f, GAS_WARN = 70f, GAS_ALARM = 90f;
    public static final float GAS_AFTER_BLAST = 40f;
    public static final float XE_RISE = 0.012f, XE_DECAY = 0.0001f, FILTER_RATE = 0.010f;
    public static final float XE_STOP = 100f, XE_RESUME = 60f, XE_WARN = 80f;
    public static final float XE_FREE = 30f;
    public static final int XE_LOSS_PCT = 40;
    public static final float MOX_POWER = 1.3f, MOX_HEAT = 1.15f, MOX_XENON = 1.5f;
    public static final int FILTER_TICKS = 24000, RECOMBINER_RANGE = 24, HULL_HEAL_TICKS = 6000;
    public static final float BLAST_HULL = 3f, BLAST_ROOM = 22f;
    public static final int BORON_PER_ITEM = 100, BORON_MAX = 1000, BORON_LOSS_PCT = 50;
    public static final float CRUD_RISE = 0.0013f, CRUD_WARN = 60f, CRUD_ALARM = 85f;
    public static final int CRUD_LOSS_PCT = 15;
    public static final float EXCHANGE_CRUD = 35f;
    public static final int EXCHANGE_BORON = 200;
    public static final double HULL_LEAK_SV_H = 4.0;

    private float gas, xenon, crud;
    private int boron;
    private int hullDamage, hullHeal;
    private boolean poisoned;
    private int recombiners, filters;

    public float gas() { return gas; }
    public float xenon() { return xenon; }
    public int gasPercent() { return Math.round(gas); }
    public int xenonPercent() { return Math.round(xenon); }
    public boolean poisoned() { return poisoned; }
    public int boron() { return boron; }
    public float crud() { return crud; }
    public int crudPercent() { return Math.round(crud); }
    public boolean hullDamaged() { return hullDamage > 0; }
    public int filters() { return filters; }
    public int recombiners() { return recombiners; }

    public float powerFactor() {
        return poisoned ? 0f : (1f - XE_LOSS_PCT / 100f * Mth.clamp((xenon - XE_FREE) / (XE_STOP - XE_FREE), 0f, 1f)) * boronFactor();
    }

    public float boronFactor() {
        return 1f - BORON_LOSS_PCT / 100f * boron / BORON_MAX;
    }

    public float crudCoolingFactor() {
        return 1f - CRUD_LOSS_PCT / 100f * Mth.clamp(crud / 100f, 0f, 1f);
    }

    public boolean dose() {
        if (boron >= BORON_MAX) return false;
        boron = Math.min(BORON_MAX, boron + BORON_PER_ITEM);
        return true;
    }

    public void exchange() {
        crud = Math.max(0f, crud - EXCHANGE_CRUD);
        boron = Math.max(0, boron - EXCHANGE_BORON);
    }

    public void set(float gas, float xenon) {
        this.gas = Mth.clamp(gas, 0f, 100f);
        this.xenon = Mth.clamp(xenon, 0f, 100f);
        if (this.xenon >= XE_STOP) poisoned = true;
        else if (this.xenon <= XE_RESUME) poisoned = false;
    }

    public void setWater(int boron, float crud) {
        this.boron = Mth.clamp(boron, 0, BORON_MAX);
        this.crud = Mth.clamp(crud, 0f, 100f);
    }

    public boolean tick(ServerLevel sl, Vec3 core, int tick, float insertion, float xenonFactor) {
        insertion = Mth.clamp(insertion, 0f, 1f);
        if (tick % 20 == 0) {
            recombiners = RecombinerBlockEntity.count(sl, BlockPos.containing(core), RECOMBINER_RANGE);
            filters = XenonFilterBlockEntity.work(sl, BlockPos.containing(core), RECOMBINER_RANGE, 20, insertion > 0f || xenon > 1f);
        }
        gas = Mth.clamp(gas + insertion * GAS_RISE - recombiners * RECOMBINER_RATE, 0f, 100f);
        float filter = filters * FILTER_RATE;
        float xeLoss = 1f - XE_LOSS_PCT / 100f * Mth.clamp((xenon - XE_FREE) / (XE_STOP - XE_FREE), 0f, 1f);
        xenon = Mth.clamp(xenon + Math.min(1f, insertion / xeLoss) * XE_RISE * xenonFactor - xenon * XE_DECAY - (xenon > 0f ? filter : 0f), 0f, 100f);
        boolean was = poisoned;
        if (xenon >= XE_STOP) poisoned = true;
        else if (xenon <= XE_RESUME) poisoned = false;
        if (poisoned != was) say(sl, core, poisoned ? "message.nuclearsq.xenon_stop" : "message.nuclearsq.xenon_resume", ChatFormatting.YELLOW);
        else if (tick % 600 == 0 && xenon >= XE_WARN && !poisoned) say(sl, core, "message.nuclearsq.xenon_warn", ChatFormatting.YELLOW, Math.round(xenon));
        float before = crud;
        crud = Mth.clamp(crud + insertion * CRUD_RISE, 0f, 100f);
        if (before < CRUD_WARN && crud >= CRUD_WARN || before < CRUD_ALARM && crud >= CRUD_ALARM) {
            say(sl, core, "message.nuclearsq.crud_warn", ChatFormatting.GOLD, Math.round(crud));
        }
        if (hullDamage > 0 && ++hullHeal >= HULL_HEAL_TICKS) {
            hullDamage = 0;
            hullHeal = 0;
        }
        if (hullDamage > 0 && tick % 20 == 0) RadiationManager.leak(sl, BlockPos.containing(core), HULL_LEAK_SV_H);
        if (gas >= GAS_ALARM && tick % 100 == 0) say(sl, core, "message.nuclearsq.gas_alarm", ChatFormatting.RED, Math.round(gas));
        else if (gas >= GAS_WARN && gas < GAS_ALARM && tick % 400 == 0) say(sl, core, "message.nuclearsq.gas_warn", ChatFormatting.GOLD, Math.round(gas));
        if (gas < 100f) return false;
        return ignite(sl, core);
    }

    private boolean ignite(ServerLevel sl, Vec3 core) {
        hullHeal = 0;
        if (hullDamage == 0) {
            hullDamage = 1;
            gas = GAS_AFTER_BLAST;
            say(sl, core, "message.nuclearsq.gas_hull", ChatFormatting.RED);
            sl.explode(null, core.x, core.y, core.z, BLAST_HULL, Level.ExplosionInteraction.BLOCK);
            return false;
        }
        gas = 0f;
        hullDamage = 0;
        say(sl, core, "message.nuclearsq.gas_room", ChatFormatting.DARK_RED);
        sl.explode(null, core.x, core.y, core.z, BLAST_ROOM, Level.ExplosionInteraction.BLOCK);
        return true;
    }

    private static void say(ServerLevel sl, Vec3 c, String key, ChatFormatting color, Object... args) {
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(c) < 48 * 48) p.displayClientMessage(Component.translatable(key, args).withStyle(color, ChatFormatting.BOLD), true);
        }
    }

    void idle() {
        gas = Math.max(0f, gas - 0.02f);
        xenon = Math.max(0f, xenon - xenon * XE_DECAY);
        if (xenon <= XE_RESUME) poisoned = false;
    }

    void save(CompoundTag tag) {
        tag.putFloat("gas", gas);
        tag.putFloat("xenon", xenon);
        tag.putInt("hull_damage", hullDamage);
        tag.putInt("hull_heal", hullHeal);
        tag.putBoolean("poisoned", poisoned);
        tag.putInt("boron", boron);
        tag.putFloat("crud", crud);
    }

    void load(CompoundTag tag) {
        gas = Mth.clamp(tag.getFloat("gas"), 0f, 100f);
        xenon = Mth.clamp(tag.getFloat("xenon"), 0f, 100f);
        hullDamage = tag.getInt("hull_damage");
        hullHeal = tag.getInt("hull_heal");
        poisoned = tag.getBoolean("poisoned");
        boron = Mth.clamp(tag.getInt("boron"), 0, BORON_MAX);
        crud = Mth.clamp(tag.getFloat("crud"), 0f, 100f);
    }
}
