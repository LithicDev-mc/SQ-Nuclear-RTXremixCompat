package com.sqc.nuclearsq.storage.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.client.PanelDraw;
import com.sqc.nuclearsq.storage.StorageFormat;
import com.sqc.nuclearsq.storage.StorageMonitorBlock;
import com.sqc.nuclearsq.storage.StorageMonitorBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class StorageMonitorRenderer implements BlockEntityRenderer<StorageMonitorBlockEntity> {
    private static final int GLASS = 0xFF040A06, FRAME = 0xFF1C2226, EDGE = 0xFF0E1A12, GREEN = 0xFF41FF7A, MID = 0xFF22B04E,
            DIM = 0xFF136B30, AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, BAR_BG = 0xFF0B3319;
    private static final float ROW = 11, HEAD = 24;

    public StorageMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(StorageMonitorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(StorageMonitorBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).inflate(StorageMonitorBlock.MAX_W, StorageMonitorBlock.MAX_H, StorageMonitorBlock.MAX_W);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.storage.monitor." + key, args);
    }

    private static int barColor(float f, float warn) {
        return f >= warn ? RED : f >= warn - 0.15f ? AMBER : MID;
    }

    @Override
    public void render(StorageMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        if (be.getLevel() == null) return;
        StorageMonitorBlock.Layout l = StorageMonitorBlock.layout(be.getLevel(), be.getBlockPos());
        if (!l.origin().equals(be.getBlockPos())) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 3 / 16f);
        float cw = l.w() * 64, ch = l.h() * 64, right = cw - 6;
        d.push(0, -(l.h() - 1) * 64, 1);
        d.fill(1, 1, cw - 1, ch - 1, FRAME);
        d.layer();
        d.fill(2.5f, 2.5f, cw - 2.5f, ch - 2.5f, GLASS);
        d.layer();
        List<StorageMonitorBlockEntity.Row> rows = be.sorted(false);
        float warn = be.warnFrom();
        if (be.rows().isEmpty()) {
            d.center(t("no_racks"), cw / 2, ch / 2 - 3, 0.3f, cw - 10, AMBER);
            d.pop();
            d.end();
            return;
        }
        long used = be.used(), cap = be.capacity();
        float f = cap <= 0 ? 0 : Math.min(1f, used / (float) cap);
        d.textFit(t("title"), 6, 6, 0.3f, right - 6, GREEN);
        int on = be.online(), all = be.rows().size();
        d.right(t("racks", on, all), right, 6, 0.26f, on < all ? AMBER : MID);
        d.textFit(t("total", StorageFormat.count(used), StorageFormat.count(cap)), 6, 12.5f, 0.26f, (right - 6) * 0.7f, GREEN);
        d.right(Component.literal(Math.round(f * 100) + " %"), right, 12.5f, 0.26f, MID);
        d.fill(6, 18.5f, right, 20.5f, BAR_BG);
        d.layer();
        d.fill(6, 18.5f, 6 + (right - 6) * f, 20.5f, barColor(f, warn));
        d.fill(4, HEAD - 1, cw - 4, HEAD - 0.6f, EDGE);
        int perPage = Math.max(1, (int) ((ch - HEAD - 4) / ROW));
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        int page = (int) (be.getLevel().getGameTime() / be.pageTicks() % pages);
        float y = HEAD + 2;
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            StorageMonitorBlockEntity.Row r = rows.get(i);
            float rf = r.frac();
            d.fill(6, y + 1, 9, y + 4, r.online() ? GREEN : RED);
            Component name = Component.literal((i + 1) + " ").append(Component.translatable("gui.nuclearsq.storage.rack." + r.machine()));
            Component amount = r.drives() == 0 ? t("empty") : Component.literal(StorageFormat.count(r.used()) + " / " + StorageFormat.count(r.capacity()));
            float amountW = d.width(amount) * 0.24f;
            d.textFit(name, 11, y, 0.24f, Math.max(8, right - 11 - amountW - 3), r.online() ? GREEN : DIM);
            d.right(amount, right, y, 0.24f, r.drives() == 0 ? DIM : MID);
            d.fill(11, y + 6, right, y + 7.5f, BAR_BG);
            d.layer();
            if (rf > 0) d.fill(11, y + 6, 11 + (right - 11) * rf, y + 7.5f, barColor(rf, warn));
            y += ROW;
        }
        if (pages > 1) d.right(Component.literal((page + 1) + "/" + pages), right, ch - 7, 0.22f, DIM);
        d.pop();
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
