package com.sqc.nuclearsq.data.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.LogEntry;
import com.sqc.nuclearsq.data.LogbookBlock;
import com.sqc.nuclearsq.data.LogbookBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class LogbookRenderer implements BlockEntityRenderer<LogbookBlockEntity> {
    private static final int GLASS = 0xFF040A06, FRAME = 0xFF1C2226, EDGE = 0xFF0E1A12, GREEN = 0xFF41FF7A, MID = 0xFF22B04E,
            DIM = 0xFF136B30, AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E;
    private static final float HEAD = 13;
    private static final float[] SCALES = {0.24f, 0.30f, 0.38f, 0.48f, 0.62f};

    public LogbookRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(LogbookBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LogbookBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).inflate(LogbookBlock.MAX_W, LogbookBlock.MAX_H, LogbookBlock.MAX_W);
    }

    private static int color(int severity) {
        return switch (severity) {
            case LogEntry.ALARM -> RED;
            case LogEntry.WARN -> AMBER;
            case LogEntry.CLEAR -> GREEN;
            default -> MID;
        };
    }

    @Override
    public void render(LogbookBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        if (be.getLevel() == null) return;
        LogbookBlock.Layout l = LogbookBlock.layout(be.getLevel(), be.getBlockPos());
        if (!l.origin().equals(be.getBlockPos())) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 3 / 16f);
        float cw = l.w() * 64, ch = l.h() * 64, right = cw - 5;
        d.push(0, -(l.h() - 1) * 64, 1);
        d.fill(1, 1, cw - 1, ch - 1, FRAME);
        d.layer();
        d.fill(2.5f, 2.5f, cw - 2.5f, ch - 2.5f, GLASS);
        d.layer();
        float scale = SCALES[Math.max(0, Math.min(SCALES.length - 1, be.textSize()))], row = scale * 29;
        List<LogEntry> all = be.entries();
        d.text(Component.translatable("gui.nuclearsq.logbook.title"), 5, 5, 0.3f, GREEN);
        d.right(Component.translatable("gui.nuclearsq.logbook.count", all.size()), right, 5.5f, 0.24f, MID);
        d.fill(4, HEAD + 1, cw - 4, HEAD + 1.4f, EDGE);
        if (all.isEmpty()) {
            d.center(Component.translatable("gui.nuclearsq.logbook.empty_wall"), cw / 2, ch / 2 - 2, scale, cw - 10, DIM);
            d.pop();
            d.end();
            return;
        }
        int rows = Math.max(1, (int) ((ch - HEAD - 6) / row));
        int from = Math.max(0, all.size() - rows);
        float headW = d.width(Component.literal("T99 00:00")) * scale + 3;
        float y = ch - 5 - row * (all.size() - from);
        for (int i = from; i < all.size(); i++, y += row) {
            LogEntry e = all.get(i);
            int c = color(e.severity());
            d.text(Component.literal("T" + LogEntry.day(e.dayTime()) + " " + LogEntry.clock(e.dayTime())), 5, y, scale, DIM);
            d.textFit(e.text(), 5 + headW, y, scale, right - 5 - headW, c);
        }
        d.pop();
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
