package com.sqc.nuclearsq.transport;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class OverheadWireItem extends Item {
    public OverheadWireItem(Properties props) {
        super(props);
    }

    @Nullable
    private static BlockPos pending(ItemStack stack, Level level) {
        GlobalPos from = stack.get(ModDataComponents.GRID_FROM.get());
        return from == null || !from.dimension().equals(level.dimension()) ? null : from.pos();
    }

    private static void setPending(ItemStack stack, @Nullable BlockPos pos, Level level) {
        if (pos == null) stack.remove(ModDataComponents.GRID_FROM.get());
        else stack.set(ModDataComponents.GRID_FROM.get(), GlobalPos.of(level.dimension(), pos.immutable()));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        GridNode node = GridNode.at(level, ctx.getClickedPos());
        if (node == null || p == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos here = node.nodePos();
        if (p.isShiftKeyDown()) {
            int back = GridNode.unlinkAll(level, node);
            if (back > 0 && !p.isCreative()) {
                ItemStack give = new ItemStack(this, back);
                if (!p.getInventory().add(give)) p.drop(give, false);
            }
            setPending(stack, null, level);
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.unlinked", back), true);
            return InteractionResult.CONSUME;
        }
        BlockPos from = pending(stack, level);
        GridNode start = from == null || !level.isLoaded(from) && from.distSqr(here) > (double) LargePowerMastBlock.SPAN * LargePowerMastBlock.SPAN
                ? null : GridNode.node(level, from);
        if (start == null || start == node) {
            setPending(stack, here, level);
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.start"), true);
            return InteractionResult.CONSUME;
        }
        if (start.links().contains(here)) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.already").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        int span = GridNode.span(start, node);
        if (from.distSqr(here) > (double) span * span) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.too_far", span)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        int cost = GridNode.wireCost(from, here);
        if (!p.isCreative() && stack.getCount() < cost) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.need", cost).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        GridNode.link(start, node);
        int len = (int) Math.round(Math.sqrt(from.distSqr(here)));
        setPending(stack, here, level);
        if (!p.isCreative()) stack.shrink(cost);
        p.displayClientMessage(Component.translatable("message.nuclearsq.grid.linked", len, cost), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (!p.isShiftKeyDown() || pending(stack, level) == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            setPending(stack, null, level);
            p.displayClientMessage(Component.translatable("message.nuclearsq.grid.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.GRID_FROM.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.nuclearsq.overhead_wire_sqc.tip", GridNode.BLOCKS_PER_WIRE,
                GridNode.MAX_SPAN).withStyle(ChatFormatting.GRAY));
    }
}
