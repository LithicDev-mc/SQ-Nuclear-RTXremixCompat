package com.sqc.nuclearsq.machine.big.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

final class ClippedModel {
    private ClippedModel() {}

    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};
    private static final int X = 0, Y = 1, Z = 2, U = 3, V = 4;
    private static final int STRIDE = 8;

    static void render(PoseStack.Pose pose, VertexConsumer vc, BlockState st, BakedModel model, float minY, int light,
                       int overlay) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource rand = RandomSource.create();
        for (Direction side : SIDES) {
            rand.setSeed(42L);
            for (BakedQuad q : model.getQuads(st, side, rand, ModelData.EMPTY, null)) {
                float shade = mc.level == null ? 1f : mc.level.getShade(q.getDirection(), q.isShade());
                quad(pose, vc, q, minY, shade, light, overlay);
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, BakedQuad q, float minY, float shade, int light,
                             int overlay) {
        int[] d = q.getVertices();
        List<float[]> poly = new ArrayList<>(5);
        for (int i = 0; i < 4; i++) {
            int o = i * STRIDE;
            poly.add(new float[] {Float.intBitsToFloat(d[o]), Float.intBitsToFloat(d[o + 1]), Float.intBitsToFloat(d[o + 2]),
                    Float.intBitsToFloat(d[o + 4]), Float.intBitsToFloat(d[o + 5])});
        }
        poly = clip(poly, minY);
        if (poly.size() < 3) return;
        Direction dir = q.getDirection();
        emit(pose, vc, poly.get(0), poly.get(1), poly.get(2), poly.get(Math.min(3, poly.size() - 1)), dir, shade, light, overlay);
        if (poly.size() == 5) emit(pose, vc, poly.get(0), poly.get(3), poly.get(4), poly.get(4), dir, shade, light, overlay);
    }

    private static List<float[]> clip(List<float[]> in, float minY) {
        boolean all = true;
        for (float[] p : in) all &= p[Y] >= minY;
        if (all) return in;
        List<float[]> out = new ArrayList<>(5);
        for (int i = 0; i < in.size(); i++) {
            float[] a = in.get(i), b = in.get((i + 1) % in.size());
            boolean ina = a[Y] >= minY, inb = b[Y] >= minY;
            if (ina) out.add(a);
            if (ina != inb) {
                float t = (minY - a[Y]) / (b[Y] - a[Y]);
                float[] p = new float[5];
                for (int k = 0; k < 5; k++) p[k] = a[k] + (b[k] - a[k]) * t;
                p[Y] = minY;
                out.add(p);
            }
        }
        return out;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] a, float[] b, float[] c, float[] e,
                             Direction dir, float shade, int light, int overlay) {
        for (float[] p : new float[][] {a, b, c, e}) {
            vc.addVertex(pose, p[X], p[Y], p[Z]).setColor(shade, shade, shade, 1f).setUv(p[U], p[V]).setOverlay(overlay)
                    .setLight(light).setNormal(pose, dir.getStepX(), dir.getStepY(), dir.getStepZ());
        }
    }
}
