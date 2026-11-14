package com.sqc.nuclearsq.robotics.decon.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.decon.DeconRegistry;
import com.sqc.nuclearsq.robotics.decon.DeconRobotEntity;
import com.sqc.nuclearsq.robotics.decon.RobotPayloads;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class DeconRobotClient {
    private DeconRobotClient() {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static int controlledId = -1;
    private static float incline;
    private static final java.util.function.DoubleUnaryOperator ORBIT = p -> Minecraft.getInstance().player.getViewYRot((float) p);
    private static final RandomSource NOISE_RANDOM = RandomSource.create();

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DeconRobotModel.LAYER, DeconRobotModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeconRegistry.ROBOT.get(), DeconRobotRenderer::new);
    }

    public static void setControl(int robotId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (robotId >= 0 && mc.level.getEntity(robotId) instanceof DeconRobotEntity robot) {
            controlledId = robotId;
            mc.setCameraEntity(robot);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else {
            if (robot(mc) != null) robot(mc).orbitYaw = null;
            controlledId = -1;
            mc.setCameraEntity(mc.player);
        }
    }

    private static DeconRobotEntity robot(Minecraft mc) {
        return controlledId >= 0 && mc.level != null && mc.level.getEntity(controlledId) instanceof DeconRobotEntity r ? r : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (controlledId < 0 || mc.player == null) return;
        if (robot(mc) == null) {
            setControl(-1);
            return;
        }
        var o = mc.options;
        DeconRobotEntity r = robot(mc);
        r.orbitYaw = o.getCameraType().isFirstPerson() ? null : ORBIT;
        if (r.orbitYaw == null) {
            mc.player.setYRot(r.getYRot());
            mc.player.yRotO = r.getYRot();
        }
        int fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        int turn = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
        float pitch = Mth.clamp(mc.player.getXRot(), -DeconRobotEntity.PITCH_MAX, DeconRobotEntity.PITCH_MAX);
        r.setXRot(pitch);
        PacketDistributor.sendToServer(new RobotPayloads.ControlInput(fwd, turn, o.keyShift.isDown(), pitch));
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
        if (controlledId < 0 || event.isUseItem()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (controlledId >= 0) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        DeconRobotEntity r = robot(mc);
        if (r == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int noise = r.noise();
        if (noise > 0) {
            int dots = noise * 12;
            for (int i = 0; i < dots; i++) {
                int x = NOISE_RANDOM.nextInt(w), y = NOISE_RANDOM.nextInt(h), s = 1 + NOISE_RANDOM.nextInt(3);
                int shade = 90 + NOISE_RANDOM.nextInt(150);
                g.fill(x, y, x + s, y + 1, 0xFF000000 | shade << 16 | shade << 8 | shade);
            }
        }
        frame(g, mc, r, w, h);
        int y = 24;
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.energy", r.energy() * 100 / DeconRobotEntity.BATTERY), 8, y, 0xFF56C2F0, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.waste", r.waste(), DeconRobotEntity.WASTE_MAX), 8, y + 11, r.waste() >= DeconRobotEntity.WASTE_MAX ? 0xFFDE5646 : 0xFFE8C13A, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.agent", r.agent(), DeconRobotEntity.AGENT_MAX), 8, y + 22, r.agent() < DeconRobotEntity.AGENT_COST ? 0xFFDE5646 : 0xFFE8E4C0, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.health", (int) r.getHealth(), (int) r.getMaxHealth()), 8, y + 33, r.broken() ? 0xFFDE5646 : 0xFF34CF77, true);
        if (r.agent() < DeconRobotEntity.AGENT_COST) g.drawCenteredString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.no_agent"), w / 2, h / 2 + 16, 0xFFDE5646);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.help"), 8, h - 14, 0xFF98A0A7, true);
        sensors(g, mc, r, y + 49);
    }

    private static void frame(GuiGraphics g, Minecraft mc, DeconRobotEntity r, int w, int h) {
        int c = 0xCCFFFFFF, m = 8, l = 14;
        for (int i = 0; i < 4; i++) {
            int x = i % 2 == 0 ? m : w - m, y = i < 2 ? m : h - m, dx = i % 2 == 0 ? 1 : -1, dy = i < 2 ? 1 : -1;
            g.fill(Math.min(x, x + dx * l), y, Math.max(x, x + dx * l), y + dy, c);
            g.fill(x, Math.min(y, y + dy * l), x + dx, Math.max(y, y + dy * l), c);
        }
        g.fill(w / 2 - 6, h / 2, w / 2 + 7, h / 2 + 1, c);
        g.fill(w / 2, h / 2 - 6, w / 2 + 1, h / 2 + 7, c);
        long t = r.tickCount / 20;
        String code = String.format("%02d:%02d:%02d", t / 3600, t / 60 % 60, t % 60);
        Component rec = Component.translatable("hud.nuclearsq.decon_robot.rec", code);
        int rx = w - 8 - mc.font.width(rec);
        if (r.tickCount % 30 < 20) g.fill(rx - 10, 14, rx - 4, 20, 0xFFDE2020);
        g.drawString(mc.font, rec, rx, 13, 0xFFFFFFFF, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.cam"), w - 90, 25, 0xFFFFFFFF, true);
        Component dir = Component.translatable("hud.nuclearsq.decon_robot.dir." + net.minecraft.core.Direction.fromYRot(r.getYRot()).getName());
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.pos", dir, r.getBlockX(), r.getBlockY(), r.getBlockZ()), w - 110, h - 24, 0xFFFFFFFF, true);
    }

    private static void sensors(GuiGraphics g, Minecraft mc, DeconRobotEntity r, int y) {
        var lv = mc.level;
        BlockPos pos = r.blockPosition();
        double mSv = r.radiation() * 1000.0;
        int radColor = mSv >= 100 ? 0xFFDE5646 : mSv >= 10 ? 0xFFE8C13A : 0xFF34CF77;
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.rad", String.format("%.3f", mSv)), 8, y, radColor, true);
        float temp = lv.getBiome(pos).value().getBaseTemperature() * 25f;
        g.drawString(mc.font, Component.translatable(lv.isRaining() ? "hud.nuclearsq.decon_robot.temp_rain" : "hud.nuclearsq.decon_robot.temp", String.format("%.0f", temp)), 8, y + 11, 0xFFFFFFFF, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.lux", lv.getMaxLocalRawBrightness(pos)), 8, y + 22, 0xFFFFFFFF, true);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.ground", lv.getBlockState(pos.below()).getBlock().getName()), 8, y + 33, 0xFFFFFFFF, true);
        var hit = mc.hitResult;
        Component target = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? lv.getBlockState(b.getBlockPos()).getBlock().getName() : Component.literal("-");
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.target", target), 8, y + 44, 0xFFFFFFFF, true);
        int w = g.guiWidth();
        g.drawString(mc.font, LocalDateTime.now().format(STAMP), w - 128, 37, 0xFFFFFFFF, true);
        double dx = r.getX() - r.xo, dz = r.getZ() - r.zo, dy = r.getY() - r.yo, flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > 0.001) incline = (float) Math.toDegrees(Math.atan2(dy, flat));
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.speed", String.format("%.1f", flat * 20), String.format("%+.0f", incline)), w - 128, 49, 0xFFFFFFFF, true);
        double d = mc.player.distanceTo(r), sig = Math.max(0, 100 - 100 * d / DeconRobotEntity.RANGE) * (1 - r.noise() / 200.0);
        g.drawString(mc.font, Component.translatable("hud.nuclearsq.decon_robot.signal", String.format("%.0f", sig), String.format("%03.0f", Mth.positiveModulo(r.getYRot() + 180f, 360f))), w - 128, 61, sig < 25 ? 0xFFDE5646 : 0xFFFFFFFF, true);
    }
}
