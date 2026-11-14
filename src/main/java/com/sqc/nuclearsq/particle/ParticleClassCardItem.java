package com.sqc.nuclearsq.particle;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ParticleClassCardItem extends Item {
    public final int tier;

    public ParticleClassCardItem(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof ParticleClassCardItem c ? c.tier : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.translatable("tooltip.nuclearsq.particle_card." + ParticleClasses.CARDS.get(tier - 1)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.nuclearsq.particle_card.slot").withStyle(ChatFormatting.DARK_GRAY));
    }
}
