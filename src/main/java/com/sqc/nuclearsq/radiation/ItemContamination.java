package com.sqc.nuclearsq.radiation;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ItemContamination {
    private ItemContamination() {}

    public static final float CLEAN = 1e-6f;
    public static final float MAX = 0.01f;
    public static final double HAND_FROM_GROUND = 0.02;
    public static final double HAND_FROM_BODY = 0.01;
    public static final double PICKUP_FROM_GROUND = 0.2;
    public static final double DROP_PER_SV = 0.0005;
    public static final double EAT_FACTOR = 10.0;

    public static float get(ItemStack s) {
        if (s.isEmpty()) return 0f;
        return s.getOrDefault(ModDataComponents.CONTAMINATION.get(), 0f);
    }

    public static void set(ItemStack s, float svH) {
        if (s.isEmpty()) return;
        if (!(svH >= CLEAN)) s.remove(ModDataComponents.CONTAMINATION.get());
        else s.set(ModDataComponents.CONTAMINATION.get(), quantize(Math.min(MAX, svH)));
    }

    public static float quantize(float svH) {
        double step = Math.round(Math.log10(svH) * 8.0) / 8.0;
        return (float) Math.min(MAX, Math.pow(10.0, step));
    }

    public static void add(ItemStack s, float svH) {
        if (svH <= 0) return;
        set(s, get(s) + svH);
    }

    public static void atLeast(ItemStack s, float svH) {
        if (svH > get(s)) set(s, svH);
    }

    public static double carried(Player p) {
        double sv = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) sv += get(inv.getItem(i));
        return sv;
    }

    public static void washInventory(Player p, double keep) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            float c = get(s);
            if (c > 0) set(s, (float) (c * keep));
        }
    }

    public static void tickPlayer(ServerLevel lv, Player p) {
        if (p.isSpectator()) return;
        double amount = Contamination.underFoot(lv, p) * HAND_FROM_GROUND + Contamination.body(p) * HAND_FROM_BODY;
        if (amount < CLEAN) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(hand);
            if (!s.isEmpty()) add(s, (float) amount);
        }
    }

    public static float contaminateFromGround(ServerLevel lv, net.minecraft.world.entity.item.ItemEntity ie) {
        float ground = groundAt(lv, ie.blockPosition());
        if (ground > 0) atLeast(ie.getItem(), (float) (ground * PICKUP_FROM_GROUND));
        return get(ie.getItem());
    }

    public static double dropContamination(ServerLevel lv, net.minecraft.world.entity.LivingEntity e) {
        double dose = RadiationManager.doseOf(e);
        double svH = dose >= com.sqc.nuclearsq.radiation.RadiationValues.SV_SEVERE ? dose * DROP_PER_SV : 0;
        svH = Math.max(svH, Contamination.underFoot(lv, e) * PICKUP_FROM_GROUND);
        return svH < CLEAN ? 0 : Math.min(MAX, svH);
    }

    static float groundAt(ServerLevel lv, BlockPos pos) {
        var z = Contamination.zones(lv);
        if (z.size() == 0) return 0f;
        return Math.max(z.get(pos), z.get(pos.below()));
    }

    public static double eatDose(ItemStack s) {
        if (s.isEmpty() || !s.has(DataComponents.FOOD)) return 0;
        return get(s) * EAT_FACTOR;
    }
}
