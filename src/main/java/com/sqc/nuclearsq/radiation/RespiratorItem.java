package com.sqc.nuclearsq.radiation;

import java.util.List;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RespiratorItem extends ArmorItem {
    public static final int FILTER_SECONDS = 600;
    public static final double DUST_BLOCK = 0.6;
    public static final double AIR_BLOCK = 0.95;
    public static final double SHIELD = 0.5;
    public static final double DIRTY_AIR_SV_H = 1e-4;
    public static final double DIRTY_FULL_SV_H = 0.05;
    public static final int MAX_WEAR_FACTOR = 5;
    public static final int LEAK_WEAR = 2;
    public static final int WARN_SECONDS = 60;

    public RespiratorItem(Holder<ArmorMaterial> material, Properties props) {
        super(material, Type.HELMET, props);
    }

    public static int filterLeft(ItemStack mask) {
        return mask.getOrDefault(ModDataComponents.FILTER_LEFT.get(), 0);
    }

    private static void setFilterLeft(ItemStack mask, int seconds) {
        mask.set(ModDataComponents.FILTER_LEFT.get(), Math.max(0, Math.min(FILTER_SECONDS, seconds)));
    }

    public static ItemStack worn(LivingEntity e) {
        ItemStack head = e.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof RespiratorItem ? head : ItemStack.EMPTY;
    }

    public static boolean active(LivingEntity e) {
        ItemStack m = worn(e);
        return !m.isEmpty() && filterLeft(m) > 0;
    }

    public static boolean comboActive(LivingEntity e) {
        ItemStack m = worn(e);
        return !m.isEmpty() && filterLeft(m) > 0 && m.has(ModDataComponents.FILTER_COMBO.get());
    }

    public static double dustFactor(LivingEntity e) {
        return active(e) ? 1.0 - DUST_BLOCK : 1.0 - com.sqc.nuclearsq.exo.ExoRadiation.dust(e);
    }

    public static double airFactor(LivingEntity e) {
        return active(e) ? 1.0 - AIR_BLOCK : 1.0 - com.sqc.nuclearsq.exo.ExoRadiation.air(e);
    }

    public static void tick(ServerLevel lv, Player p, double outsideSvH) {
        ItemStack mask = worn(p);
        if (mask.isEmpty() || filterLeft(mask) <= 0) return;
        if (!Contamination.dirtyAir(lv, p) && outsideSvH < DIRTY_AIR_SV_H) return;
        double f = 1.0 + (MAX_WEAR_FACTOR - 1) * Math.min(1.0, outsideSvH / DIRTY_FULL_SV_H);
        wear(p, mask, (int) Math.round(f));
    }

    public static void leakWear(Player p) {
        ItemStack mask = worn(p);
        if (!mask.isEmpty() && filterLeft(mask) > 0) wear(p, mask, LEAK_WEAR);
    }

    private static void wear(Player p, ItemStack mask, int seconds) {
        int before = filterLeft(mask), after = Math.max(0, before - seconds);
        setFilterLeft(mask, after);
        if (!(p instanceof ServerPlayer sp) || sp instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        if (after == 0) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.filter_empty"), true);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
        } else if (before > WARN_SECONDS && after <= WARN_SECONDS) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.filter_low", after), true);
        }
    }

    public static boolean insert(Player p, ItemStack mask, ItemStack filter) {
        if (filterLeft(mask) > 0) {
            message(p, "message.nuclearsq.filter_still_good");
            return false;
        }
        setFilterLeft(mask, FILTER_SECONDS);
        if (filter.getItem() instanceof RespiratorFilterItem f && f.combo()) mask.set(ModDataComponents.FILTER_COMBO.get(), net.minecraft.util.Unit.INSTANCE);
        else mask.remove(ModDataComponents.FILTER_COMBO.get());
        if (!p.getAbilities().instabuild) filter.shrink(1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1.0f);
        message(p, "message.nuclearsq.filter_inserted");
        return true;
    }

    static void message(Player p, String key) {
        if (p instanceof ServerPlayer sp && !(sp instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            sp.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!(other.getItem() instanceof RespiratorFilterItem) || !slot.allowModification(player)) return false;
        if (filterLeft(stack) > 0) return false;
        if (!player.level().isClientSide()) insert(player, stack, other);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return filterLeft(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * filterLeft(stack) / FILTER_SECONDS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return filterLeft(stack) <= WARN_SECONDS ? 0xE03C2C : 0x4AA3E0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int left = filterLeft(stack);
        if (left > 0) {
            tip.add(Component.translatable(stack.has(ModDataComponents.FILTER_COMBO.get()) ? "tooltip.nuclearsq.respirator.filter_iodine" : "tooltip.nuclearsq.respirator.filter", time(left)).withStyle(left <= WARN_SECONDS ? ChatFormatting.RED : ChatFormatting.AQUA));
        } else {
            tip.add(Component.translatable("tooltip.nuclearsq.respirator.empty").withStyle(ChatFormatting.RED));
        }
        tip.add(Component.translatable("tooltip.nuclearsq.respirator.hint").withStyle(ChatFormatting.GRAY));
    }

    static String time(int seconds) {
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }
}
