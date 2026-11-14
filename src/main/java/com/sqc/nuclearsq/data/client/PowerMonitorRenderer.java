package com.sqc.nuclearsq.data.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.PowerMonitorBlock;
import com.sqc.nuclearsq.data.PowerMonitorBlockEntity;
import com.sqc.nuclearsq.data.PowerSample;
import com.sqc.nuclearsq.data.PowerStats;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class PowerMonitorRenderer implements BlockEntityRenderer<PowerMonitorBlockEntity> {
    private static final int GLASS = 0xFF040A06, FRAME = 0xFF1C2226, EDGE = 0xFF0E1A12, GREEN = 0xFF41FF7A,
            DIM = 0xFF136B30, AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, CYAN = 0xFF5FE3FF, BAR_DIM = 0xFF0B3319;
    private static final float HEAD = 13;

    public PowerMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(PowerMonitorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PowerMonitorBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).inflate(PowerMonitorBlock.MAX_W, PowerMonitorBlock.MAX_H, PowerMonitorBlock.MAX_W);
    }

    @Override
    public void render(PowerMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        if (be.getLevel() == null) return;
        PowerMonitorBlock.Layout l = PowerMonitorBlock.layout(be.getLevel(), be.getBlockPos());
        if (!l.origin().equals(be.getBlockPos())) return;
        clamps(be, l, pose, buffers, light);
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 3 / 16f);
        float cw = l.w() * 64, ch = l.h() * 64, left = 5, right = cw - 5;
        d.push(0, -(l.h() - 1) * 64, 1);
        d.fill(1, 1, cw - 1, ch - 1, FRAME);
        d.layer();
        d.fill(2.5f, 2.5f, cw - 2.5f, ch - 2.5f, GLASS);
        d.layer();
        List<PowerSample> list = be.shown();
        PowerSample now = list.isEmpty() ? PowerSample.ZERO : list.get(list.size() - 1);
        boolean tall = ch >= 100;
        float titleScale = l.w() >= 2 ? 0.3f : 0.24f;
        d.text(Component.translatable("gui.nuclearsq.power.wall_title"), left, 5, titleScale, GREEN);
        long bal = now.balance();
        d.right(Component.literal((bal > 0 ? "+" : "") + PowerStats.compact(bal)), right, 5.5f, 0.24f, bal >= 0 ? GREEN : RED);
        d.fill(4, HEAD + 1, cw - 4, HEAD + 1.4f, EDGE);

        float foot = tall ? 22 : 6;
        float gy0 = HEAD + 4, gy1 = ch - foot, gh = Math.max(4, gy1 - gy0), gw = right - left;
        d.fill(left, gy1, right, gy1 + 0.5f, DIM);
        if (list.isEmpty()) {
            d.center(Component.translatable("gui.nuclearsq.power.wait"), cw / 2, ch / 2 - 2, 0.3f, cw - 10, DIM);
        } else {
            long top = PowerMonitorScreen.niceMax(PowerStats.max(list));
            int max = PowerMonitorBlockEntity.WORLD_SAMPLES, n = list.size();
            float step = gw / max;
            d.layer();
            for (int i = 0; i < n; i++) {
                PowerSample s = list.get(i);
                float x1 = right - (n - 1 - i) * step, x0 = x1 - Math.max(0.6f, step - 0.4f);
                float ph = gh * Math.min(1f, s.prod() / (float) top);
                float ch2 = gh * Math.min(1f, s.cons() / (float) top);
                if (ph > 0.3f) d.fill(x0, gy1 - ph, x1, gy1, BAR_DIM);
                if (ph > 0.3f) d.fill(x0, gy1 - ph, x1, gy1 - ph + 0.9f, GREEN);
                if (ch2 > 0.3f) d.fill(x0, gy1 - ch2 - 0.4f, x1, gy1 - ch2 + 0.6f, AMBER);
            }
            d.layer();
            d.text(Component.literal(PowerStats.compact(top)), left + 1, gy0 - 1, 0.2f, DIM);
        }
        if (tall) {
            float fy = ch - foot + 3, s = 0.26f;
            d.text(Component.literal(PowerStats.compact(now.prod())), left, fy, s, GREEN);
            d.center(Component.literal(PowerStats.compact(now.cons())), cw / 2, fy, s, cw / 3f, AMBER);
            if (now.capacity() > 0) {
                d.right(Component.literal(Math.round(now.fill() * 100) + "%"), right, fy, s, CYAN);
                d.fill(left, ch - 8, right, ch - 5, EDGE);
                d.fill(left, ch - 8, left + (right - left) * now.fill(), ch - 5, CYAN);
            } else {
                d.right(Component.translatable("gui.nuclearsq.power.no_storage"), right, fy, s, DIM);
            }
        }
        d.pop();
        d.end();
    }

    private static void clamps(PowerMonitorBlockEntity be, PowerMonitorBlock.Layout l, PoseStack pose, MultiBufferSource buffers, int light) {
        var level = be.getLevel();
        net.minecraft.core.Direction f = be.getBlockState().getValue(DataPanelBlock.FACING);
        for (BlockPos c : l.cells()) {
            BlockPos back = c.relative(f.getOpposite());
            var st = level.getBlockState(back);
            if (!(st.getBlock() instanceof com.sqc.nuclearsq.transport.CableBlock)
                    && !(level.getBlockEntity(back) instanceof com.sqc.nuclearsq.transport.ConduitBlockEntity ce && ce.energySide(null) != null)) continue;
            pose.pushPose();
            pose.translate(c.getX() - be.getBlockPos().getX() + 0.5, c.getY() - be.getBlockPos().getY() + 0.5, c.getZ() - be.getBlockPos().getZ() + 0.5);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-f.toYRot()));
            pose.translate(-0.5, -0.5, -0.5);
            box(pose, buffers, light, 7, 7, -6.5f, 9, 9, 3.2f);
            box(pose, buffers, light, 5.5f, 5.5f, -9.5f, 10.5f, 10.5f, -6.5f);
            pose.popPose();
        }
    }

    private static void box(PoseStack pose, MultiBufferSource buffers, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
        var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.textBackground());
        org.joml.Matrix4f m = pose.last().pose();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, g = z1 / 16;
        float[][] q = {
            {a, b, c, a, e, c, d, e, c, d, b, c}, {a, b, g, d, b, g, d, e, g, a, e, g},
            {a, b, c, a, b, g, a, e, g, a, e, c}, {d, b, c, d, e, c, d, e, g, d, b, g},
            {a, e, c, a, e, g, d, e, g, d, e, c}, {a, b, c, d, b, c, d, b, g, a, b, g}};
        int[] col = {0xFF3A444A, 0xFF3A444A, 0xFF2A3338, 0xFF2A3338, 0xFF4C5860, 0xFF1C2226};
        for (int i = 0; i < 6; i++) {
            for (int v = 0; v < 12; v += 3) vc.addVertex(m, q[i][v], q[i][v + 1], q[i][v + 2]).setColor(col[i]).setLight(light);
        }
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
