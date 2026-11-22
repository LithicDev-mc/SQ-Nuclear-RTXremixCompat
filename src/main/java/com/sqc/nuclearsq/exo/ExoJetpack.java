package com.sqc.nuclearsq.exo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class ExoJetpack {
    private ExoJetpack() {}

    public static final int UP = 1, DOWN = 2, HOVER = 4, CLAW = 8, BOOST = 16, COST = 100;
    private static final double[] UP_MAX = {0, 0.35, 0.45, 0.55, 0.65, 0.75};
    private static final double GLIDE = -0.1, DIVE = -0.45, BOOST_GAIN = 1.1, BOOST_MAX = 0.6;

    private static final Map<UUID, long[]> INPUT = new ConcurrentHashMap<>();
    private static final Field FLYING_TICKS = flyingTicks();

    private static Field flyingTicks() {
        try {
            Field f = ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    public static int level(Player p) {
        return Math.min(ExoModule.MAX_LEVEL, ExoEvents.level(p, ExoModule.Kind.JETPACK));
    }

    public static boolean touchesWall(Player p) {
        return !p.level().noCollision(p, p.getBoundingBox().inflate(0.1, 0, 0.1));
    }

    public static boolean hasEnergy(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack c = inv.getItem(i);
            if (c.getItem() instanceof ExoCoreItem && ExoCoreItem.energy(c) >= COST) return true;
        }
        return false;
    }

    public static void apply(Player p, int level, int bits) {
        Vec3 v = p.getDeltaMovement();
        double y = v.y;
        if ((bits & UP) != 0) y = Math.min(Math.max(y, 0) + 0.15, UP_MAX[level]);
        else if ((bits & HOVER) != 0 && level >= 2) y = 0;
        else if ((bits & DOWN) != 0) y = Math.min(y, DIVE);
        else if (y < GLIDE) y = GLIDE;
        double x = v.x, z = v.z;
        if ((bits & BOOST) != 0 && !p.onGround()) {
            x *= BOOST_GAIN;
            z *= BOOST_GAIN;
            double h = Math.sqrt(x * x + z * z);
            if (h > BOOST_MAX) {
                x *= BOOST_MAX / h;
                z *= BOOST_MAX / h;
            }
        }
        p.setDeltaMovement(x, y, z);
        p.resetFallDistance();
    }

    public static void setInput(ServerPlayer p, int bits) {
        INPUT.put(p.getUUID(), new long[] {bits, p.level().getGameTime()});
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        int lvl = level(sp);
        boolean claws = ExoEvents.level(sp, ExoModule.Kind.CLAWS) > 0;
        if (lvl == 0 && !claws) return;
        long[] in = INPUT.get(sp.getUUID());
        int bits = in != null && sp.level().getGameTime() - in[1] <= 2 ? (int) in[0] : 0;
        if (lvl > 0) {
            if ((bits & (UP | HOVER)) != 0 && !ExoCoreItem.drain(sp, COST)) bits &= ~(UP | HOVER);
            if ((bits & BOOST) != 0 && !hasEnergy(sp)) bits &= ~BOOST;
            apply(sp, lvl, bits);
        }
        if (claws && (bits & CLAW) != 0) {
            Vec3 v = sp.getDeltaMovement();
            sp.setDeltaMovement(v.x, 0, v.z);
            sp.resetFallDistance();
        } else bits &= ~CLAW;
        if ((bits & (UP | HOVER | CLAW)) != 0 && FLYING_TICKS != null) {
            try {
                FLYING_TICKS.setInt(sp.connection, 0);
            } catch (ReflectiveOperationException | RuntimeException ex) {
            }
        }
    }
}
