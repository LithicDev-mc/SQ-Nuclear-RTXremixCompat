package com.sqc.nuclearsq.storage;

import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WifiTerminalItem extends Item {
    public WifiTerminalItem(Properties props) {
        super(props.stacksTo(1));
    }

    public static int capacity() {
        return WifiValues.terminal().capacity();
    }

    public static int energy(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0), 0, capacity());
    }

    public static int charge(ItemStack stack, int fe) {
        int add = Math.max(0, Math.min(fe, capacity() - energy(stack)));
        if (add > 0) stack.set(ModDataComponents.STORED_ENERGY.get(), energy(stack) + add);
        return add;
    }

    public static boolean drain(ItemStack stack, int fe) {
        int have = energy(stack);
        if (fe <= 0) return true;
        if (have < fe) return false;
        stack.set(ModDataComponents.STORED_ENERGY.get(), have - fe);
        return true;
    }

    public static IEnergyStorage storage(ItemStack stack) {
        return new ComponentEnergyStorage(stack, ModDataComponents.STORED_ENERGY.get(), capacity(), WifiValues.terminal().maxReceive(), 0);
    }

    @Nullable
    public static GlobalPos link(ItemStack stack) {
        return stack.getItem() instanceof WifiTerminalItem ? stack.get(ModDataComponents.WIFI_LINK.get()) : null;
    }

    public static void setLink(ItemStack stack, @Nullable GlobalPos pos) {
        if (pos == null) stack.remove(ModDataComponents.WIFI_LINK.get());
        else stack.set(ModDataComponents.WIFI_LINK.get(), pos);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                setLink(stack, null);
                sp.displayClientMessage(Component.translatable("message.nuclearsq.wifi.unlinked"), true);
            } else {
                WifiLink.open(sp, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3AB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.nuclearsq.wifi.energy", StorageFormat.count(energy(stack)), StorageFormat.count(capacity()))
                .withStyle(ChatFormatting.AQUA));
        GlobalPos link = link(stack);
        if (link == null) {
            lines.add(Component.translatable("tooltip.nuclearsq.wifi.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.nuclearsq.wifi.linked", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                    link.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
