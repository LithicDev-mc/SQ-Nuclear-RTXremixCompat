package com.sqc.nuclearsq.radiation;

import com.sqc.nuclearsq.network.GeigerPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class GeigerCounterItem extends Item {
    private static final double AMBIENT_SV_H = RadiationManager.AMBIENT_SV_H;
    public static final double ELEVATED_SV_H = 1e-6;
    public static final double DANGER_SV_H = 1e-3;
    public static final double CPS_PER_USV_H = 1.8;
    public static final double TAU = 1.0 / 45.0;
    private static final int MAX_SOUNDS_PER_TICK = 3;

    private static final java.util.Map<Player, Double> RATE = new java.util.WeakHashMap<>();

    public GeigerCounterItem(Properties props) {
        super(props);
    }

    public static double clicksPerSecond(double svH) {
        double n = Math.max(0, svH) * 1e6 * CPS_PER_USV_H;
        return n / (1 + n * TAU);
    }

    public static int level(double svH) {
        return svH >= DANGER_SV_H ? 2 : svH >= ELEVATED_SV_H ? 1 : 0;
    }

    public static double measure(ServerLevel sl, Player player) {
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.5, 0);
        return RadiationManager.rateAt(sl, at) + RadiationManager.carried(player) + AMBIENT_SV_H;
    }

    public static ItemStack activeCounter(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GeigerCounterItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GeigerCounterItem) return off;
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof GeigerCounterItem) return s;
        }
        return null;
    }

    private static int poisson(RandomSource rnd, double mean, int max) {
        if (mean <= 0) return 0;
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        while (k < max) {
            p *= rnd.nextDouble();
            if (p <= l) break;
            k++;
        }
        return k;
    }

    public static GeigerPayload payload(ServerLevel sl, ServerPlayer sp, double rate, boolean inHand) {
        if (!inHand) return GeigerPayload.reading((float) rate);
        Vec3 at = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
        RadiationCompass.Target t = RadiationCompass.scan(sl, at);
        if (t == null) return GeigerPayload.reading((float) rate);
        return new GeigerPayload((float) rate, true, (float) t.pos().x, (float) t.pos().y, (float) t.pos().z,
                (float) t.apparentSvH(), t.here(at));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof Player player)) return;
        ItemStack active = activeCounter(player);
        if (active != stack) return;
        boolean inHand = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
        Double rate = RATE.get(player);
        if (rate == null || player.tickCount % 10 == 0) {
            rate = measure(sl, player);
            RATE.put(player, rate);
            if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && sp.connection != null) {
                PacketDistributor.sendToPlayer(sp, payload(sl, sp, rate, inHand));
            }
        }
        int clicks = poisson(sl.random, clicksPerSecond(rate) / 20.0, MAX_SOUNDS_PER_TICK);
        float base = inHand ? 0.45f : 0.22f;
        for (int k = 0; k < clicks; k++) {
            sl.playSound(null, player.getX(), player.getY() + 1, player.getZ(), com.sqc.nuclearsq.registry.ModSounds.GEIGER.get(),
                    SoundSource.PLAYERS, base * (0.85f + sl.random.nextFloat() * 0.3f), 0.9f + sl.random.nextFloat() * 0.2f);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl) {
            double rate = measure(sl, player);
            RATE.put(player, rate);
            int lvl = level(rate);
            Component msg = switch (lvl) {
                case 2 -> Component.translatable("message.nuclearsq.geiger.danger", RadiationManager.fmtRate(rate)).withStyle(ChatFormatting.RED);
                case 1 -> Component.translatable("message.nuclearsq.geiger.elevated", RadiationManager.fmtRate(rate)).withStyle(ChatFormatting.GOLD);
                default -> Component.translatable("message.nuclearsq.geiger.normal", RadiationManager.fmtRate(rate)).withStyle(ChatFormatting.GRAY);
            };
            player.displayClientMessage(msg, true);

            Vec3 at = player.position().add(0, player.getBbHeight() * 0.5, 0);
            float loud = (float) Math.min(1.0, 0.3 + Math.log10(1.0 + rate * 1e6) / 3.0);
            float pitch = (float) Math.min(2.0, 0.6 + Math.log10(1.0 + rate * 1e6) / 4.0);
            sl.playSound(null, BlockPos.containing(at), com.sqc.nuclearsq.registry.ModSounds.GEIGER.get(), SoundSource.PLAYERS, loud, pitch);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
