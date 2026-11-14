package com.sqc.nuclearsq.progression;

import com.sqc.nuclearsq.progression.client.ProgressionClient;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;

public class ProgressionBookItem extends Item {
    public ProgressionBookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) ProgressionClient.open();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
