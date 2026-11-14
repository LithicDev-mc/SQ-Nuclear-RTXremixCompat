package com.sqc.nuclearsq.data.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.ControlWallBlock;
import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataSource;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class ControlWallRenderer implements BlockEntityRenderer<ControlWallBlockEntity> {
    private static final int GLASS = 0xFF040A06, EDGE = 0xFF0E1A12, GREEN = 0xFF41FF7A, MID = 0xFF22B04E, DIM = 0xFF136B30;
    private static final int AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, GREY = 0xFF8792A0;

    public ControlWallRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(ControlWallBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ControlWallBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).inflate(ControlWallBlock.MAX_W, ControlWallBlock.MAX_H, ControlWallBlock.MAX_W);
    }

    @Override
    public void render(ControlWallBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        if (be.getLevel() == null) return;
        ControlWallBlock.Layout l = ControlWallBlock.layout(be.getLevel(), be.getBlockPos());
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 1f);
        if (l == null) {
            d.fill(3, 3, 61, 61, GLASS);
            d.layer();
            d.center(Component.translatable("data.nuclearsq.screen.wall_size"), 32, 24, 0.3f, 54, AMBER);
            d.center(Component.literal(ControlWallBlock.MIN_W + "x" + ControlWallBlock.MIN_H + " - " + ControlWallBlock.MAX_W + "x"
                    + ControlWallBlock.MAX_H), 32, 34, 0.3f, 54, DIM);
            d.end();
            return;
        }
        if (!l.origin().equals(be.getBlockPos())) {
            d.end();
            return;
        }
        float cw = l.w() * 64, ch = l.h() * 64;
        d.push(0, -(l.h() - 1) * 64, 1);
        d.fill(2, 2, cw - 2, ch - 2, GLASS);
        d.layer();
        var tiles = be.tiles();
        long time = be.getLevel().getGameTime();
        ControlWallBlock.Grid g = ControlWallBlock.grid(l.w(), l.h(), tiles.size());
        float tw = cw / g.cols(), th = ch / g.rows();
        int pages = Math.max(1, (tiles.size() + g.tiles() - 1) / g.tiles());
        if (tiles.isEmpty()) d.textFit(Component.translatable("data.nuclearsq.screen.no_signal"), 6, 6, 0.32f, cw - 12, AMBER);
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (c > 0) d.fill(c * tw - 0.3f, 4, c * tw + 0.3f, ch - 4, EDGE);
                if (r > 0 && c == 0) d.fill(4, r * th - 0.3f, cw - 4, r * th + 0.3f, EDGE);
                int i = ControlWallBlock.tileIndex(g, c, r, tiles.size(), time);
                if (i < 0) continue;
                float s = Math.min(tw, th) / 64f;
                d.push(c * tw, r * th + (th - 64 * s) / 2, s);
                tile(d, tiles.get(i), tw / s, time);
                d.pop();
            }
        }
        if (pages > 1) d.right(Component.literal((time / ControlWallBlock.PAGE_TICKS % pages + 1) + "/" + pages), cw - 4, 3, 0.25f, DIM);
        d.pop();
        d.end();
    }

    private static void tile(PanelDraw d, ControlWallBlockEntity.Tile t, float iw, long time) {
        int sc = DataEntry.color(t.state());
        if (t.state() == DataSource.ALARM && (time / 10) % 2 == 0) {
            d.fill(4, 4, iw - 4, 5.5f, RED);
            d.fill(4, 58.5f, iw - 4, 60, RED);
        }
        float right = iw - 6;
        d.fill(6, 8, 10, 12, sc);
        d.textFit(t.name(), 13, 8, 0.36f, right - 13, GREEN);
        d.textFit(t.stateText(), 13, 14.5f, 0.28f, right - 13, sc == GREY ? MID : sc);
        d.fill(6, 20, right, 20.4f, DIM);
        float y = 23;
        for (ControlWallBlockEntity.Line ln : t.lines()) {
            d.textFit(ln.label(), 6, y, 0.26f, (right - 6) * 0.55f, MID);
            d.right(Component.literal(ln.text()), right, y, 0.28f, GREEN);
            if (ln.scaled()) {
                d.fill(6, y + 7, right, y + 8.5f, 0xFF0B3319);
                d.layer();
                int c = ln.frac() >= 0.9f ? RED : ln.frac() >= 0.75f ? AMBER : MID;
                d.fill(6, y + 7, 6 + (right - 6) * ln.frac(), y + 8.5f, c);
            }
            y += 12;
        }
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
