package com.sqc.nuclearsq.nanobot;

import java.util.List;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class NanoCanisterItem extends Item {
    public NanoCanisterItem(Properties props) {
        super(props);
    }

    public static NanoTask task(ItemStack stack) {
        return NanoTask.byOrdinal(stack.getOrDefault(NanobotRegistry.TASK.get(), 0));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level.isClientSide);
        ItemStack stack = ctx.getItemInHand();
        if (level.getBlockState(ctx.getClickedPos()).getBlock() instanceof DroneItemPortBlock) {
            if (sp.isShiftKeyDown()) NanoPort.uncouple(sp, stack, true);
            else NanoPort.couple(sp, stack, ctx.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        if (task(stack).gadget() && !sp.isShiftKeyDown()) {
            NanoGadget.click(sp, stack, ctx.getClickedPos(), ctx.getClickedFace());
            return InteractionResult.SUCCESS;
        }
        if (!sp.isShiftKeyDown() && NanoSettings.of(stack).point()) return point(sp, stack, ctx.getClickedPos(), ctx.getClickedFace());
        return InteractionResult.PASS;
    }

    private static final java.util.Map<java.util.UUID, long[]> HOLD = new java.util.HashMap<>();

    private static InteractionResult point(ServerPlayer sp, ItemStack stack, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        long now = sp.level().getGameTime();
        long[] h = HOLD.computeIfAbsent(sp.getUUID(), k -> new long[3]);
        if (now - h[0] > 6) {
            h[1] = now;
            h[2] = 0;
        }
        h[0] = now;
        boolean area = now - h[1] >= 20 && h[2] == 0;
        if (now != h[1] && !area) return InteractionResult.SUCCESS;
        if (area) h[2] = 1;
        net.minecraft.world.level.block.state.BlockState st = sp.level().getBlockState(pos);
        if (st.isAir() || st.getDestroySpeed(sp.level(), pos) < 0) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.point_unsupported"), true);
            return InteractionResult.SUCCESS;
        }
        if (Math.sqrt(sp.blockPosition().distSqr(pos)) > NanoSettings.of(stack).radius()) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.point_far"), true);
            return InteractionResult.SUCCESS;
        }
        NanoSwarm sw = NanoSwarms.get(sp);
        if (sw == null || sw.isEnded()) {
            if (!NanoPower.hasCore(sp)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.no_reactor"), true);
                return InteractionResult.SUCCESS;
            }
            if (!NuclearConfig.value(NuclearConfig.NANOBOT_ENABLED)) {
                sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.disabled"), true);
                return InteractionResult.SUCCESS;
            }
            sw = NanoSwarms.start(sp, task(stack));
            sw.markPointOnly();
        }
        if (area) {
            List<net.minecraft.core.BlockPos> all = new java.util.ArrayList<>(com.sqc.nuclearsq.exo.ExoMining.shape(pos, face, net.minecraft.world.phys.Vec3.ZERO, 5));
            all.add(pos);
            sw.pointArea(pos, all);
        } else sw.point(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            if (level.isClientSide && !player.isShiftKeyDown()) com.sqc.nuclearsq.nanobot.client.NanoWheelScreen.open();
            if (!level.isClientSide) NuclearSQ.LOGGER.info("[nanobots] Kanister: Spieler {} ist kein ServerPlayer", player.getClass().getSimpleName());
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            applyTask(sp, stack, task(stack).next());
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    public static void openMenu(ServerPlayer sp, InteractionHand hand) {
        int slot = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : 40;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new NanoCanisterMenu(id, inv, slot), Component.translatable("gui.nuclearsq.nanobots.title")),
                buf -> buf.writeInt(slot));
    }

    public static void applyTask(ServerPlayer sp, ItemStack stack, NanoTask next) {
        NanoSwarm running = NanoSwarms.get(sp);
        stack.set(NanobotRegistry.TASK.get(), next.ordinal());
        GadgetData gd = NanoGadget.data(stack);
        if (gd.points() > 0) NanoGadget.setData(stack, gd.withPoints(java.util.Optional.empty(), java.util.Optional.empty()));
        if (running != null) running.setTask(next);
        NuclearSQ.LOGGER.info("[nanobots] Kanister: Aufgabe {} (Schwarm laeuft: {})", next, running != null);
        sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.task", next.label()), true);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    public static void toggle(ServerPlayer sp, ItemStack stack) {
        NanoSwarm running = NanoSwarms.get(sp);
        if (running != null && !running.isEnded()) {
            NuclearSQ.LOGGER.info("[nanobots] Kanister: Rueckruf, Phase {}", running.phase());
            running.recall();
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.recalled"), true);
        } else if (!NanoPower.hasCore(sp)) {
            NuclearSQ.LOGGER.info("[nanobots] Kanister: kein Exo-Kern im Inventar von {}", sp.getName().getString());
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.no_reactor"), true);
            return;
        } else if (!NuclearConfig.value(NuclearConfig.NANOBOT_ENABLED)) {
            NuclearSQ.LOGGER.info("[nanobots] Kanister: Nano-Bots in der Config ausgeschaltet");
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.disabled"), true);
            return;
        } else {
            NanoTask t = task(stack);
            NanoSwarms.start(sp, t);
            NuclearSQ.LOGGER.info("[nanobots] Kanister: Schwarm gestartet, Aufgabe {}", t);
            sp.displayClientMessage(Component.translatable("message.nuclearsq.nanobots.started", t.label()), true);
        }
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.nuclearsq.nanobots.task", task(stack).label()).withStyle(ChatFormatting.AQUA));
        NanoSettings s = NanoSettings.of(stack);
        tooltip.add(Component.translatable("tooltip.nuclearsq.nanobots.stats", s.bots(), String.format("%.1f", s.speedFactor()), s.radius()).withStyle(ChatFormatting.GRAY));
    }
}
