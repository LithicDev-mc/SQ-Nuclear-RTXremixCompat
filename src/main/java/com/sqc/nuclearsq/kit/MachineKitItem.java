package com.sqc.nuclearsq.kit;

import java.util.List;

import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MachineKitItem extends Item {
    public final String kitId;

    public MachineKitItem(String kitId, Properties props) {
        super(props);
        this.kitId = kitId;
    }

    public MachineKits.Def def() {
        return MachineKits.def(kitId);
    }

    public static BlockPos target(Level level, BlockPos clicked, net.minecraft.core.Direction face) {
        return MachineKits.free(level, clicked) ? clicked : clicked.relative(face);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!player.mayBuild()) return InteractionResult.FAIL;
        MachineKits.Placement pl = MachineKits.placement(level, def(), target(level, ctx.getClickedPos(), ctx.getClickedFace()),
                player.getDirection());
        List<BlockPos> blocked = MachineKits.blocked(level, pl, player);
        if (!blocked.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.kit.blocked", blocked.size())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!MachineKits.place(level, pl, player)) return InteractionResult.FAIL;
        MachineKits.restoreEnergy(level, pl, ctx.getItemInHand().getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0));
        Component name = getName(ctx.getItemInHand());
        if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        player.displayClientMessage(Component.translatable("message.nuclearsq.kit.placed", name), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        MultiblockStructure s = def().structure();
        tip.add(Component.translatable("item.nuclearsq.kit.size", s.width(), s.height(), s.depth()).withStyle(ChatFormatting.GRAY));
        int fe = stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0);
        if (fe > 0) tip.add(Component.translatable("item.nuclearsq.kit.stored_energy", String.format("%,d", fe)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.nuclearsq.kit.tip1").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable("item.nuclearsq.kit.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
