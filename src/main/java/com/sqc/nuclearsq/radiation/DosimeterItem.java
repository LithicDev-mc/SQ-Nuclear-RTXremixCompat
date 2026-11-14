package com.sqc.nuclearsq.radiation;

import java.util.List;
import java.util.Locale;

import com.sqc.nuclearsq.network.DosimeterPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class DosimeterItem extends Item {
    public static final double DAY_LIMIT_SV = 0.25;

    public DosimeterItem(Properties props) {
        super(props);
    }

    private static RadiationAttachments.Dosimeter data(Player p) {
        RadiationAttachments.Dosimeter d = p.getExistingDataOrNull(RadiationAttachments.DOSIMETER);
        return d == null ? RadiationAttachments.Dosimeter.EMPTY : d;
    }

    private static void store(Player p, RadiationAttachments.Dosimeter d) {
        p.setData(RadiationAttachments.DOSIMETER, d);
    }

    private static long day(Player p) {
        return p.level().getServer() != null ? p.level().getServer().overworld().getDayTime() / 24000L : 0;
    }

    public static double today(Player p) {
        RadiationAttachments.Dosimeter d = data(p);
        return d.day() == day(p) ? d.today() : 0;
    }

    public static double total(Player p) {
        Double t = p.getExistingDataOrNull(RadiationAttachments.DOSIMETER_TOTAL);
        return t == null ? 0 : t;
    }

    public static void tick(ServerPlayer sp, double received, double rate) {
        tick(sp, received, rate, rate, 0);
    }

    public static void tick(ServerPlayer sp, double received, double rate, double outside, double suit) {
        checkPin(sp);
        RadiationAttachments.Dosimeter d = data(sp);
        long day = day(sp);
        if (d.day() != day) d = d.withDay(day, 0, 0);
        if (received > 0) {
            d = d.withToday(d.today() + received);
            sp.setData(RadiationAttachments.DOSIMETER_TOTAL, total(sp) + received);
        }
        if (!carries(sp) || sp.connection == null) {
            store(sp, d);
            return;
        }
        double today = d.today();
        int level = today >= DAY_LIMIT_SV ? 2 : today >= DAY_LIMIT_SV / 2 ? 1 : 0;
        if (level > d.warn()) {
            d = d.withWarn(level);
            sp.displayClientMessage(Component.translatable(level == 2 ? "message.nuclearsq.dosimeter.limit" : "message.nuclearsq.dosimeter.half",
                    mSv(today), mSv(DAY_LIMIT_SV)).withStyle(level == 2 ? ChatFormatting.RED : ChatFormatting.GOLD), false);
            for (int k = 0; k < (level == 2 ? 3 : 1); k++) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
            }
        }
        store(sp, d.withShown((float) rate, (float) outside, (float) suit));
        send(sp);
    }

    private static void send(ServerPlayer sp) {
        if (sp instanceof FakePlayer || sp.connection == null) return;
        PacketDistributor.sendToPlayer(sp, payload(sp));
    }

    public static DosimeterPayload payload(Player p) {
        RadiationAttachments.Dosimeter d = data(p);
        float rate = Math.max(d.rate(), (float) RadiationManager.AMBIENT_SV_H);
        return new DosimeterPayload((float) today(p), rate, (float) Contamination.body(p), (float) total(p),
                d.pinned(), (float) RadiationManager.doseOf(p), RadiationManager.iodineTicksLeft(p),
                RadiationManager.medicineTicksLeft(p), d.outside(), d.suit(), filterOf(p));
    }

    private static int filterOf(Player p) {
        var mask = RespiratorItem.worn(p);
        return mask.isEmpty() ? -1 : RespiratorItem.filterLeft(mask);
    }

    public static boolean isPinned(Player p) {
        return data(p).pinned();
    }

    public static boolean togglePin(Player p) {
        boolean pinned = !isPinned(p);
        store(p, data(p).withPinned(pinned));
        if (p instanceof ServerPlayer sp) send(sp);
        return pinned;
    }

    public static void checkPin(Player p) {
        if (!isPinned(p) || pinSource(p)) return;
        store(p, data(p).withPinned(false));
        if (p instanceof ServerPlayer sp) send(sp);
    }

    private static boolean pinSource(Player p) {
        if (p.getOffhandItem().getItem() instanceof DosimeterItem) return true;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).getItem() instanceof DosimeterItem) return true;
        return false;
    }

    public static boolean carries(Player p) {
        return p.getInventory().hasAnyMatching(s -> s.getItem() instanceof DosimeterItem);
    }

    public static String fmtDose(double sv) {
        if (sv >= 1.0) return String.format(Locale.ROOT, "%.2f Sv", sv);
        if (sv >= 0.001) return String.format(Locale.ROOT, "%.2f mSv", sv * 1000.0);
        return String.format(Locale.ROOT, "%.1f \u00B5Sv", sv * 1e6);
    }

    public static String mSv(double sv) {
        return String.format(Locale.ROOT, "%.1f mSv", sv * 1000.0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            boolean pinned = togglePin(sp);
            sp.displayClientMessage(Component.translatable(pinned ? "message.nuclearsq.dosimeter.pinned"
                    : "message.nuclearsq.dosimeter.unpinned"), true);
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, pinned ? 1.8f : 1.3f);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.nuclearsq.dosimeter", mSv(DAY_LIMIT_SV)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.nuclearsq.dosimeter.pin").withStyle(ChatFormatting.GRAY));
    }
}
