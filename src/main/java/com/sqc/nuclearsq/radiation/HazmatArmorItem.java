package com.sqc.nuclearsq.radiation;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class HazmatArmorItem extends ArmorItem {
    public static final double[] SHIELD = {0.60, 0.85, 0.97};
    public static final double WEAR_FROM = 0.001, WEAR_FULL = 0.05;

    private final int tier;

    public HazmatArmorItem(Holder<ArmorMaterial> material, Type type, int tier, Properties props) {
        super(material, type, props);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public static double weight(Type type) {
        return switch (type) {
            case HELMET -> 0.25;
            case CHESTPLATE -> 0.35;
            case LEGGINGS -> 0.25;
            default -> 0.15;
        };
    }

    public static double shielding(LivingEntity e) {
        double s = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            Item it = e.getItemBySlot(slot).getItem();
            if (it instanceof HazmatArmorItem h) s += weight(h.getType()) * SHIELD[h.tier - 1];
            else if (it instanceof RespiratorItem) s += weight(Type.HELMET) * RespiratorItem.SHIELD;
            else if (slot == EquipmentSlot.HEAD && it instanceof com.sqc.nuclearsq.exo.ExoArmorItem) s += weight(Type.HELMET) * com.sqc.nuclearsq.exo.ExoRadiation.shield(e);
        }
        return Mth.clamp(s, 0, 0.99);
    }

    public static void wear(LivingEntity e, double rate) {
        if (rate < WEAR_FROM) return;
        double chance = Math.min(1.0, rate / WEAR_FULL);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack st = e.getItemBySlot(slot);
            if (st.getItem() instanceof HazmatArmorItem && e.getRandom().nextDouble() < chance) st.hurtAndBreak(1, e, slot);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.nuclearsq.hazmat.tier", "I".repeat(tier))
                .withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("tooltip.nuclearsq.hazmat.piece",
                Math.round(weight(getType()) * SHIELD[tier - 1] * 100), Math.round(SHIELD[tier - 1] * 100)).withStyle(ChatFormatting.GRAY));
    }
}
