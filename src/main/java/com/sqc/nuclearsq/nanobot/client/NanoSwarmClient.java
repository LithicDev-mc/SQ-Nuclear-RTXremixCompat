package com.sqc.nuclearsq.nanobot.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.NanoSwarmPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NanoSwarmClient {
    private NanoSwarmClient() {}

    private static final long TIMEOUT_MS = 3000L;
    private static final long FINISH_MS = 15000L;

    public interface Presenter {
        boolean add(ClientSwarm swarm);

        void remove(ClientSwarm swarm);

        void tick(ClientSwarm swarm);
    }

    @Nullable
    public static volatile Presenter presenter;

    private static final Map<UUID, ClientSwarm> SWARMS = new HashMap<>();

    public static Collection<ClientSwarm> swarms() {
        return SWARMS.values();
    }

    public static void receive(NanoSwarmPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.count() == 0) {
            ClientSwarm old = SWARMS.get(p.owner());
            if (old != null && p.state() == 5 && old.leaving() && !old.allEntered()) {
                old.finishing(System.currentTimeMillis());
                return;
            }
            drop(SWARMS.remove(p.owner()));
            return;
        }
        ClientSwarm s = SWARMS.get(p.owner());
        boolean fresh = s == null;
        if (fresh) {
            s = new ClientSwarm(p.owner());
            SWARMS.put(p.owner(), s);
        }
        s.update(p, mc.level.getGameTime(), System.currentTimeMillis());
        if (fresh) {
            Presenter pr = presenter;
            s.external = pr != null && pr.add(s);
        }
    }

    private static void drop(@Nullable ClientSwarm s) {
        Presenter pr = presenter;
        if (s != null && pr != null && s.external) pr.remove(s);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SWARMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        Presenter pr = presenter;
        for (Iterator<ClientSwarm> it = SWARMS.values().iterator(); it.hasNext();) {
            ClientSwarm s = it.next();
            if (now - s.lastPacket() > (s.isFinishing() ? FINISH_MS : TIMEOUT_MS) || (s.isFinishing() && s.allEntered())) {
                drop(s);
                it.remove();
            } else {
                if (pr != null) pr.tick(s);
                if (mc.level != null) s.tickSim(mc.level, SwarmFx.ownerPoint(s, 0f));
                if (mc.level != null && time % 20 == 0 && NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.DEBUG_BOT.get()) debugLog(s, mc.level, time);
                SwarmFx.tick(s, time);
            }
        }
    }

    private static void debugLog(ClientSwarm s, net.minecraft.client.multiplayer.ClientLevel level, long time) {
        Vec3 owner = SwarmFx.ownerPoint(s, 0f);
        NuclearSQ.LOGGER.info("[nanobots-debug] schwarm {} zustand {} bots {} flywheel {} flywheelBilder {} ersatz {} rueckstellungen {} mitte {}", s.owner, s.state(), s.count(),
                s.isExternal(), s.visualAlive(time), !s.visualAlive(time), s.invalidResets(), s.center(time, 0f));
        for (int i = 0; i < s.count(); i++) NuclearSQ.LOGGER.info("[nanobots-debug]   {}", s.debugLine(i, level, time, owner));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        for (ClientSwarm s : SWARMS.values()) drop(s);
        SWARMS.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SWARMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long time = mc.level.getGameTime();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.debugFilledBox());
        RenderType botType = RenderType.entityCutoutNoCull(BotCube.texture());
        VertexConsumer tv = buf.getBuffer(botType);
        float[] pose = new float[SpiderModel.P_SIZE];
        org.joml.Quaternionf orient = new org.joml.Quaternionf();
        boolean chrome = NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.NANOBOT_LOOK.get() == NuclearClientConfig.NanobotLook.CHROME;
        Vec3[] bf = new Vec3[SwarmFx.MAX_BEAMS], bt = new Vec3[SwarmFx.MAX_BEAMS];
        float[] bw = new float[SwarmFx.MAX_BEAMS];
        long time0 = time;
        boolean debugBox = NuclearClientConfig.SPEC.isLoaded() && NuclearClientConfig.DEBUG_BOT_BOX.get();
        if (debugBox) {
            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            for (ClientSwarm s : SWARMS.values()) {
                for (int i = 0; i < s.count(); i++) {
                    LevelRenderer.renderLineBox(ps, lines, s.hitbox(i, pt).move(-cam.x, -cam.y, -cam.z), 1f, 0.2f, 0.2f, 1f);
                    Vec3 c = s.bot(i, time0, pt), nrm = s.normal(i).scale(0.45);
                    LevelRenderer.renderLineBox(ps, lines, new net.minecraft.world.phys.AABB(c, c.add(nrm)).inflate(0.004).move(-cam.x, -cam.y, -cam.z), 0.2f, 1f, 0.3f, 1f);
                }
            }
            buf.endBatch(RenderType.lines());
        }
        for (ClientSwarm s : SWARMS.values()) {
            if (s.visualAlive(time)) continue;
            Vec3 owner = SwarmFx.ownerPoint(s, pt);
            boolean work = s.state() == 3;
            for (int i = 0; i < s.count(); i++) {
                float vis = s.visibility(i, time, pt, owner);
                if (vis <= 0.02f || s.invalid(i, pt)) continue;
                Vec3 b = s.bot(i, time, pt);
                if (b.distanceToSqr(cam) > 96.0 * 96.0) continue;
                s.orientation(i, pt, orient);
                s.pose(i, pt, pose);
                float glow = work ? 0.5f + 0.5f * Mth.sin((time + pt) * 0.5f - i) : 0.35f + 0.15f * Mth.sin((time + pt) * 0.2f + i);
                int light = SwarmFx.lightAt(mc.level, b);
                int pulse = Mth.clamp((int) (150 + 105 * glow), 0, 255);
                SpiderModel.parts(pose, i, (idx, mat, cx, cy, cz, hx, hy, hz, yaw, pitch) -> {
                    ps.pushPose();
                    ps.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
                    ps.mulPose(orient);
                    ps.scale(vis, vis, vis);
                    ps.translate(cx, cy, cz);
                    ps.mulPose(Axis.YP.rotation(yaw));
                    ps.mulPose(Axis.XP.rotation(pitch));
                    boolean lit = mat == SpiderModel.Mat.GLOW || mat == SpiderModel.Mat.SHINE;
                    int c = mat == SpiderModel.Mat.GLOW ? pulse : 255;
                    texturedBox(ps, tv, SpiderModel.kindOf(idx), hx, hy, hz, c, lit ? LightTexture.FULL_BRIGHT : light);
                    ps.popPose();
                });
            }
            int nb = SwarmFx.beams(s, time, pt, owner, bf, bt, bw);
            for (int k = 0; k < nb; k++) {
                Vec3 a = bf[k].subtract(cam), c = bt[k].subtract(cam);
                float w = bw[k];
                double dx = c.x - a.x, dy = c.y - a.y, dz = c.z - a.z;
                double h = Math.sqrt(dx * dx + dz * dz), len = Math.sqrt(h * h + dy * dy);
                if (len < 0.01) continue;
                ps.pushPose();
                ps.translate((a.x + c.x) / 2, (a.y + c.y) / 2, (a.z + c.z) / 2);
                ps.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
                ps.mulPose(Axis.XP.rotation(-(float) Math.atan2(dy, h)));
                LevelRenderer.addChainedFilledBoxVertices(ps, vc, -w, -w, -len / 2, w, w, len / 2, 0.35f, 0.95f, 1f, 1f);
                ps.popPose();
            }
        }
        buf.endBatch(botType);
        buf.endBatch(RenderType.debugFilledBox());
    }

    private static void texturedBox(PoseStack ps, VertexConsumer vc, BotUv.Kind kind, float hx, float hy, float hz, int c, int light) {
        PoseStack.Pose pose = ps.last();
        for (int f = 0; f < 6; f++) {
            float[] r = kind.rect(f), n = BotCube.NORMAL[f];
            for (int k = 0; k < 4; k++) {
                float[] p = BotCube.POS[f][k];
                vc.addVertex(pose, (p[0] - 0.5f) * 2 * hx, (p[1] - 0.5f) * 2 * hy, (p[2] - 0.5f) * 2 * hz).setColor(c, c, c, 255).setUv(BotCube.u(r, f, k), BotCube.v(r, f, k))
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
            }
        }
    }
}
