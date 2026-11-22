package com.sqc.nuclearsq.apps.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.apps.AppMonitorBlock;
import com.sqc.nuclearsq.apps.AuctionMonitorBlockEntity;
import com.sqc.nuclearsq.data.client.PanelDraw;
import com.sqc.nuclearsq.economy.BankNet;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class AuctionMonitorRenderer implements BlockEntityRenderer<AuctionMonitorBlockEntity> {
    private static final int FRAME = 0xFF1C2226, GLASS = 0xFF06090E, EDGE = 0xFF16304A, CYAN = 0xFF5FE3FF, DIM = 0xFF3C6E8C,
            GOLD = 0xFFE8A33D, RED = 0xFFFF5A4E, TEXT = 0xFFDDDDE0;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public AuctionMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public AABB getRenderBoundingBox(AuctionMonitorBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    public static String left(long ticks) {
        long min = (ticks + 1199) / 1200;
        return min >= 60 ? (min / 60) + "h" : min + "m";
    }

    @Override
    public void render(AuctionMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (be.getLevel() == null || com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(AppMonitorBlock.FACING), AppMonitorBlock.DEPTH);
        d.fill(1, 1, 63, 63, FRAME);
        d.layer();
        d.fill(2.5f, 2.5f, 61.5f, 61.5f, GLASS);
        d.layer();
        d.text(Component.translatable("gui.nuclearsq.app_auction.wall_title"), 5, 5, 0.3f, GOLD);
        d.right(Component.literal(LocalTime.now().format(CLOCK)), 59, 5.5f, 0.24f, DIM);
        d.fill(4, 13, 60, 13.4f, EDGE);
        if (be.link() != BankNet.Link.ONLINE) {
            d.center(Component.translatable("gui.nuclearsq.app_login.wall_offline"), 32, 28, 0.36f, 54, RED);
            d.center(Component.translatable("gui.nuclearsq.app_login.wall_offline_hint"), 32, 38, 0.24f, 54, DIM);
            d.end();
            return;
        }
        d.text(Component.translatable("gui.nuclearsq.app_auction.wall_count", be.count()), 5, 16, 0.26f, CYAN);
        float y = 24;
        for (AuctionMonitorBlockEntity.Row r : be.rows()) {
            d.fill(4, y - 1, 60, y + 8, 0xFF0E151C);
            d.layer();
            d.textFit(r.stack().getHoverName(), 5, y, 0.24f, 34, TEXT);
            d.right(Component.literal(String.valueOf(r.price())), 59, y, 0.24f, GOLD);
            d.right(Component.literal(left(r.left())), 59, y + 4, 0.2f, DIM);
            y += 10;
        }
        if (be.rows().isEmpty()) d.center(Component.translatable("gui.nuclearsq.app_auction.wall_empty"), 32, 30, 0.26f, 54, DIM);
        d.center(Component.translatable("gui.nuclearsq.app_auction.wall_hint"), 32, 56, 0.22f, 54, DIM);
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
