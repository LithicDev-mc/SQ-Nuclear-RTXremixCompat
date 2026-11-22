package com.sqc.nuclearsq.robotics.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.robotics.drone.DronePayloads;
import com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity;
import com.sqc.nuclearsq.robotics.drone.SurveyMap;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class SurveyDroneControlClient {
    private SurveyDroneControlClient() {}

    private static final int MAP_PX = 128, WHITE = 0xFFFFFFFF, GREY = 0xFF98A0A7, BLUE = 0xFF56C2F0, RED = 0xFFDE5646, AMBER = 0xFFE8C13A;
    private static int controlledId = -1;
    private static DronePayloads.Scan scan;
    private static long scanMs;
    private static boolean stopSent;

    public static void setControl(int droneId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        SurveyDroneEntity old = drone(mc);
        if (old != null) old.viewYaw = old.viewPitch = null;
        if (droneId >= 0 && mc.level.getEntity(droneId) instanceof SurveyDroneEntity d) {
            controlledId = droneId;
            stopSent = false;
            d.viewYaw = pt -> mc.player.getViewYRot((float) pt);
            d.viewPitch = pt -> mc.player.getViewXRot((float) pt);
            mc.setCameraEntity(d);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            controlledId = -1;
            scan = null;
            mc.setCameraEntity(mc.player);
        }
    }

    public static void receive(DronePayloads.Scan p) {
        scan = p;
        scanMs = System.currentTimeMillis();
    }

    public static boolean active() {
        return controlledId >= 0;
    }

    private static SurveyDroneEntity drone(Minecraft mc) {
        return controlledId >= 0 && mc.level != null && mc.level.getEntity(controlledId) instanceof SurveyDroneEntity d ? d : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (controlledId < 0 || mc.player == null) return;
        SurveyDroneEntity d = drone(mc);
        if (d == null) {
            setControl(-1);
            return;
        }
        var o = mc.options;
        int fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        int strafe = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
        int up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        PacketDistributor.sendToServer(new DronePayloads.Input(fwd, strafe, up, mc.player.getYRot(), mc.player.getXRot(), false));
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (controlledId < 0) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (controlledId < 0) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem() && !stopSent) {
            stopSent = true;
            PacketDistributor.sendToServer(new DronePayloads.Input(0, 0, 0, 0, 0, true));
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (controlledId >= 0) event.setCanceled(true);
    }

    private static DronePayloads.Scan freshScan() {
        DronePayloads.Scan s = scan;
        return s != null && System.currentTimeMillis() - scanMs < 3000L ? s : null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || controlledId < 0) return;
        DronePayloads.Scan s = freshScan();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || s.pos().length == 0 || mc.level == null || mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (int i = 0; i < s.pos().length; i++) {
            int c = SurveyMapScreen.color(SurveyMap.encode(s.svH()[i]));
            LevelRenderer.renderLineBox(ps, lines, new AABB(BlockPos.of(s.pos()[i])).inflate(0.004),
                    ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SurveyDroneEntity d = drone(mc);
        if (d == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        corners(g, w, h);
        DronePayloads.Scan s = freshScan();
        int y = 14;
        int pct = d.energy() * 100 / SurveyDroneEntity.BATTERY, low = SurveyDroneEntity.RESERVE * 100 / SurveyDroneEntity.BATTERY;
        line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.energy", pct), y, pct <= low + 10 ? RED : BLUE);
        double here = s == null ? 0 : s.here();
        line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.here", RadiationManager.fmtRate(here)), y + 11, rateColor(here));
        BlockPos t = target(d);
        double tv = t == null || s == null ? -1 : valueAt(s, t);
        Component tc = t == null ? Component.literal("-") : tv > 0
                ? Component.translatable("hud.nuclearsq.survey_drone.target_value", mc.level.getBlockState(t).getBlock().getName(), RadiationManager.fmtRate(tv))
                : mc.level.getBlockState(t).getBlock().getName();
        line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.target", tc), y + 22, tv > 0 ? rateColor(tv) : WHITE);
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, d.getBlockX(), d.getBlockZ());
        line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.height", Math.max(0, d.getBlockY() - ground), d.getBlockX(), d.getBlockY(), d.getBlockZ()), y + 33, WHITE);
        double sig = Math.max(0, 100 - 100 * mc.player.distanceTo(d) / SurveyDroneEntity.RANGE);
        line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.signal", String.format("%.0f", sig)), y + 44, sig < 25 ? RED : WHITE);
        if (s != null && s.pos().length > 0) line(g, mc, Component.translatable("hud.nuclearsq.survey_drone.sources", s.pos().length), y + 55, AMBER);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.survey_drone.help"), 8, h - 14, GREY, true);
        if (s != null) minimap(g, mc, d, s.map(), w);
        g.fill(w / 2 - 6, h / 2, w / 2 + 7, h / 2 + 1, 0xCCFFFFFF);
        g.fill(w / 2, h / 2 - 6, w / 2 + 1, h / 2 + 7, 0xCCFFFFFF);
    }

    private static void line(GuiGraphics g, Minecraft mc, Component c, int y, int color) {
        g.drawString(mc.font, c, 8, y, color, true);
    }

    private static int rateColor(double svH) {
        return svH <= 0 ? WHITE : SurveyMapScreen.color(SurveyMap.encode(svH));
    }

    private static BlockPos target(SurveyDroneEntity d) {
        HitResult hit = d.pick(SurveyDroneEntity.SCAN, 1f, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    static double valueAt(DronePayloads.Scan s, BlockPos p) {
        long key = p.asLong(), above = p.above().asLong();
        double best = -1;
        for (int i = 0; i < s.pos().length; i++) if (s.pos()[i] == key || s.pos()[i] == above) best = Math.max(best, s.svH()[i]);
        return best;
    }

    private static void corners(GuiGraphics g, int w, int h) {
        int c = 0xCCFFFFFF, m = 6, l = 14;
        for (int i = 0; i < 4; i++) {
            int x = i % 2 == 0 ? m : w - m, y = i < 2 ? m : h - m, dx = i % 2 == 0 ? 1 : -1, dy = i < 2 ? 1 : -1;
            g.fill(Math.min(x, x + dx * l), y, Math.max(x, x + dx * l), y + dy, c);
            g.fill(x, Math.min(y, y + dy * l), x + dx, Math.max(y, y + dy * l), c);
        }
    }

    private static void minimap(GuiGraphics g, Minecraft mc, SurveyDroneEntity d, SurveyMap m, int w) {
        if (m == null || m.w() == 0 || m.h() == 0) return;
        int c = Math.max(1, Math.min(MAP_PX / m.w(), MAP_PX / m.h()));
        int mw = c * m.w(), mh = c * m.h(), ox = w - 12 - mw, oy = 14;
        g.fill(ox - 2, oy - 2, ox + mw + 2, oy + mh + 2, 0xC0101418);
        g.fill(ox - 1, oy - 1, ox + mw + 1, oy, 0xFF5A6066);
        g.fill(ox - 1, oy + mh, ox + mw + 1, oy + mh + 1, 0xFF5A6066);
        g.fill(ox - 1, oy, ox, oy + mh, 0xFF5A6066);
        g.fill(ox + mw, oy, ox + mw + 1, oy + mh, 0xFF5A6066);
        for (int j = 0; j < m.h(); j++) for (int i = 0; i < m.w(); i++) {
            int b = m.at(i, j);
            if (b > 0) g.fill(ox + i * c, oy + j * c, ox + (i + 1) * c, oy + (j + 1) * c, SurveyMapScreen.color(b));
        }
        int pi = Mth.floor((d.getX() - m.x0()) / SurveyMap.CELL), pj = Mth.floor((d.getZ() - m.z0()) / SurveyMap.CELL);
        if (pi >= 0 && pj >= 0 && pi < m.w() && pj < m.h()) {
            int px = ox + pi * c + c / 2, py = oy + pj * c + c / 2;
            g.fill(px - 2, py, px + 3, py + 1, WHITE);
            g.fill(px, py - 2, px + 1, py + 3, WHITE);
        }
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.survey_drone.map"), ox, oy + mh + 4, GREY, true);
    }
}
