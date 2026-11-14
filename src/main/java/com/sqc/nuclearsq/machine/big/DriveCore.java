package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DriveCore {
    public static final int MAX_POWER = 200;
    public static final float MAX_HEAT = 1000f;
    public static final float HEAT_FULL = 0.7f;
    public static final float PASSIVE_COOLING = 0.20f;
    public static final float PRIMARY_COOLING_PER_MB = 0.001f;
    public static final float DRY_HEAT_PER_MB = 0.0025f;
    public static final float RED_ZONE = 0.8f;
    public static final float MELTDOWN_ABORT = 0.9f, RECOVER = 0.6f;
    public static final int MELTDOWN_TICKS = 250;
    public static final int HEAT_RISE_PCT = 80;
    public static final float PREHEAT_BONUS = 0.15f;
    public static final double LEAK_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.REACTOR_LEAK_SV_H;

    private static final float HEAR_VOLUME = 3.2f;
    private static final double FAR_PARTICLE_RANGE = 256.0;

    private float heat, coolingAcc, lastCooling = PASSIVE_COOLING, insertion;
    private int meltdownTicks = MELTDOWN_TICKS;
    private float riseScale = HEAT_RISE_PCT / 100f;
    private double radiation;
    private boolean overheated;
    private int meltdown;
    private final CoreChemistry chem = new CoreChemistry();

    public CoreChemistry chem() { return chem; }

    void configure(int meltdownTicks, int risePct) {
        this.meltdownTicks = meltdownTicks > 0 ? meltdownTicks : MELTDOWN_TICKS;
        this.riseScale = (risePct > 0 ? risePct : HEAT_RISE_PCT) / 100f;
    }

    public void addCooling(float amount) {
        coolingAcc += amount;
    }

    boolean tick(ServerLevel sl, Vec3 core, int tick, float insertion, int flowMb, int missingMb) {
        return tick(sl, core, tick, insertion, flowMb, missingMb, 1f, 0f);
    }

    boolean tick(ServerLevel sl, Vec3 core, int tick, float insertion, int flowMb, int missingMb, float heatFactor, float coolerAdd) {
        this.insertion = Mth.clamp(insertion, 0f, 1f);
        float gen = this.insertion * HEAT_FULL * heatFactor + missingMb * DRY_HEAT_PER_MB;
        lastCooling = PASSIVE_COOLING + (flowMb * PRIMARY_COOLING_PER_MB + coolingAcc + coolerAdd) * chem.crudCoolingFactor();
        coolingAcc = 0f;
        float net = gen - lastCooling;
        if (net > 0f) net *= riseScale;
        heat = Mth.clamp(heat + net, 0f, MAX_HEAT);
        if (heat >= MAX_HEAT) overheated = true;
        else if (heat <= MAX_HEAT * RECOVER) overheated = false;
        effects(sl, core, tick);
        radiate(sl, core, tick);
        if (heat >= MAX_HEAT) {
            meltdown++;
            if (meltdown == 1) announce(sl, core, "message.nuclearsq.meltdown_server", meltdownTicks / 20);
            if (meltdown % 20 == 1) countdown(sl, core);
            if (meltdown >= meltdownTicks) {
                announce(sl, core, "message.nuclearsq.explosion_server", 0);
                meltdown = 0;
                heat = 0f;
                overheated = false;
                return true;
            }
        } else if (heat <= MAX_HEAT * MELTDOWN_ABORT) {
            meltdown = 0;
        }
        return false;
    }

    void idle() {
        chem.idle();
        coolingAcc = 0f;
        insertion = 0f;
        radiation = 0;
        lastCooling = PASSIVE_COOLING;
        heat = Math.max(0f, heat - PASSIVE_COOLING);
        if (heat <= MAX_HEAT * RECOVER) overheated = false;
        meltdown = 0;
    }

    private static void announce(ServerLevel sl, Vec3 c, String key, int sec) {
        if (com.sqc.nuclearsq.NuclearConfig.ANNOUNCE_MELTDOWN.get()) sl.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(key, (int) c.x, (int) c.y, (int) c.z, sec).withStyle(ChatFormatting.RED), false);
    }

    private void countdown(ServerLevel sl, Vec3 c) {
        int sec = (meltdownTicks - meltdown + 19) / 20;
        sl.playSound(null, BlockPos.containing(c), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, HEAR_VOLUME,
                0.5f + 0.5f * meltdown / (float) meltdownTicks);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(c) < 48 * 48) {
                p.displayClientMessage(Component.translatable("message.nuclearsq.reactor_meltdown", sec)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
    }

    private void radiate(ServerLevel sl, Vec3 c, int tick) {
        boolean red = hot();
        radiation = com.sqc.nuclearsq.radiation.RadiationValues.REACTOR_POWER_SV_H * insertion + (red ? LEAK_SV_H : 0);
        if (tick % 20 != 0 || radiation <= 0) return;
        if (red) com.sqc.nuclearsq.radiation.RadiationManager.leak(sl, BlockPos.containing(c), radiation);
        else com.sqc.nuclearsq.radiation.RadiationManager.emit(sl, BlockPos.containing(c), radiation);
    }

    private void effects(ServerLevel sl, Vec3 c, int tick) {
        if (heat < MAX_HEAT * RED_ZONE) return;
        if (tick % 40 == 0) sl.playSound(null, BlockPos.containing(c), ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, HEAR_VOLUME, 1f);
        if (tick % 2 == 0) {
            double a = sl.random.nextDouble() * Math.PI * 2;
            far(sl, ParticleTypes.CLOUD, c.x + Math.cos(a) * 3.2, c.y + sl.random.nextDouble() * 2, c.z + Math.sin(a) * 3.2,
                    Math.cos(a) * 0.18, 0.03, Math.sin(a) * 0.18);
            if (overheated) far(sl, ParticleTypes.LARGE_SMOKE, c.x, c.y + 4, c.z, 0, 0.2, 0);
        }
        if (tick % 25 == 0) sl.playSound(null, BlockPos.containing(c), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, HEAR_VOLUME, 0.6f);
    }

    static void far(ServerLevel sl, ParticleOptions p, double x, double y, double z, double dx, double dy, double dz) {
        for (ServerPlayer pl : sl.players()) {
            if (pl.distanceToSqr(x, y, z) <= FAR_PARTICLE_RANGE * FAR_PARTICLE_RANGE) sl.sendParticles(pl, p, true, x, y, z, 0, dx, dy, dz, 1.0);
        }
    }

    public int power() {
        return overheated ? 0 : Math.round(MAX_POWER * insertion);
    }

    public float heat() { return heat; }

    public double radiationSvH() { return radiation; }

    public int heatPercent() { return Math.round(heat * 100f / MAX_HEAT); }

    public boolean overheated() { return overheated; }

    public boolean hot() { return heat >= MAX_HEAT * RED_ZONE; }

    public int meltdownLeft() { return meltdown > 0 ? meltdownTicks - meltdown : 0; }

    public float cooling() { return lastCooling; }

    public void setHeat(float value) {
        heat = Mth.clamp(value, 0f, MAX_HEAT);
        if (heat >= MAX_HEAT) overheated = true;
    }

    void save(CompoundTag tag) {
        tag.putFloat("heat", heat);
        tag.putBoolean("overheated", overheated);
        tag.putInt("meltdown", meltdown);
        chem.save(tag);
    }

    void load(CompoundTag tag) {
        heat = Mth.clamp(tag.getFloat("heat"), 0f, MAX_HEAT);
        overheated = tag.getBoolean("overheated");
        meltdown = tag.getInt("meltdown");
        chem.load(tag);
    }
}
