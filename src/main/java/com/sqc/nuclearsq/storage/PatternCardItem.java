package com.sqc.nuclearsq.storage;

import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class PatternCardItem extends Item {
    public PatternCardItem(Properties props) {
        super(props);
    }

    @Nullable
    public static Pattern pattern(ItemStack stack) {
        return stack.getItem() instanceof PatternCardItem ? stack.get(ModDataComponents.PATTERN.get()) : null;
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof PatternCardItem && !stack.has(ModDataComponents.PATTERN.get());
    }

    public static ItemStack write(ItemStack blank, Pattern p) {
        ItemStack card = blank.copyWithCount(1);
        card.set(ModDataComponents.PATTERN.get(), p);
        card.set(DataComponents.MAX_STACK_SIZE, 1);
        return card;
    }

    @Override
    public Component getName(ItemStack stack) {
        Pattern p = pattern(stack);
        return p == null ? super.getName(stack)
                : Component.translatable("item.nuclearsq.pattern_card_sqc.encoded", p.output().item().getHoverName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return pattern(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        Pattern p = pattern(stack);
        if (p == null) {
            lines.add(Component.translatable("tooltip.nuclearsq.pattern_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("tooltip.nuclearsq.pattern_card.output", p.output().count(), p.output().item().getHoverName())
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("tooltip.nuclearsq.pattern_card.inputs").withStyle(ChatFormatting.GRAY));
        for (Pattern.Part in : p.inputs()) {
            lines.add(Component.translatable("tooltip.nuclearsq.pattern_card.line", in.count(), in.item().getHoverName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        for (Pattern.Part r : p.remainders()) {
            lines.add(Component.translatable("tooltip.nuclearsq.pattern_card.remainder", r.count(), r.item().getHoverName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
