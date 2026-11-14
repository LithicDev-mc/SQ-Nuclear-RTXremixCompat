package com.sqc.nuclearsq.data.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.machine.client.TermKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class PanelDraw {
    public static final int FULL_BRIGHT = 0xF000F0;
    private static final float PX = 1 / 64f;

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Font font = Minecraft.getInstance().font;
    private float z;

    private PanelDraw(PoseStack pose, MultiBufferSource buffers) {
        this.pose = pose;
        this.buffers = buffers;
    }

    public static PanelDraw begin(PoseStack pose, MultiBufferSource buffers, Direction facing, float depth) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0.5, -0.5 + depth + 0.002);
        pose.scale(PX, -PX, PX);
        return new PanelDraw(pose, buffers);
    }

    public static PanelDraw at(PoseStack pose, MultiBufferSource buffers, double x, double y, double z, float yRot) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.scale(PX, -PX, PX);
        return new PanelDraw(pose, buffers);
    }

    public void end() {
        pose.popPose();
    }

    public void push(float x, float y, float scale) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
    }

    public void pop() {
        pose.popPose();
    }

    public void layer() {
        z += 0.05f;
    }

    public void fill(float x0, float y0, float x1, float y1, int argb) {
        argb = TermKit.col(argb);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        Matrix4f m = pose.last().pose();
        float zz = z;
        vc.addVertex(m, x0, y0, zz).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, zz).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, zz).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y0, zz).setColor(argb).setLight(FULL_BRIGHT);
    }

    public void needle(float cx, float cy, float deg, float len, float half, int argb) {
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        fill(-half, -len, half, half, argb);
        pose.popPose();
    }

    public void tick(float cx, float cy, float deg, float r0, float r1, float half, int argb) {
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        fill(-half, -r1, half, -r0, argb);
        pose.popPose();
    }

    public int width(Component c) {
        return font.width(c);
    }

    public void text(Component c, float x, float y, float scale, int argb) {
        argb = TermKit.tcol(argb);
        pose.pushPose();
        pose.translate(x, y, z + 0.02f);
        pose.scale(scale, scale, 1);
        font.drawInBatch(c, 0, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
    }

    public float textFit(Component c, float x, float y, float scale, float maxW, int argb) {
        int max = (int) (maxW / scale);
        if (font.width(c) <= max) {
            text(c, x, y, scale, argb);
            return font.width(c) * scale;
        }
        argb = TermKit.tcol(argb);
        FormattedCharSequence cut = net.minecraft.locale.Language.getInstance().getVisualOrder(
                font.substrByWidth(c, Math.max(0, max - font.width("..."))));
        pose.pushPose();
        pose.translate(x, y, z + 0.02f);
        pose.scale(scale, scale, 1);
        float w = font.drawInBatch(cut, 0, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        font.drawInBatch("...", w, 0, argb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
        return maxW;
    }

    public void center(Component c, float cx, float y, float scale, float maxW, int argb) {
        float w = Math.min(font.width(c) * scale, maxW);
        textFit(c, cx - w / 2, y, scale, maxW, argb);
    }

    public void right(Component c, float xr, float y, float scale, int argb) {
        text(c, xr - font.width(c) * scale, y, scale, argb);
    }
}
