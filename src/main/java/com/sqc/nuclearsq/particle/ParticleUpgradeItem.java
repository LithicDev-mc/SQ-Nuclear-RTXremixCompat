package com.sqc.nuclearsq.particle;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ParticleUpgradeItem extends Item {
    public final ParticleClasses.Upgrade kind;

    public ParticleUpgradeItem(ParticleClasses.Upgrade kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Nullable
    public static ParticleClasses.Upgrade kindOf(ItemStack stack) {
        return stack.getItem() instanceof ParticleUpgradeItem u ? u.kind : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.translatable("tooltip.nuclearsq.particle_upgrade." + kind.key).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.nuclearsq.particle_upgrade.max", ParticleClasses.get().max(kind)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
