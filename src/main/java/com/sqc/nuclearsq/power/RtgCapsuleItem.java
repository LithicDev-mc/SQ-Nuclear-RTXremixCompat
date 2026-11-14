package com.sqc.nuclearsq.power;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RtgCapsuleItem extends Item {
    private final int peak, stepTicks;

    public RtgCapsuleItem(Properties props) {
        this(props, RtgBlockEntity.PEAK, RtgBlockEntity.STEP_TICKS);
    }

    public RtgCapsuleItem(Properties props, int peak, int stepTicks) {
        super(props.durability(RtgBlockEntity.CAPSULE_STEPS));
        this.peak = peak;
        this.stepTicks = stepTicks;
    }

    public int peak() { return peak; }

    public int stepTicks() { return stepTicks; }

    public int rateFor(int damage) {
        if (damage >= RtgBlockEntity.CAPSULE_STEPS) return 0;
        return (int) Math.round(peak * Math.pow(0.5, damage / (double) RtgBlockEntity.HALF_LIFE_STEPS));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int d = stack.getDamageValue();
        int left = Math.round(100f * (RtgBlockEntity.CAPSULE_STEPS - Math.min(RtgBlockEntity.CAPSULE_STEPS, d)) / RtgBlockEntity.CAPSULE_STEPS);
        tooltip.add(Component.translatable(getDescriptionId() + ".tip", rateFor(d), left).withStyle(ChatFormatting.GRAY));
    }
}
