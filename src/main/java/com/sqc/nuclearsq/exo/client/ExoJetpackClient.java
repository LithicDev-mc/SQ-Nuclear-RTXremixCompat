package com.sqc.nuclearsq.exo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoEvents;
import com.sqc.nuclearsq.exo.ExoJetpack;
import com.sqc.nuclearsq.exo.ExoJetpackPayload;
import com.sqc.nuclearsq.exo.ExoModule;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ExoJetpackClient {
    private ExoJetpackClient() {}

    public static final KeyMapping UP_KEY = new KeyMapping("key.nuclearsq.jetpack_up",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nuclearsq");
    public static final KeyMapping DOWN_KEY = new KeyMapping("key.nuclearsq.jetpack_down",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nuclearsq");
    public static final KeyMapping BOOST_KEY = new KeyMapping("key.nuclearsq.jetpack_boost",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nuclearsq");

    @EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent e) {
            e.register(UP_KEY);
            e.register(DOWN_KEY);
            e.register(BOOST_KEY);
        }
    }

    private static boolean held(KeyMapping own, KeyMapping fallback) {
        return own.isUnbound() ? fallback.isDown() : own.isDown();
    }

    private static boolean prevJump, hover, clinging;
    private static long lastPress;
    private static int sent;
    private static double slide;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        Player p = e.getEntity();
        if (p != mc.player) return;
        int lvl = ExoJetpack.level(p);
        boolean claws = ExoEvents.level(p, ExoModule.Kind.CLAWS) > 0;
        if (lvl == 0 && !claws) {
            hover = false;
            clinging = false;
            send(0);
            return;
        }
        int bits = 0;
        if (lvl > 0) {
            boolean jump = held(UP_KEY, mc.options.keyJump), down = held(DOWN_KEY, mc.options.keyShift), boost = held(BOOST_KEY, mc.options.keySprint);
            long now = p.level().getGameTime();
            if (p.onGround() || down) hover = false;
            if (lvl >= 2 && jump && !prevJump && !p.onGround() && now - lastPress <= 7) hover = !hover;
            if (jump && !prevJump) lastPress = now;
            prevJump = jump;
            bits = (jump ? ExoJetpack.UP : 0) | (down ? ExoJetpack.DOWN : 0) | (hover ? ExoJetpack.HOVER : 0) | (boost ? ExoJetpack.BOOST : 0);
            if ((bits & (ExoJetpack.UP | ExoJetpack.HOVER | ExoJetpack.BOOST)) != 0 && !ExoJetpack.hasEnergy(p)) bits &= ~(ExoJetpack.UP | ExoJetpack.HOVER | ExoJetpack.BOOST);
            ExoJetpack.apply(p, lvl, bits);
        }
        if (claws) bits |= cling(mc, p);
        send(bits);
    }

    private static int cling(Minecraft mc, Player p) {
        boolean on = mc.options.keyUse.isDown() && !p.onGround() && ExoJetpack.touchesWall(p);
        if (on && !clinging) {
            if (p.getDeltaMovement().y > 0) on = false;
            else {
                clinging = true;
                slide = 2.0;
            }
        }
        if (!on) {
            clinging = false;
            return 0;
        }
        double y = slide > 0.05 ? -0.1 * slide : 0;
        slide = y == 0 ? 0 : slide * 0.9;
        p.setDeltaMovement(p.getDeltaMovement().x, y, p.getDeltaMovement().z);
        p.resetFallDistance();
        return ExoJetpack.CLAW;
    }

    private static void send(int bits) {
        if (bits == 0 && sent == 0) return;
        sent = bits;
        PacketDistributor.sendToServer(new ExoJetpackPayload(bits));
    }
}
