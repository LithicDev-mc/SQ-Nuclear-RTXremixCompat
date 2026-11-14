package com.sqc.nuclearsq.compat.flywheel;

import com.sqc.nuclearsq.nanobot.client.BotCube;
import com.sqc.nuclearsq.nanobot.client.BotUv;
import com.sqc.nuclearsq.nanobot.client.ClientSwarm;
import com.sqc.nuclearsq.nanobot.client.SpiderModel;
import com.sqc.nuclearsq.nanobot.client.SwarmFx;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class SwarmVisual implements EffectVisual<SwarmEffect>, SimpleDynamicVisual {
    private final ClientSwarm swarm;
    private final Vec3i origin;
    private final TransformedInstance[] parts;
    private final TransformedInstance[] beams = new TransformedInstance[SwarmFx.MAX_BEAMS];
    private final Vec3[] bf = new Vec3[SwarmFx.MAX_BEAMS], bt = new Vec3[SwarmFx.MAX_BEAMS];
    private final float[] bw = new float[SwarmFx.MAX_BEAMS];
    private final float[] pose = new float[SpiderModel.P_SIZE];
    private final org.joml.Quaternionf orient = new org.joml.Quaternionf();
    private static final java.util.Map<String, Model> MODELS = new java.util.HashMap<>();

    private static Model cube(BotUv.Kind kind, ResourceLocation tex) {
        return MODELS.computeIfAbsent(kind + "@" + tex, k -> {
            MemoryBlock mem = MemoryBlock.mallocTracked(24 * FullVertexView.STRIDE);
            FullVertexView v = new FullVertexView();
            v.load(mem);
            int i = 0;
            for (int f = 0; f < 6; f++) {
                float[] r = kind.rect(f);
                for (int c = 0; c < 4; c++, i++) {
                    float[] p = BotCube.POS[f][c], n = BotCube.NORMAL[f];
                    v.x(i, p[0]);
                    v.y(i, p[1]);
                    v.z(i, p[2]);
                    v.r(i, 1f);
                    v.g(i, 1f);
                    v.b(i, 1f);
                    v.a(i, 1f);
                    v.u(i, BotCube.u(r, f, c));
                    v.v(i, BotCube.v(r, f, c));
                    v.overlay(i, OverlayTexture.NO_OVERLAY);
                    v.light(i, 0);
                    v.normalX(i, n[0]);
                    v.normalY(i, n[1]);
                    v.normalZ(i, n[2]);
                }
            }
            Material m = SimpleMaterial.builderOf(Materials.SOLID_BLOCK).texture(tex).mipmap(false).blur(false).build();
            return new SingleMeshModel(new SimpleQuadMesh(v, "nano bot " + kind), m);
        });
    }

    SwarmVisual(VisualizationContext ctx, ClientSwarm swarm) {
        this.swarm = swarm;
        this.origin = ctx.renderOrigin();
        this.parts = new TransformedInstance[Math.max(1, swarm.count()) * SpiderModel.PARTS];
        ResourceLocation tex = BotCube.texture();
        java.util.Map<BotUv.Kind, Instancer<TransformedInstance>> byKind = new java.util.EnumMap<>(BotUv.Kind.class);
        for (BotUv.Kind kind : BotUv.Kind.values()) byKind.put(kind, ctx.instancerProvider().instancer(InstanceTypes.TRANSFORMED, cube(kind, tex)));
        Instancer<TransformedInstance> glow = ctx.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.SEA_LANTERN.defaultBlockState()));
        for (int i = 0; i < parts.length; i++) {
            Instancer<TransformedInstance> in = byKind.get(SpiderModel.kindOf(i % SpiderModel.PARTS));
            parts[i] = in.createInstance();
            parts[i].setZeroTransform().setChanged();
        }
        for (int i = 0; i < beams.length; i++) {
            beams[i] = glow.createInstance();
            beams[i].light(LightTexture.FULL_BRIGHT);
            beams[i].setZeroTransform().setChanged();
        }
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long time = mc.level.getGameTime();
        float pt = ctx.partialTick();
        swarm.markVisualFrame(time);
        Vec3 owner = SwarmFx.ownerPoint(swarm, pt);
        boolean work = swarm.state() == 3;
        int cap = parts.length / SpiderModel.PARTS;
        int n = Math.min(swarm.count(), cap);
        for (int i = 0; i < cap; i++) {
            float vis = i < n && !swarm.invalid(i, pt) ? swarm.visibility(i, time, pt, owner) : 0f;
            int base = i * SpiderModel.PARTS;
            if (vis <= 0.02f) {
                for (int k = 0; k < SpiderModel.PARTS; k++) parts[base + k].setZeroTransform().setChanged();
                continue;
            }
            Vec3 p = swarm.bot(i, time, pt);
            swarm.orientation(i, pt, orient);
            swarm.pose(i, pt, pose);
            int light = SwarmFx.lightAt(mc.level, p);
            float glow = work ? 0.5f + 0.5f * Mth.sin((time + pt) * 0.5f - i) : 0.35f + 0.15f * Mth.sin((time + pt) * 0.2f + i);
            int pulse = (int) (150 + 105 * glow);
            SpiderModel.parts(pose, i, (idx, mat, cx, cy, cz, hx, hy, hz, yaw, pitch) -> {
                TransformedInstance t = parts[base + idx];
                t.setIdentityTransform()
                        .translate((float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()))
                        .rotate(orient)
                        .scale(vis, vis, vis)
                        .translate(cx, cy, cz)
                        .rotateY(yaw)
                        .rotateX(pitch)
                        .scale(hx * 2, hy * 2, hz * 2)
                        .translate(-0.5f, -0.5f, -0.5f);
                switch (mat) {
                    case GLOW -> {
                        t.light(LightTexture.FULL_BRIGHT);
                        t.color(Mth.clamp(pulse, 0, 255), Mth.clamp(pulse, 0, 255), Mth.clamp(pulse, 0, 255));
                    }
                    case SHINE -> {
                        t.light(LightTexture.pack(15, 15));
                        t.color(255, 255, 255);
                    }
                    default -> {
                        t.light(light);
                        t.color(255, 255, 255);
                    }
                }
                t.setChanged();
            });
        }
        int nb = SwarmFx.beams(swarm, time, pt, owner, bf, bt, bw);
        for (int k = 0; k < beams.length; k++) {
            TransformedInstance t = beams[k];
            if (k >= nb) {
                t.setZeroTransform().setChanged();
                continue;
            }
            double dx = bt[k].x - bf[k].x, dy = bt[k].y - bf[k].y, dz = bt[k].z - bf[k].z;
            double h = Math.sqrt(dx * dx + dz * dz), len = Math.sqrt(h * h + dy * dy);
            float w = bw[k];
            t.setIdentityTransform()
                    .translate((float) ((bf[k].x + bt[k].x) / 2 - origin.getX()), (float) ((bf[k].y + bt[k].y) / 2 - origin.getY()),
                            (float) ((bf[k].z + bt[k].z) / 2 - origin.getZ()))
                    .rotateY((float) Math.atan2(dx, dz))
                    .rotateX(-(float) Math.atan2(dy, h))
                    .scale(w * 2, w * 2, (float) len)
                    .translate(-0.5f, -0.5f, -0.5f);
            t.color(110, 245, 255);
            t.setChanged();
        }
    }

    @Override
    public void update(float partialTick) {}

    @Override
    public void delete() {
        for (TransformedInstance b : parts) b.delete();
        for (TransformedInstance b : beams) b.delete();
    }
}
