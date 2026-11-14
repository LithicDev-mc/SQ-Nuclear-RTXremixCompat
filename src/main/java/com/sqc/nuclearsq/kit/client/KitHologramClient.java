package com.sqc.nuclearsq.kit.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKitItem;
import com.sqc.nuclearsq.kit.MachineKits;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
public final class KitHologramClient {
    private KitHologramClient() {}

    static final int MAX_FACES = 6000;
    private static final int MAX_BLOCKED = 1500;
    private static final int CHECK_TICKS = 5;

    private static final RenderType GHOST = RenderType.create("nuclearsq_kit_hologram", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    record Shape(int[] cells, int[] faces, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private static final Map<String, Shape> SHAPES = new HashMap<>();

    private static MachineKits.Placement current;
    private static Shape shape;
    private static List<BlockPos> blocked = List.of();
    private static Set<BlockPos> blockedSet = Set.of();
    private static int age;
    private static boolean dirty;

    private static MachineKitItem held(Minecraft mc) {
        if (mc.player == null) return null;
        ItemStack s = mc.player.getMainHandItem();
        if (s.getItem() instanceof MachineKitItem k) return k;
        return mc.player.getOffhandItem().getItem() instanceof MachineKitItem k ? k : null;
    }

    static Shape shape(MachineKits.Def def, int rot) {
        return SHAPES.computeIfAbsent(def.id() + "/" + rot, k -> build(new MachineKits.Placement(def, BlockPos.ZERO, rot).footprint()));
    }

    static Shape build(List<BlockPos> all) {
        Set<BlockPos> set = new HashSet<>(all);
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        int[] cells = new int[all.size() * 3];
        List<int[]> faces = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            BlockPos p = all.get(i);
            cells[i * 3] = p.getX();
            cells[i * 3 + 1] = p.getY();
            cells[i * 3 + 2] = p.getZ();
            for (Direction d : Direction.values()) {
                if (!set.contains(p.relative(d))) faces.add(new int[] {p.getX(), p.getY(), p.getZ(), d.ordinal()});
            }
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        int step = Math.max(1, (faces.size() + MAX_FACES - 1) / MAX_FACES);
        List<int[]> keep = faces;
        while (step > 1 && (keep = thin(faces, step, y0, y1)).size() > MAX_FACES) step++;
        int[] flat = new int[keep.size() * 4];
        for (int i = 0; i < keep.size(); i++) System.arraycopy(keep.get(i), 0, flat, i * 4, 4);
        return new Shape(cells, flat, x0, y0, z0, x1, y1, z1);
    }

    private static List<int[]> thin(List<int[]> faces, int step, int y0, int y1) {
        List<int[]> out = new ArrayList<>();
        for (int[] f : faces) {
            int y = f[1];
            boolean layer = (y - y0) % step == 0 || y == y1;
            boolean side = f[3] >= 2;
            if (layer && (side || y == y0 && f[3] == 0 || y == y1 && f[3] == 1)) out.add(f);
        }
        return out;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MachineKitItem kit = held(mc);
        if (kit == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            current = null;
            return;
        }
        BlockPos target = MachineKitItem.target(mc.level, bhr.getBlockPos(), bhr.getDirection());
        Direction look = mc.player.getDirection();
        MachineKits.Placement pl = MachineKits.placement(mc.level, kit.def(), target, look);
        if (current == null || current.def() != pl.def() || !current.anchor().equals(pl.anchor()) || current.rot() != pl.rot()) {
            current = pl;
            shape = shape(pl.def(), pl.rot());
            dirty = true;
        }
        if (++age >= CHECK_TICKS || dirty && age >= 2) {
            age = 0;
            dirty = false;
            List<BlockPos> b = new ArrayList<>();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int[] c = shape.cells();
            BlockPos a = pl.anchor();
            for (int i = 0; i < c.length; i += 3) {
                m.set(a.getX() + c[i], a.getY() + c[i + 1], a.getZ() + c[i + 2]);
                if (!MachineKits.free(mc.level, m)) b.add(m.immutable());
            }
            blocked = b;
            blockedSet = new HashSet<>(b);
        }
        Component name = new ItemStack(kit).getHoverName();
        mc.player.displayClientMessage(blocked.isEmpty()
                ? Component.translatable("message.nuclearsq.kit.ready", name).withStyle(ChatFormatting.AQUA)
                : Component.translatable("message.nuclearsq.kit.blocked", blocked.size()).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || current == null || shape == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (held(mc) == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        BlockPos a = current.anchor();
        boolean ok = blocked.isEmpty();
        ps.pushPose();
        ps.translate(a.getX() - cam.x, a.getY() - cam.y, a.getZ() - cam.z);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(GHOST);
        int[] f = shape.faces();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < f.length; i += 4) {
            if (!ok && blockedSet.contains(at.set(a.getX() + f[i], a.getY() + f[i + 1], a.getZ() + f[i + 2]))) continue;
            face(vc, m, f[i], f[i + 1], f[i + 2], f[i + 3], 64, 179, 255, 41);
        }
        int n = 0;
        for (BlockPos p : blocked) {
            if (n++ >= MAX_BLOCKED) break;
            for (int d = 0; d < 6; d++) face(vc, m, p.getX() - a.getX(), p.getY() - a.getY(), p.getZ() - a.getZ(), d, 255, 51, 51, 102);
        }
        buf.endBatch(GHOST);
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        float r = ok ? 0.3f : 1f, g = ok ? 0.8f : 0.25f, b = ok ? 1f : 0.25f;
        LevelRenderer.renderLineBox(ps, lines, new AABB(shape.minX(), shape.minY(), shape.minZ(),
                shape.maxX() + 1, shape.maxY() + 1, shape.maxZ() + 1).inflate(0.01), r, g, b, 1f);
        n = 0;
        for (BlockPos p : blocked) {
            if (n++ >= MAX_BLOCKED) break;
            LevelRenderer.renderLineBox(ps, lines, new AABB(p.subtract(a)).deflate(0.02), 1f, 0.2f, 0.2f, 1f);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
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
