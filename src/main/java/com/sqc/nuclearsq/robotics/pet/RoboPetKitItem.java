package com.sqc.nuclearsq.robotics.pet;

import java.util.List;
import java.util.function.Supplier;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class RoboPetKitItem extends Item {
    private final Supplier<? extends EntityType<RoboPetEntity>> type;

    public RoboPetKitItem(Properties props, Supplier<? extends EntityType<RoboPetEntity>> type) {
        super(props);
        this.type = type;
    }

    public static int energy(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), RoboPetEntity.BATTERY), 0, RoboPetEntity.BATTERY);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        RoboPetEntity pet = type.get().create(sl);
        if (pet == null || ctx.getPlayer() == null) return InteractionResult.FAIL;
        pet.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getHorizontalDirection().toYRot(), 0);
        pet.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.SPAWN_EGG, null);
        if (!sl.noCollision(pet)) return InteractionResult.FAIL;
        pet.tame(ctx.getPlayer());
        pet.setEnergy(energy(ctx.getItemInHand()));
        sl.addFreshEntity(pet);
        if (!ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / RoboPetEntity.BATTERY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3AB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.nuclearsq.robo_pet.energy", energy(stack), RoboPetEntity.BATTERY));
    }
}
