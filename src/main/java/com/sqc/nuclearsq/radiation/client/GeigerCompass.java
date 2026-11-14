package com.sqc.nuclearsq.radiation.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.network.GeigerPayload;
import com.sqc.nuclearsq.radiation.GeigerCounterItem;
import com.sqc.nuclearsq.radiation.RadiationCompass;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GeigerCompass {
    private GeigerCompass() {}

    private static final long STALE_MS = 2000L;
    public static final int[] COLORS = {0xFFFFD92B, 0xFFFF8A1E, 0xFFFF3030};

    private static volatile boolean has, here;
    private static volatile double tx, ty, tz;
    private static volatile float apparent;
    private static volatile long lastMs;

    private static float cur, vel;
    private static long lastTick = Long.MIN_VALUE;

    public static void receive(GeigerPayload p) {
        has = p.hasTarget();
        here = p.here();
        tx = p.x();
        ty = p.y();
        tz = p.z();
        apparent = p.apparent();
        lastMs = System.currentTimeMillis();
    }

    public static boolean active() {
        return has && System.currentTimeMillis() - lastMs <= STALE_MS;
    }

    public static boolean here() {
        return active() && here;
    }

    public static int level() {
        return Math.min(COLORS.length - 1, RadiationCompass.level(apparent));
    }

    public static int color() {
        return COLORS[level()];
    }

    public static double distance(Player p) {
        return Math.sqrt(sq(tx - p.getX()) + sq(ty - p.getEyeY()) + sq(tz - p.getZ()));
    }

    public static double heightDiff(Player p) {
        return ty - p.getEyeY();
    }

    public static float rawTurns(Player p) {
        return RadiationCompass.bearingTurns(p.getYRot(), tx - p.getX(), tz - p.getZ());
    }

    private static double sq(double v) {
        return v * v;
    }

    private static float needle(Player p, long gameTime) {
        if (here()) return (System.currentTimeMillis() % 900L) / 900f;
        float target = rawTurns(p);
        if (lastTick == Long.MIN_VALUE || gameTime - lastTick > 10 || gameTime < lastTick) {
            cur = target;
            vel = 0f;
        } else if (gameTime != lastTick) {
            float d = target - cur;
            d -= (float) Math.floor(d + 0.5f);
            vel = vel * 0.8f + d * 0.1f;
            cur += vel;
            cur -= (float) Math.floor(cur);
        }
        lastTick = gameTime;
        return cur;
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            GeigerCounterItem geiger = CatalogRegistry.GEIGER_COUNTER.get();
            ItemProperties.register(geiger, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "geiger_signal"),
                    (ItemStack stack, ClientLevel level, LivingEntity entity, int seed) -> held(stack, entity) ? 1f : 0f);
            ItemProperties.register(geiger, ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "geiger_angle"),
                    (ItemStack stack, ClientLevel level, LivingEntity entity, int seed) ->
                            held(stack, entity) && entity instanceof Player p && level != null ? needle(p, level.getGameTime()) : 0f);
        });
    }

    private static boolean held(ItemStack stack, LivingEntity entity) {
        if (!active() || entity == null || entity != Minecraft.getInstance().player) return false;
        return entity.getMainHandItem() == stack || entity.getOffhandItem() == stack;
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 1 ? color() : 0xFFFFFFFF, CatalogRegistry.GEIGER_COUNTER.get());
    }
}
