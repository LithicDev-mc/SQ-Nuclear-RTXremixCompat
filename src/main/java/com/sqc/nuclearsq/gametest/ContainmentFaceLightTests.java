package com.sqc.nuclearsq.gametest;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.FaceLight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ContainmentFaceLightTests {
    private ContainmentFaceLightTests() {}

    @GameTest(template = "floor", batch = "containment_facelight", timeoutTicks = 1200)
    public static void containmentInsideIsDark(GameTestHelper h) {
        BigMachine c = BigMachine.CONTAINMENT;
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(c.id), h.absolutePos(new BlockPos(34, 120, 4)), Direction.SOUTH);
        for (BlockPos p : pl.footprint()) h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
        var blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException(blocked.size() + " Felder der Huelle belegt, z. B. " + blocked.get(0));
        MachineKits.place(h.getLevel(), pl);
        for (int x = 0; x < c.w; x++) for (int z = 0; z < c.d; z++)
            h.getLevel().setBlockAndUpdate(c.structure.world(pl.anchor(), pl.rot(), x, -1, z), Blocks.STONE.defaultBlockState());
        h.succeedWhen(() -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || !be.isFormed()) throw new GameTestAssertException("Huelle nicht aufgebaut");
            if (h.getTick() < 100) throw new GameTestAssertException("warte auf das Licht, Tick " + h.getTick());
            sealed(h, pl);
            face(h, pl, new int[] {1, 10, 15}, new int[] {2, 10, 15}, 0, "Innenwand West");
            face(h, pl, new int[] {29, 10, 15}, new int[] {28, 10, 15}, 0, "Innenwand Ost");
            face(h, pl, new int[] {15, 31, 15}, new int[] {15, 30, 15}, 0, "Dach innen (Dachoeffnung)");
            face(h, pl, new int[] {8, 31, 8}, new int[] {8, 30, 8}, 0, "Dach innen");
            face(h, pl, new int[] {0, 10, 15}, new int[] {-1, 10, 15}, 15, "Aussenwand West");
            face(h, pl, new int[] {15, 31, 15}, new int[] {15, 32, 15}, 15, "Dach aussen");
        });
    }

    private static void sealed(GameTestHelper h, MachineKits.Placement pl) {
        BigMachine c = BigMachine.CONTAINMENT;
        var lv = h.getLevel();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] {15, 10, 15});
        seen.add(BlockPos.asLong(15, 10, 15));
        int n = 0;
        while (!q.isEmpty()) {
            int[] v = q.poll();
            n++;
            BlockPos p = c.structure.world(pl.anchor(), pl.rot(), v[0], v[1], v[2]);
            int sky = lv.getBrightness(LightLayer.SKY, p);
            if (sky != 0) throw new GameTestAssertException("Himmelslicht " + sky + " im Innenraum bei Feld " + v[0] + "," + v[1] + "," + v[2]);
            for (Direction d : Direction.values()) {
                int[] w = {v[0] + d.getStepX(), v[1] + d.getStepY(), v[2] + d.getStepZ()};
                if (w[1] < 0) continue;
                if (w[0] < 0 || w[0] >= c.w || w[1] >= c.h || w[2] < 0 || w[2] >= c.d) {
                    throw new GameTestAssertException("Huelle undicht: Innenraum reicht bei Feld " + v[0] + "," + v[1] + "," + v[2] + " nach " + d + " hinaus");
                }
                if (!seen.add(BlockPos.asLong(w[0], w[1], w[2]))) continue;
                BlockPos wp = c.structure.world(pl.anchor(), pl.rot(), w[0], w[1], w[2]);
                BlockState st = lv.getBlockState(wp);
                if (st.getLightBlock(lv, wp) >= 15) continue;
                if (!st.isAir()) throw new GameTestAssertException("Block laesst Licht durch: " + st + " bei Feld " + w[0] + "," + w[1] + "," + w[2]);
                q.add(w);
            }
        }
        if (n < 15000) throw new GameTestAssertException("Innenraum zu klein: " + n + " Felder");
    }

    private static void face(GameTestHelper h, MachineKits.Placement pl, int[] wall, int[] front, int sky, String what) {
        BigMachine c = BigMachine.CONTAINMENT;
        BlockPos w = c.structure.world(pl.anchor(), pl.rot(), wall[0], wall[1], wall[2]);
        BlockPos f = c.structure.world(pl.anchor(), pl.rot(), front[0], front[1], front[2]);
        Vec3 n = Vec3.atLowerCornerOf(f.subtract(w));
        Vec3 m = Vec3.atCenterOf(w).add(n.scale(0.5));
        BlockPos s = FaceLight.sample(h.getLevel(), m.x, m.y, m.z, (float) n.x, (float) n.y, (float) n.z);
        if (!f.equals(s)) throw new GameTestAssertException(what + ": Licht von " + s + " statt " + f);
        int got = h.getLevel().getBrightness(LightLayer.SKY, s);
        if (got != sky) throw new GameTestAssertException(what + ": Himmelslicht " + got + " (erwartet " + sky + ")");
    }
}
