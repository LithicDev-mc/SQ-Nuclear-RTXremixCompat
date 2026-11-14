package com.sqc.nuclearsq.transport;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import com.sqc.nuclearsq.kit.MachineKits;

public class WrenchItem extends Item {
    public WrenchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null || !p.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return MachineKits.isPart(ctx.getLevel().getBlockState(ctx.getClickedPos())) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        MachineKits.Placement pl = MachineKits.owner(level, ctx.getClickedPos());
        if (pl == null) return InteractionResult.PASS;
        if (!MachineKits.mayDismantle(level, pl, p, ctx.getClickedPos())) return InteractionResult.FAIL;
        MachineKits.dismantle(level, pl, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        Block b = level.getBlockState(pos).getBlock();
        return b instanceof ConduitBlock || b instanceof PowerGateBlock;
    }

    public static boolean mayUse(Level level, BlockPos pos, Player player) {
        return player.mayBuild() && level.mayInteract(player, pos);
    }

    public static void dismantle(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp) sp.gameMode.destroyBlock(pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.nuclearsq.wrench_sqc.tip").withStyle(ChatFormatting.GRAY));
    }
}
