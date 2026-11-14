package com.sqc.nuclearsq.robotics.decon;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class RemoteControlItem extends Item {
    public RemoteControlItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DeconRobotEntity robot)) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            ItemStack real = player.getItemInHand(hand);
            (real.is(this) ? real : st).set(DeconRegistry.BOUND.get(), robot.getUUID());
            player.displayClientMessage(Component.translatable("message.nuclearsq.decon_remote.bound"), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) {
            List<BlockPos> z = ctx.getItemInHand().getOrDefault(DeconRegistry.ZONE.get(), List.of());
            z = z.size() == 1 ? List.of(z.get(0), ctx.getClickedPos()) : List.of(ctx.getClickedPos());
            ctx.getItemInHand().set(DeconRegistry.ZONE.get(), z);
            ctx.getPlayer().displayClientMessage(z.size() == 1 ? Component.translatable("message.nuclearsq.decon_remote.corner")
                    : Component.translatable("message.nuclearsq.decon_remote.zone", DeconRobotEntity.zoneSize(z.get(0).getX(), z.get(1).getX()),
                    DeconRobotEntity.zoneSize(z.get(0).getZ(), z.get(1).getZ())), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        DeconRobotEntity current = DeconRobotEntity.controlledBy(sp);
        if (current != null) {
            current.toggleArm();
            return InteractionResultHolder.consume(st);
        }
        UUID id = st.get(DeconRegistry.BOUND.get());
        if (id == null) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.decon_remote.unbound"), true);
            return InteractionResultHolder.consume(st);
        }
        if (!(((ServerLevel) level).getEntity(id) instanceof DeconRobotEntity robot) || !robot.isAlive()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.decon_remote.missing"), true);
            return InteractionResultHolder.consume(st);
        }
        if (!sp.isShiftKeyDown()) robot.startControl(sp);
        else if (robot.zoneJob()) robot.stopZone(sp);
        else robot.startZone(sp, st.getOrDefault(DeconRegistry.ZONE.get(), List.of()));
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(stack.has(DeconRegistry.BOUND.get()) ? "tooltip.nuclearsq.decon_remote.bound" : "tooltip.nuclearsq.decon_remote.unbound")
                .withStyle(ChatFormatting.GRAY));
        List<BlockPos> z = stack.getOrDefault(DeconRegistry.ZONE.get(), List.of());
        if (z.size() == 2) tip.add(Component.translatable("tooltip.nuclearsq.decon_remote.zone", DeconRobotEntity.zoneSize(z.get(0).getX(), z.get(1).getX()),
                DeconRobotEntity.zoneSize(z.get(0).getZ(), z.get(1).getZ())).withStyle(ChatFormatting.GRAY));
    }
}
