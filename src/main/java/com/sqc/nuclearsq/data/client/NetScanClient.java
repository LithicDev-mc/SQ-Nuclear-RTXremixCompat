package com.sqc.nuclearsq.data.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.NetScanPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NetScanClient {
    private NetScanClient() {}

    private static final long TIMEOUT_MS = 2500L;
    private static final double LABEL_RANGE = 72;

    private static volatile Scan last;

    private record Scan(NetScanPayload p, BlockPos[] cables, int[] links, long at) {}

    public static void receive(NetScanPayload p) {
        if (p.isEmpty()) {
            last = null;
            return;
        }
        BlockPos[] cables = new BlockPos[p.cables().length];
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < cables.length; i++) {
            cables[i] = BlockPos.of(p.cables()[i]);
            set.add(p.cables()[i]);
        }
        java.util.ArrayList<Integer> links = new java.util.ArrayList<>();
        for (int i = 0; i < cables.length; i++) {
            for (Direction d : new Direction[] {Direction.EAST, Direction.UP, Direction.SOUTH}) {
                if (set.contains(cables[i].relative(d).asLong())) {
                    links.add(i);
                    links.add(d.ordinal());
                }
            }
        }
        int[] l = new int[links.size()];
        for (int i = 0; i < l.length; i++) l[i] = links.get(i);
        last = new Scan(p, cables, l, System.currentTimeMillis());
    }

    static int stateColor(int state) {
        return switch (state) {
            case DataSource.OK -> 0x45E06A;
            case DataSource.WARN -> 0xFFD23F;
            case DataSource.ALARM -> 0xFF4545;
            default -> 0x9AA3AD;
        };
    }

    private static void line(VertexConsumer c, PoseStack.Pose pose, Vec3 a, Vec3 b, int r, int g, int bl, int al) {
        float nx = (float) (b.x - a.x), ny = (float) (b.y - a.y), nz = (float) (b.z - a.z);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) { nx /= len; ny /= len; nz /= len; }
        c.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al).setNormal(pose, nx, ny, nz);
        c.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al).setNormal(pose, nx, ny, nz);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Scan s = last;
        if (s == null || System.currentTimeMillis() - s.at > TIMEOUT_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(NetScanRenderTypes.LINES);
        float pulse = 0.75f + 0.25f * Mth.sin((System.currentTimeMillis() % 2000L) / 2000f * Mth.TWO_PI);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos c : s.cables) {
            LevelRenderer.renderLineBox(ps, lines, new AABB(c).deflate(0.34), 0.15f, 0.85f, 1.0f, pulse);
        }
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < s.links.length; i += 2) {
            BlockPos a = s.cables[s.links[i]];
            Direction d = Direction.values()[s.links[i + 1]];
            Vec3 from = Vec3.atCenterOf(a), to = Vec3.atCenterOf(a.relative(d));
            line(lines, pose, from, to, 38, 217, 255, (int) (255 * pulse));
        }
        for (long d : s.p.devices()) {
            LevelRenderer.renderLineBox(ps, lines, new AABB(BlockPos.of(d)).inflate(0.02), 1f, 1f, 1f, 0.95f);
        }
        for (NetScanPayload.Machine m : s.p.machines()) {
            int col = stateColor(m.state());
            float r = (col >> 16 & 255) / 255f, g = (col >> 8 & 255) / 255f, b = (col & 255) / 255f;
            AABB box = new AABB(m.min().getX(), m.min().getY(), m.min().getZ(), m.max().getX() + 1, m.max().getY() + 1, m.max().getZ() + 1);
            LevelRenderer.renderLineBox(ps, lines, box.inflate(0.03), r, g, b, 1f);
            LevelRenderer.renderLineBox(ps, lines, new AABB(m.port()).inflate(0.05), 1f, 1f, 1f, 1f);
        }
        ps.popPose();
        buf.endBatch(NetScanRenderTypes.LINES);
        labels(event, s, cam, mc, buf);
    }

    private static void labels(RenderLevelStageEvent event, Scan s, Vec3 cam, Minecraft mc, MultiBufferSource.BufferSource buf) {
        List<NetScanPayload.Machine> ms = s.p.machines();
        if (ms.isEmpty()) return;
        Font font = mc.font;
        PoseStack ps = event.getPoseStack();
        for (NetScanPayload.Machine m : ms) {
            Vec3 top = new Vec3((m.min().getX() + m.max().getX() + 1) / 2.0, m.max().getY() + 1.6, (m.min().getZ() + m.max().getZ() + 1) / 2.0);
            double dist = top.distanceTo(cam);
            if (dist > LABEL_RANGE) continue;
            Component name = m.name();
            ps.pushPose();
            ps.translate(top.x - cam.x, top.y - cam.y, top.z - cam.z);
            ps.mulPose(event.getCamera().rotation());
            float sc = 0.02f * Mth.clamp((float) dist / 10f, 1f, 5f);
            ps.scale(-sc, -sc, sc);
            font.drawInBatch(name, -font.width(name) / 2f, 0, 0xFF000000 | stateColor(m.state()), false, ps.last().pose(), buf,
                    Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }
}
