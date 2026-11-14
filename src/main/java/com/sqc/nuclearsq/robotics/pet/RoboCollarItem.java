package com.sqc.nuclearsq.robotics.pet;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;

public class RoboCollarItem extends Item {
    public static final int SNIFFER = 1, TIDY = 2, LAMP = 3;
    public final int kind;

    public RoboCollarItem(Properties props, int kind) {
        super(props);
        this.kind = kind;
    }

    public boolean fits(RoboPetEntity pet) {
        return kind == LAMP || (kind == TIDY) == pet.isCat();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (kind != TIDY || ctx.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, ctx.getClickedPos(), null) == null) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) {
            ctx.getItemInHand().set(RoboPetRegistry.CHEST.get(), ctx.getClickedPos().immutable());
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.nuclearsq.robo_cat.chest_set", ctx.getClickedPos().toShortString()), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BlockPos p = stack.get(RoboPetRegistry.CHEST.get());
        if (kind == TIDY) tip.add((p == null ? Component.translatable("tooltip.nuclearsq.robo_collar.no_chest")
                : Component.translatable("tooltip.nuclearsq.robo_collar.chest", p.toShortString())).withStyle(ChatFormatting.GRAY));
    }
}
