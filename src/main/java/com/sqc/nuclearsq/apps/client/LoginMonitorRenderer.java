package com.sqc.nuclearsq.apps.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.apps.AppMonitorBlock;
import com.sqc.nuclearsq.apps.LoginMonitorBlockEntity;
import com.sqc.nuclearsq.data.client.PanelDraw;
import com.sqc.nuclearsq.economy.BankNet;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public class LoginMonitorRenderer implements BlockEntityRenderer<LoginMonitorBlockEntity> {
    private static final int FRAME = 0xFF1C2226, GLASS = 0xFF06090E, EDGE = 0xFF16304A, CYAN = 0xFF5FE3FF, DIM = 0xFF3C6E8C,
            GREEN = 0xFF41FF7A, AMBER = 0xFFFFB23A, RED = 0xFFFF5A4E, BODY = 0xFF2A3440;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public LoginMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public AABB getRenderBoundingBox(LoginMonitorBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public void render(LoginMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (be.getLevel() == null || com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(AppMonitorBlock.FACING), AppMonitorBlock.DEPTH);
        d.fill(1, 1, 63, 63, FRAME);
        d.layer();
        d.fill(2.5f, 2.5f, 61.5f, 61.5f, GLASS);
        d.layer();
        d.text(Component.translatable("gui.nuclearsq.app_login.wall_title"), 5, 5, 0.3f, CYAN);
        d.right(Component.literal(LocalTime.now().format(CLOCK)), 59, 5.5f, 0.24f, DIM);
        d.fill(4, 13, 60, 13.4f, EDGE);

        boolean online = be.link() == BankNet.Link.ONLINE;
        boolean in = ClientAppSession.loggedIn();
        int lock = in ? GREEN : online ? AMBER : RED;
        d.fill(23, 27, 41, 40, BODY);
        d.layer();
        d.fill(24, 28, 40, 39, lock);
        d.fill(31, 31, 33, 36, GLASS);
        float lift = in ? 4 : 0;
        d.fill(25, 18 - lift, 27, 27 - (in ? 3 : 0), lock);
        d.fill(37, 18 - lift, 39, 27, lock);
        d.fill(25, 18 - lift, 39, 20 - lift, lock);
        d.layer();

        Component status, sub;
        int col;
        if (in) {
            status = Component.translatable("gui.nuclearsq.app_login.wall_in");
            sub = Component.literal(ClientAppSession.name());
            col = GREEN;
        } else if (!online) {
            status = Component.translatable("gui.nuclearsq.app_login.wall_offline");
            sub = Component.translatable("gui.nuclearsq.app_login.wall_offline_hint");
            col = RED;
        } else {
            status = Component.translatable("gui.nuclearsq.app_login.wall_out");
            sub = Component.translatable("gui.nuclearsq.app_login.wall_hint");
            col = AMBER;
        }
        d.center(status, 32, 45, 0.3f, 54, col);
        d.center(sub, 32, 53, 0.24f, 54, in ? CYAN : DIM);
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
