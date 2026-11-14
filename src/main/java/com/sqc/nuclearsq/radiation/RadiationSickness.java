package com.sqc.nuclearsq.radiation;

import static com.sqc.nuclearsq.radiation.RadiationValues.SV_DAMAGE;
import static com.sqc.nuclearsq.radiation.RadiationValues.SV_FATAL;
import static com.sqc.nuclearsq.radiation.RadiationValues.SV_FIRST_SIGNS;
import static com.sqc.nuclearsq.radiation.RadiationValues.SV_LETHAL;
import static com.sqc.nuclearsq.radiation.RadiationValues.SV_NAUSEA;
import static com.sqc.nuclearsq.radiation.RadiationValues.SV_SEVERE;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class RadiationSickness {
    private RadiationSickness() {}

    public static final int STAGE_NONE = 0;
    public static final int STAGE_FIRST_SIGNS = 1;
    public static final int STAGE_NAUSEA = 2;
    public static final int STAGE_SEVERE = 3;
    public static final int STAGE_DAMAGE = 4;
    public static final int STAGE_LETHAL = 5;
    public static final int STAGE_FATAL = 6;

    public static final int VOMIT_STOP_TICKS = 40;
    public static final int VOMIT_NAUSEA_TICKS = 100;
    public static final int VOMIT_SLOW_AMP = 6;
    private static final int LINGER_TICKS = 60;
    private static final int FIRST_VOMIT_MIN = 100, FIRST_VOMIT_MAX = 200;
    private static final double DOWN_HYSTERESIS = 1.1;

    private static final DustParticleOptions VOMIT_DUST = new DustParticleOptions(new Vector3f(0.45f, 0.5f, 0.12f), 1.2f);
    private static final DustParticleOptions VOMIT_DUST_DARK = new DustParticleOptions(new Vector3f(0.4f, 0.3f, 0.1f), 1.0f);

    private static final class State {
        int stage;
        long nextVomit = -1;
        long lastSeen;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static long lastPrune = 0;

    public static int stage(double doseSv) {
        if (doseSv >= SV_FATAL) return STAGE_FATAL;
        if (doseSv >= SV_LETHAL) return STAGE_LETHAL;
        if (doseSv >= SV_DAMAGE) return STAGE_DAMAGE;
        if (doseSv >= SV_SEVERE) return STAGE_SEVERE;
        if (doseSv >= SV_NAUSEA) return STAGE_NAUSEA;
        if (doseSv >= SV_FIRST_SIGNS) return STAGE_FIRST_SIGNS;
        return STAGE_NONE;
    }

    public static int vomitIntervalTicks(int stage, RandomSource r) {
        if (stage < STAGE_NAUSEA) return 0;
        if (stage == STAGE_NAUSEA) return 20 * (60 + r.nextInt(61));
        if (stage == STAGE_SEVERE) return 20 * (30 + r.nextInt(31));
        return 20 * (15 + r.nextInt(16));
    }

    public static double damagePerSecond(double dose) {
        int s = stage(dose);
        if (s < STAGE_DAMAGE) return 0;
        if (s == STAGE_DAMAGE) return 0.25 + 0.25 * (dose - SV_DAMAGE) / (SV_LETHAL - SV_DAMAGE);
        if (s == STAGE_LETHAL) return 1.0 + (dose - SV_LETHAL) / (SV_FATAL - SV_LETHAL);
        return 2.0 + 0.5 * (dose - SV_FATAL);
    }

    public static void tick(ServerLevel lv, LivingEntity e, double doseSv, long now) {
        if (now - lastPrune >= 1200) prune(now);
        if (!e.isAlive()) {
            STATES.remove(e.getUUID());
            return;
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            STATES.remove(e.getUUID());
            return;
        }
        int s = stage(doseSv);
        State st = STATES.get(e.getUUID());
        if (s == STAGE_NONE && (st == null || stage(doseSv * DOWN_HYSTERESIS) == STAGE_NONE)) {
            STATES.remove(e.getUUID());
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(e.getUUID(), st);
        }
        st.lastSeen = now;
        RandomSource r = e.getRandom();
        boolean player = e instanceof Player;

        if (s > st.stage) {
            if (e instanceof ServerPlayer sp) announce(sp, s);
            if (player && s >= STAGE_NAUSEA) {
                long first = now + (st.stage < STAGE_NAUSEA
                        ? FIRST_VOMIT_MIN + r.nextInt(FIRST_VOMIT_MAX - FIRST_VOMIT_MIN + 1)
                        : vomitIntervalTicks(s, r) / 2);
                st.nextVomit = st.nextVomit < 0 ? first : Math.min(st.nextVomit, first);
            }
            st.stage = s;
        } else if (s < st.stage) {
            int down = stage(doseSv * DOWN_HYSTERESIS);
            if (down < st.stage) st.stage = down;
        }
        int eff = s;

        if (player) {
            if (eff >= STAGE_NAUSEA) {
                if (st.nextVomit < 0) st.nextVomit = now + vomitIntervalTicks(eff, r);
                if (now >= st.nextVomit) {
                    vomit(lv, e);
                    st.nextVomit = now + vomitIntervalTicks(eff, r);
                }
            } else {
                st.nextVomit = -1;
            }
            if (eff >= STAGE_SEVERE) {
                refresh(e, new MobEffectInstance(MobEffects.WEAKNESS, LINGER_TICKS, eff >= STAGE_LETHAL ? 1 : 0, true, false, true));
                refresh(e, new MobEffectInstance(MobEffects.DIG_SLOWDOWN, LINGER_TICKS, eff >= STAGE_LETHAL ? 1 : 0, true, false, true));
            }
            if (eff >= STAGE_DAMAGE) {
                refresh(e, new MobEffectInstance(MobEffects.HUNGER, LINGER_TICKS, eff >= STAGE_FATAL ? 1 : 0, true, false, true));
            }
        }

        double dmg = damagePerSecond(doseSv);
        if (dmg > 0) {
            float amount;
            if (eff == STAGE_FATAL) {
                amount = (float) dmg;
            } else {
                int whole = (int) Math.floor(dmg);
                amount = whole + (r.nextDouble() < dmg - whole ? 1 : 0);
            }
            if (amount > 0) e.hurt(com.sqc.nuclearsq.registry.ModDamageTypes.radiation(lv), amount);
        }
    }

    private static void refresh(LivingEntity e, MobEffectInstance inst) {
        MobEffectInstance cur = e.getEffect(inst.getEffect());
        if (cur != null && cur.getAmplifier() >= inst.getAmplifier() && cur.getDuration() > 30) return;
        e.addEffect(inst);
    }

    public static boolean vomit(ServerLevel lv, LivingEntity e) {
        if (!e.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, VOMIT_STOP_TICKS, VOMIT_SLOW_AMP, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, VOMIT_NAUSEA_TICKS, 0, false, false, true));
        e.setSprinting(false);
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(0, Math.min(v.y, 0), 0);
        e.hurtMarked = true;

        if (e instanceof Player p) {
            FoodData food = p.getFoodData();
            food.setFoodLevel(Math.max(0, food.getFoodLevel() - 2));
            food.addExhaustion(4.0f);
        }

        Vec3 look = e.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 mouth = e.getEyePosition().add(flat.scale(0.35)).add(0, -0.2, 0);
        RandomSource r = e.getRandom();
        ItemParticleOption flesh = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));
        ItemParticleOption slime = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL));
        for (int i = 0; i < 14; i++) {
            double sp = 0.12 + r.nextDouble() * 0.12;
            double dx = flat.x * sp + (r.nextDouble() - 0.5) * 0.06;
            double dz = flat.z * sp + (r.nextDouble() - 0.5) * 0.06;
            double dy = -0.02 - r.nextDouble() * 0.08;
            lv.sendParticles(i % 3 == 0 ? flesh : slime, mouth.x, mouth.y, mouth.z, 0, dx, dy, dz, 1.0);
        }
        Vec3 mid = mouth.add(flat.scale(0.3)).add(0, -0.3, 0);
        lv.sendParticles(VOMIT_DUST, mid.x, mid.y, mid.z, 18, 0.12, 0.3, 0.12, 0.02);
        lv.sendParticles(VOMIT_DUST_DARK, mid.x, mid.y - 0.3, mid.z, 10, 0.15, 0.25, 0.15, 0.02);
        Vec3 ground = e.position().add(flat.scale(0.7));
        lv.sendParticles(VOMIT_DUST, ground.x, ground.y + 0.05, ground.z, 12, 0.25, 0.0, 0.25, 0.0);

        lv.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0f, 0.5f + r.nextFloat() * 0.1f);
        lv.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.9f, 0.7f + r.nextFloat() * 0.2f);
        lv.playSound(null, ground.x, ground.y, ground.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7f, 0.6f);
        return true;
    }

    private static void announce(ServerPlayer sp, int stage) {
        if (sp instanceof FakePlayer || sp.connection == null) return;
        String key = "message.nuclearsq.sickness." + stage;
        ChatFormatting color = stage >= STAGE_DAMAGE ? ChatFormatting.DARK_RED : stage >= STAGE_NAUSEA ? ChatFormatting.RED : ChatFormatting.YELLOW;
        if (stage == STAGE_FIRST_SIGNS) {
            sp.displayClientMessage(Component.translatable(key + ".sub").withStyle(color), true);
            return;
        }
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, stage >= STAGE_LETHAL ? 70 : 50, 20));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(key + ".title").withStyle(color, ChatFormatting.BOLD)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(key + ".sub").withStyle(ChatFormatting.YELLOW)));
    }

    private static void prune(long now) {
        lastPrune = now;
        for (Iterator<State> it = STATES.values().iterator(); it.hasNext();) {
            if (now - it.next().lastSeen > 1200) it.remove();
        }
    }

    static void clear() {
        STATES.clear();
        lastPrune = 0;
    }
}
