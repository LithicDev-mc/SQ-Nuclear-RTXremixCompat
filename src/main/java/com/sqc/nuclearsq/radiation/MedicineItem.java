package com.sqc.nuclearsq.radiation;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class MedicineItem extends Item {
    public static final double REDUCE_SV = RadiationValues.MEDICINE_REDUCE_SV;

    public MedicineItem(Properties props) {
        super(props);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            RadiationManager.reduceDose(entity, REDUCE_SV);
            RadiationManager.takeMedicine(entity);
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.medicine", RadiationValues.MEDICINE_PROTECT_TICKS / 1200), true);
            }
        }
        if (entity instanceof Player pl && pl.getAbilities().instabuild) return stack;
        stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.nuclearsq.medicine").withStyle(ChatFormatting.GRAY));
    }
}
