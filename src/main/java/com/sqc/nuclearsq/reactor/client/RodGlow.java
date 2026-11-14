package com.sqc.nuclearsq.reactor.client;

import net.minecraft.world.inventory.InventoryMenu;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.reactor.Cherenkov;
import com.sqc.nuclearsq.reactor.FuelRodBlock;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class RodGlow {
    private RodGlow() {}

    public static final ModelResourceLocation GLOW = model("fuel_rod_glow_sqc");
    static final int NEAR = 1, FAR = 3;

    private static final int[][] OFFS;
    private static final float[] DIST;

    static {
        List<int[]> l = new ArrayList<>();
        for (int x = -FAR; x <= FAR; x++)
            for (int y = -FAR; y <= FAR; y++)
                for (int z = -FAR; z <= FAR; z++)
                    if (x * x + y * y + z * z <= FAR * FAR) l.add(new int[] {x, y, z});
        l.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1] + a[2] * a[2], b[0] * b[0] + b[1] * b[1] + b[2] * b[2]));
        OFFS = l.toArray(new int[0][]);
        DIST = new float[OFFS.length];
        for (int i = 0; i < OFFS.length; i++) DIST[i] = (float) Math.sqrt(OFFS[i][0] * OFFS[i][0] + OFFS[i][1] * OFFS[i][1] + OFFS[i][2] * OFFS[i][2]);
    }

    private static final Long2FloatOpenHashMap WATER = new Long2FloatOpenHashMap();
    private static final Long2LongOpenHashMap CHECKED = new Long2LongOpenHashMap();
    private static Level cacheLevel;
    private static final RandomSource RAND = RandomSource.create();
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();
    private static final java.util.Map<BakedModel, BakedQuad[]> QUADS = new java.util.IdentityHashMap<>();

    public static void clearCache() {
        WATER.clear();
        CHECKED.clear();
        QUADS.clear();
        cacheLevel = null;
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + name));
    }

    static float water(Level lv, BlockPos p) {
        if (lv != cacheLevel || WATER.size() > 8192) {
            WATER.clear();
            CHECKED.clear();
            cacheLevel = lv;
        }
        long key = p.asLong(), now = lv.getGameTime();
        if (CHECKED.containsKey(key) && now - CHECKED.get(key) < 20 + (key * 0x9E3779B97F4A7C15L >>> 59 & 15)) return WATER.get(key);
        float d = -1;
        BlockPos.MutableBlockPos q = MUT;
        for (int i = 0; i < OFFS.length; i++) {
            q.set(p.getX() + OFFS[i][0], p.getY() + OFFS[i][1], p.getZ() + OFFS[i][2]);
            if (lv.getFluidState(q).is(FluidTags.WATER)) {
                d = DIST[i];
                break;
            }
        }
        float lin = d < 0 ? 0f : d <= NEAR ? 1f : Math.max(0f, 1f - (d - NEAR) / (FAR - NEAR));
        float f = lin * lin;
        WATER.put(key, f);
        CHECKED.put(key, now);
        return f;
    }

    public static void segment(PoseStack pose, MultiBufferSource buffers, Level lv, BlockPos at, int charge, int cherenkov) {
        segment(pose, buffers, lv, at, charge, cherenkov, 0f);
    }

    public static void segment(PoseStack pose, MultiBufferSource buffers, Level lv, BlockPos at, int charge, int cherenkov, float clip) {
        if (charge <= 0 || lv == null) return;
        float c = charge / 4f, w = water(lv, at);
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS));
        draw(pose, vc, GLOW, Math.min(1f, c * (0.6f + 0.5f * w)));
        if (w > 0f && cherenkov > 0) CherenkovGlow.draw(pose, buffers, (0.35f + 0.65f * c) * w * cherenkov / Cherenkov.MAX, clip);
    }

    public static void rod(PoseStack pose, MultiBufferSource buffers, Level lv, BlockPos head, float drop, int cherenkov) {
        rod(pose, buffers, lv, head, drop, cherenkov, 0f);
    }

    public static void rod(PoseStack pose, MultiBufferSource buffers, Level lv, BlockPos head, float drop, int cherenkov, float clip) {
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            BlockPos real = head.below(i);
            BlockState st = lv.getBlockState(real);
            if (!st.hasProperty(FuelRodBlock.CHARGE)) continue;
            pose.pushPose();
            pose.translate(0f, -i, 0f);
            segment(pose, buffers, lv, BlockPos.containing(real.getX() + 0.5, real.getY() + 0.5 - drop, real.getZ() + 0.5),
                    st.getValue(FuelRodBlock.CHARGE), cherenkov, clip);
            pose.popPose();
        }
    }

    private static void draw(PoseStack pose, VertexConsumer vc, ModelResourceLocation loc, float k) {
        if (k <= 0.01f) return;
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(loc);
        BakedQuad[] quads = QUADS.get(m);
        if (quads == null) {
            List<BakedQuad> all = new ArrayList<>(m.getQuads(null, null, RAND, ModelData.EMPTY, null));
            for (Direction d : Direction.values()) all.addAll(m.getQuads(null, d, RAND, ModelData.EMPTY, null));
            QUADS.put(m, quads = all.toArray(new BakedQuad[0]));
        }
        for (BakedQuad q : quads) quad(pose, vc, q, k);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, BakedQuad q, float k) {
        vc.putBulkData(pose.last(), q, k, k, k, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
}
