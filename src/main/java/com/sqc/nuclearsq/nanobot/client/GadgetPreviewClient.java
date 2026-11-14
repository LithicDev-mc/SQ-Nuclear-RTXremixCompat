package com.sqc.nuclearsq.nanobot.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.nanobot.GadgetData;
import com.sqc.nuclearsq.nanobot.NanoCanisterItem;
import com.sqc.nuclearsq.nanobot.NanoGadget;
import com.sqc.nuclearsq.nanobot.NanoTask;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GadgetPreviewClient {
    private GadgetPreviewClient() {}

    private static final int MAX_FACES = 6000;

    private static final RenderType GHOST = RenderType.create("nuclearsq_gadget_hologram", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static NanoTask task;
    private static BlockPos a, b;
    private static boolean fixedB;
    private static int count;
    private static GadgetData lastKey;
    private static int[] faces = new int[0];
    private static boolean tooBig;
    private static List<BlockPos> template = List.of();
    private static int templateVersion, pasteVersion = -1;
    private static BlockPos pasteAnchor;
    private static GadgetData.Paste pasteSettings;

    public static void setTemplate(List<BlockPos> rels) {
        template = List.copyOf(rels);
        templateVersion++;
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        setTemplate(List.of());
        lastKey = null;
    }

    private static ItemStack canister(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack s = mc.player.getMainHandItem();
        if (s.getItem() instanceof NanoCanisterItem) return s;
        s = mc.player.getOffhandItem();
        return s.getItem() instanceof NanoCanisterItem ? s : ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack can = canister(mc);
        task = can.isEmpty() ? null : NanoCanisterItem.task(can);
        if (task == null || !task.gadget() || mc.level == null) {
            a = b = null;
            return;
        }
        GadgetData d = NanoGadget.data(can);
        if (task == NanoTask.GADGET_PASTE) {
            tickPaste(mc, d);
            return;
        }
        a = d.p1().orElse(null);
        fixedB = d.p2().isPresent() || (GadgetData.isRound(d.shape()) && a != null);
        b = d.p2().orElse(null);
        BlockPos live = mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? target(h) : null;
        if (GadgetData.isRound(d.shape())) {
            if (a == null) a = live;
            if (a == null) return;
            b = a;
        } else {
            if (a == null) {
                b = live;
                return;
            }
            if (b == null) b = live;
            if (b == null) return;
        }
        GadgetData key = d.withPoints(Optional.of(a), Optional.of(b));
        if (!key.equals(lastKey)) {
            lastKey = key;
            List<BlockPos> cells = NanoGadget.cells(key, NanoGadget.PREVIEW_MAX);
            tooBig = cells.isEmpty();
            count = cells.size();
            faces = tooBig ? new int[0] : buildFaces(cells);
        }
    }

    private static void tickPaste(Minecraft mc, GadgetData d) {
        a = d.p1().orElse(null);
        fixedB = a != null;
        if (a == null && mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) a = target(h);
        if (a == null) {
            b = null;
            return;
        }
        b = a;
        if (!a.equals(pasteAnchor) || !d.paste().equals(pasteSettings) || pasteVersion != templateVersion) {
            pasteAnchor = a;
            pasteSettings = d.paste();
            pasteVersion = templateVersion;
            List<BlockPos> cells = new ArrayList<>(template.size());
            for (BlockPos rel : template) cells.add(NanoGadget.pastePos(a, d.paste(), rel));
            tooBig = false;
            count = cells.size();
            faces = cells.isEmpty() ? new int[0] : buildFaces(cells);
        }
    }

    private static BlockPos target(BlockHitResult h) {
        return task == NanoTask.GADGET_BUILD || task == NanoTask.GADGET_PASTE ? h.getBlockPos().relative(h.getDirection()) : h.getBlockPos();
    }

    private static int[] buildFaces(List<BlockPos> cells) {
        Set<BlockPos> set = new HashSet<>(cells);
        List<int[]> out = new ArrayList<>();
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                if (!set.contains(p.relative(d))) out.add(new int[] {p.getX(), p.getY(), p.getZ(), d.ordinal()});
            }
        }
        int step = Math.max(1, (out.size() + MAX_FACES - 1) / MAX_FACES);
        int[] flat = new int[(out.size() / step + 1) * 4];
        int n = 0;
        for (int i = 0; i < out.size(); i += step) {
            System.arraycopy(out.get(i), 0, flat, n * 4, 4);
            n++;
        }
        return java.util.Arrays.copyOf(flat, n * 4);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || task == null || !task.gadget() || b == null) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        int r, g, bl;
        switch (task) {
            case GADGET_BREAK -> { r = 255; g = 70; bl = 70; }
            case GADGET_REPLACE -> { r = 255; g = 190; bl = 60; }
            case GADGET_COPY -> { r = 90; g = 240; bl = 230; }
            case GADGET_PASTE -> { r = 90; g = 255; bl = 120; }
            default -> { r = 64; g = 179; bl = 255; }
        }
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        if (a != null) {
            VertexConsumer vc = buf.getBuffer(GHOST);
            for (int i = 0; i < faces.length; i += 4) face(vc, m, faces[i], faces[i + 1], faces[i + 2], faces[i + 3], r, g, bl, 56);
            buf.endBatch(GHOST);
        }
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        if (a != null) LevelRenderer.renderLineBox(ps, lines, new AABB(a).inflate(0.02), 0.2f, 1f, 0.4f, 1f);
        LevelRenderer.renderLineBox(ps, lines, new AABB(b).inflate(0.02), fixedB ? 0.2f : 1f, fixedB ? 1f : 1f, fixedB ? 0.4f : 1f, 1f);
        ps.popPose();
        buf.endBatch(RenderType.lines());
        if (a != null && mc.player != null && !fixedB) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(tooBig ? "message.nuclearsq.nanobots.gadget_preview_big" : "message.nuclearsq.nanobots.gadget_preview", count), true);
        }
    }

    private static void face(VertexConsumer vc, Matrix4f m, int x, int y, int z, int d, int r, int g, int b, int alpha) {
        float e = 0.02f, x0 = x + e, y0 = y + e, z0 = z + e, x1 = x + 1 - e, y1 = y + 1 - e, z1 = z + 1 - e;
        switch (d) {
            case 0 -> quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
            case 1 -> quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
            case 2 -> quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, alpha);
            case 3 -> quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
            case 4 -> quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
            default -> quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int alpha) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, alpha);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, alpha);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, alpha);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, alpha);
    }
}
