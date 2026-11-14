package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.compat.sable.SableCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SableReachTests {
    private SableReachTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "sable_reach", timeoutTicks = 20)
    public static void distanceIsPlainWithoutShip(GameTestHelper h) {
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1)), b = h.absolutePos(new BlockPos(4, 2, 5));
        double want = a.distSqr(b);
        double got = SableCompat.distSqr(h.getLevel(), a, b);
        if (Math.abs(got - want) > 1e-9) throw new GameTestAssertException("Abstand: " + got + " statt " + want);
        Vec3 va = new Vec3(0.3, 1, 2), vb = new Vec3(3.3, 5, 2);
        got = SableCompat.distSqr(h.getLevel(), va, vb);
        if (Math.abs(got - va.distanceToSqr(vb)) > 1e-9) throw new GameTestAssertException("Vec3-Abstand: " + got);
        if (!SableCompat.toWorld(h.getLevel(), a).equals(a)) throw new GameTestAssertException("BlockPos veraendert");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "sable_reach", timeoutTicks = 20)
    public static void entityQueryIsVanillaWithoutShip(GameTestHelper h) {
        ArmorStand in = EntityType.ARMOR_STAND.create(h.getLevel());
        ArmorStand out = EntityType.ARMOR_STAND.create(h.getLevel());
        Vec3 pin = h.absoluteVec(new Vec3(2.5, 2, 2.5)), pout = h.absoluteVec(new Vec3(12.5, 2, 12.5));
        in.setPos(pin);
        out.setPos(pout);
        h.getLevel().addFreshEntity(in);
        h.getLevel().addFreshEntity(out);
        AABB box = new AABB(pin, pin).inflate(2);
        List<ArmorStand> found = SableCompat.entitiesIn(h.getLevel(), ArmorStand.class, box, null);
        List<ArmorStand> vanilla = h.getLevel().getEntitiesOfClass(ArmorStand.class, box);
        if (found.size() != vanilla.size() || !found.contains(in) || found.contains(out))
            throw new GameTestAssertException("Entity-Suche weicht von Vanilla ab: " + found.size() + " statt " + vanilla.size());
        List<ArmorStand> none = SableCompat.entitiesIn(h.getLevel(), ArmorStand.class, box, e -> false);
        if (!none.isEmpty()) throw new GameTestAssertException("Filter ignoriert");
        in.discard();
        out.discard();
        h.succeed();
    }
}
