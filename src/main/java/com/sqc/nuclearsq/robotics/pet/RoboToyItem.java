package com.sqc.nuclearsq.robotics.pet;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RoboToyItem extends Item {
    private final boolean wand;

    public RoboToyItem(Properties props, boolean wand) {
        super(props);
        this.wand = wand;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (wand) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.25f, 2.0f);
            if (!level.isClientSide) {
                for (RoboPetEntity c : level.getEntitiesOfClass(RoboPetEntity.class, player.getBoundingBox().inflate(8), c -> c.isCat() && c.isOwnedBy(player) && !c.lying())) c.playWith();
            }
            player.getCooldowns().addCooldown(this, 8);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.8f);
            if (!level.isClientSide) {
                ItemEntity ball = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), player.getAbilities().instabuild ? stack.copyWithCount(1) : stack.split(1));
                ball.setDeltaMovement(player.getLookAngle().scale(0.9).add(0, 0.12, 0));
                ball.setThrower(player);
                ball.setPickUpDelay(40);
                ball.setUnlimitedLifetime();
                level.addFreshEntity(ball);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
