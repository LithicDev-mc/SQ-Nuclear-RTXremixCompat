package com.sqc.nuclearsq.radiation;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.sqc.nuclearsq.registry.CatalogRegistry;

public class DeconSprayerItem extends Item {
    public static final int CHARGES = 32;
    public static final int RADIUS = 2;
    public static final float CLEAN_SV_H = RadiationValues.MAX_GROUND_SV_H;

    public DeconSprayerItem(Properties props) {
        super(props);
    }

    public static int water(ItemStack st) {
        return st.getMaxDamage() - st.getDamageValue();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack st = ctx.getItemInHand();
        if (refill(level, player, st)) return InteractionResult.sidedSuccess(level.isClientSide);
        if (!(level instanceof ServerLevel sl)) return water(st) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        if (water(st) <= 0) {
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.sprayer.empty"), true);
            return InteractionResult.FAIL;
        }
        if (Contamination.isFixed(sl, ctx.getClickedPos())) {
            if (player != null) player.displayClientMessage(Component.translatable("message.nuclearsq.sprayer.source"), true);
            return InteractionResult.FAIL;
        }
        if (player != null && !player.mayUseItemAt(ctx.getClickedPos(), ctx.getClickedFace(), st)) return InteractionResult.FAIL;
        BlockPos c = ctx.getClickedPos().relative(ctx.getClickedFace());
        int cleaned = spray(sl, c);
        if (player == null || !player.getAbilities().instabuild) st.setDamageValue(st.getDamageValue() + 1);
        if (player != null) {
            player.displayClientMessage(cleaned > 0
                    ? Component.translatable("message.nuclearsq.sprayer.cleaned", cleaned, water(st), CHARGES)
                    : Component.translatable("message.nuclearsq.sprayer.clean", water(st), CHARGES), true);
        }
        return InteractionResult.CONSUME;
    }

    public static int spray(ServerLevel sl, BlockPos c) {
        int n = Contamination.clean(sl, c, RADIUS, CLEAN_SV_H);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RADIUS, -1, -RADIUS), c.offset(RADIUS, 1, RADIUS))) {
            if (sl.getBlockState(p).is(CatalogRegistry.FALLOUT.get())) {
                sl.removeBlock(p, false);
                n++;
            }
        }
        for (var ie : sl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(c).inflate(RADIUS, 1, RADIUS))) {
            if (ItemContamination.get(ie.getItem()) > 0) {
                ItemStack clean = ie.getItem().copy();
                ItemContamination.set(clean, 0f);
                ie.setItem(clean);
                n++;
            }
        }
        sl.sendParticles(ParticleTypes.SPLASH, c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 0.5, 40, RADIUS * 0.6, 0.1, RADIUS * 0.6, 0.1);
        sl.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 0.3, c.getZ() + 0.5, 6, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.01);
        sl.playSound(null, c, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6f, 1.3f);
        return n;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (refill(level, player, st)) return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(st);
        return sprayPlayer(level, player, player, st) ? InteractionResultHolder.sidedSuccess(st, level.isClientSide)
                : InteractionResultHolder.pass(st);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player player, net.minecraft.world.entity.LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player other)) return InteractionResult.PASS;
        return sprayPlayer(player.level(), player, other, st) ? InteractionResult.sidedSuccess(player.level().isClientSide) : InteractionResult.PASS;
    }

    private static boolean sprayPlayer(Level level, Player user, Player target, ItemStack st) {
        if (water(st) <= 0) {
            if (!level.isClientSide) user.displayClientMessage(Component.translatable("message.nuclearsq.sprayer.empty"), true);
            return false;
        }
        if (!(level instanceof ServerLevel sl)) return true;
        boolean washed = Contamination.sprayBody(target);
        if (!user.getAbilities().instabuild) st.setDamageValue(st.getDamageValue() + 1);
        sl.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.3, 0.5, 0.3, 0.1);
        sl.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1.0, target.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
        sl.playSound(null, target.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6f, 1.4f);
        user.displayClientMessage(washed
                ? Component.translatable("message.nuclearsq.sprayer.body", RadiationManager.fmtRate(Contamination.body(target)), water(st), CHARGES)
                : Component.translatable("message.nuclearsq.sprayer.clean", water(st), CHARGES), true);
        return true;
    }

    private boolean refill(Level level, Player player, ItemStack st) {
        if (player == null || st.getDamageValue() == 0) return false;
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos p = ((BlockHitResult) hit).getBlockPos();
        if (!level.getFluidState(p).isSourceOfType(Fluids.WATER)) return false;
        if (!level.isClientSide) {
            st.setDamageValue(0);
            level.playSound(null, p, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1f, 1f);
        }
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.nuclearsq.sprayer.water", water(stack), CHARGES).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.nuclearsq.sprayer.use").withStyle(ChatFormatting.GRAY));
    }
}
