package com.sqc.nuclearsq.robotics;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class LinkToolItem extends Item {
    public LinkToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack st, UseOnContext ctx) {
        var level = ctx.getLevel();
        var player = ctx.getPlayer();
        BlockPos clicked = ctx.getClickedPos();
        boolean shift = player != null && player.isShiftKeyDown();
        if (level.getBlockEntity(clicked) instanceof RobotArmBlockEntity arm) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (shift) {
                if (player instanceof ServerPlayer sp) sp.openMenu(arm, buf -> buf.writeBlockPos(clicked));
                return InteractionResult.CONSUME;
            }
            st.set(RoboticsRegistry.LINK_ARM.get(), clicked.immutable());
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.link_tool.arm", clicked.toShortString()), true);
            applyPending(st, arm, player);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos armPos = st.get(RoboticsRegistry.LINK_ARM.get());
        if (armPos == null) {
            remember(st, shift, clicked, ctx.getClickedFace());
            if (player != null) player.displayClientMessage(Component.translatable(shift ? "message.nuclearsq.link_tool.kept_target" : "message.nuclearsq.link_tool.kept_source",
                    clicked.toShortString(), ctx.getClickedFace().getName()), true);
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(armPos) instanceof RobotArmBlockEntity arm)) {
            st.remove(RoboticsRegistry.LINK_ARM.get());
            remember(st, shift, clicked, ctx.getClickedFace());
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.link_tool.lost"), true);
            return InteractionResult.CONSUME;
        }
        if (!arm.inReach(clicked)) {
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.link_tool.reach"), true);
            return InteractionResult.CONSUME;
        }
        set(arm, shift, clicked, ctx.getClickedFace(), player);
        return InteractionResult.CONSUME;
    }

    private static void set(RobotArmBlockEntity arm, boolean target, BlockPos pos, Direction face, @Nullable Player player) {
        boolean added = !target || arm.setTarget(pos, face);
        if (!target) arm.setSource(pos, face);
        if (arm.getLevel() instanceof ServerLevel sl) sl.sendParticles(target ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.01);
        if (player != null) player.displayClientMessage(Component.translatable(!added ? "message.nuclearsq.link_tool.target_removed" : target ? "message.nuclearsq.link_tool.target" : "message.nuclearsq.link_tool.source",
                pos.toShortString(), face.getName(), arm.targets().size(), RobotArmBlockEntity.MAX_TARGETS), true);
    }

    private static void remember(ItemStack st, boolean target, BlockPos pos, Direction face) {
        CompoundTag t = st.getOrDefault(RoboticsRegistry.LINK_PENDING.get(), new CompoundTag()).copy();
        String k = target ? "dst" : "src";
        t.putLong(k, pos.asLong());
        t.putByte(k + "Side", (byte) face.get3DDataValue());
        st.set(RoboticsRegistry.LINK_PENDING.get(), t);
    }

    private static void applyPending(ItemStack st, RobotArmBlockEntity arm, @Nullable Player player) {
        CompoundTag t = st.get(RoboticsRegistry.LINK_PENDING.get());
        if (t == null) return;
        st.remove(RoboticsRegistry.LINK_PENDING.get());
        for (boolean target : new boolean[] {false, true}) {
            String k = target ? "dst" : "src";
            if (!t.contains(k)) continue;
            BlockPos pos = BlockPos.of(t.getLong(k));
            if (!arm.inReach(pos)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.link_tool.reach"), false);
                continue;
            }
            set(arm, target, pos, Direction.from3DDataValue(t.getByte(k + "Side")), player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BlockPos p = stack.get(RoboticsRegistry.LINK_ARM.get());
        if (p != null) tip.add(Component.translatable("tooltip.nuclearsq.link_tool.arm", p.toShortString()).withStyle(ChatFormatting.GRAY));
        CompoundTag t = stack.get(RoboticsRegistry.LINK_PENDING.get());
        if (t != null && t.contains("src")) tip.add(Component.translatable("tooltip.nuclearsq.link_tool.kept_source", BlockPos.of(t.getLong("src")).toShortString()).withStyle(ChatFormatting.GRAY));
        if (t != null && t.contains("dst")) tip.add(Component.translatable("tooltip.nuclearsq.link_tool.kept_target", BlockPos.of(t.getLong("dst")).toShortString()).withStyle(ChatFormatting.GRAY));
    }
}
