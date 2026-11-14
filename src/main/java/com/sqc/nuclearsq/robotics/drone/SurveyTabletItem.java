package com.sqc.nuclearsq.robotics.drone;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

public class SurveyTabletItem extends Item {
    public SurveyTabletItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SurveyDroneEntity drone)) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            ItemStack real = player.getItemInHand(hand);
            if (!real.is(this)) real = st;
            real.set(DroneRegistry.BOUND.get(), drone.getUUID());
            if (player.isShiftKeyDown()) send(player, real, drone);
            else player.displayClientMessage(Component.translatable("message.nuclearsq.survey_tablet.bound"), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) {
            List<BlockPos> z = ctx.getItemInHand().getOrDefault(DroneRegistry.ZONE.get(), List.of());
            z = z.size() == 1 ? List.of(z.get(0), ctx.getClickedPos()) : List.of(ctx.getClickedPos());
            ctx.getItemInHand().set(DroneRegistry.ZONE.get(), z);
            ctx.getPlayer().displayClientMessage(z.size() == 1 ? Component.translatable("message.nuclearsq.survey_tablet.corner")
                    : Component.translatable("message.nuclearsq.survey_tablet.zone", Math.abs(z.get(0).getX() - z.get(1).getX()) + 1,
                    Math.abs(z.get(0).getZ() - z.get(1).getZ()) + 1), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (!player.isShiftKeyDown()) com.sqc.nuclearsq.robotics.drone.client.SurveyMapScreen.open(hand);
            return InteractionResultHolder.success(st);
        }
        UUID id = st.get(DroneRegistry.BOUND.get());
        SurveyDroneEntity drone = id != null && ((ServerLevel) level).getEntity(id) instanceof SurveyDroneEntity d && d.isAlive() ? d : null;
        if (drone == null) {
            if (player.isShiftKeyDown() || id == null) player.displayClientMessage(Component.translatable(id == null ? "message.nuclearsq.survey_tablet.unbound" : "message.nuclearsq.survey_tablet.missing"), true);
            return InteractionResultHolder.consume(st);
        }
        if (player.isShiftKeyDown()) send(player, st, drone);
        else if (drone.map() != null) st.set(DroneRegistry.MAP.get(), new SurveyMap(drone.map().x0(), drone.map().z0(), drone.map().w(), drone.map().h(), drone.map().cells().clone()));
        return InteractionResultHolder.consume(st);
    }

    private static void send(Player player, ItemStack st, SurveyDroneEntity drone) {
        String r = drone.busy() ? "recall" : drone.start(st.getOrDefault(DroneRegistry.ZONE.get(), List.of()));
        if (r.equals("recall")) drone.recall();
        player.displayClientMessage(Component.translatable("message.nuclearsq.survey_drone." + r, SurveyDroneEntity.ZONE_MAX, SurveyDroneEntity.ZONE_MAX), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(stack.has(DroneRegistry.BOUND.get()) ? "tooltip.nuclearsq.survey_tablet.bound" : "tooltip.nuclearsq.survey_tablet.unbound")
                .withStyle(ChatFormatting.GRAY));
        List<BlockPos> z = stack.getOrDefault(DroneRegistry.ZONE.get(), List.of());
        if (z.size() == 2) tip.add(Component.translatable("tooltip.nuclearsq.survey_tablet.zone", Math.abs(z.get(0).getX() - z.get(1).getX()) + 1,
                Math.abs(z.get(0).getZ() - z.get(1).getZ()) + 1).withStyle(ChatFormatting.GRAY));
    }
}
