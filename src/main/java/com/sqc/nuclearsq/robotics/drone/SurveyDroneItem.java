package com.sqc.nuclearsq.robotics.drone;

import java.util.List;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class SurveyDroneItem extends Item {
    public SurveyDroneItem(Properties props) {
        super(props);
    }

    public static int energy(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), SurveyDroneEntity.BATTERY), 0, SurveyDroneEntity.BATTERY);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        SurveyDroneEntity d = DroneRegistry.DRONE.get().create(sl);
        if (d == null) return InteractionResult.FAIL;
        d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getHorizontalDirection().toYRot(), 0);
        d.setEnergy(energy(ctx.getItemInHand()));
        sl.addFreshEntity(d);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / SurveyDroneEntity.BATTERY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3AB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.nuclearsq.robo_pet.energy", energy(stack), SurveyDroneEntity.BATTERY));
    }
}
