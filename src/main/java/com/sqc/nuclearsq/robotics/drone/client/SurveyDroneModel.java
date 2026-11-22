package com.sqc.nuclearsq.robotics.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class SurveyDroneModel extends EntityModel<SurveyDroneEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "survey_drone"), "main");
    private static final float R = 6.4f;
    private final ModelPart root;
    private final ModelPart[] rotors = new ModelPart[4];

    public SurveyDroneModel(ModelPart root) {
        this.root = root;
        for (int i = 0; i < 4; i++) rotors[i] = root.getChild("rotor" + i);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 21.7f, 0));
        seg(body, "hull", 0, -3.5f, 3.5f, 5, 3, 0.9f);
        seg(body, "nose", 0.1f, -5.2f, -3.5f, 4, 2.4f, 0.8f);
        seg(body, "tail", 0, 3.5f, 5, 4.4f, 2.6f, 0.8f);
        seg(body, "shell", -1.6f, -3.2f, 4.2f, 3.4f, 1, 0.4f);
        PartDefinition gimbal = body.addOrReplaceChild("gimbal", blob(CubeListBuilder.create(), 44, 44, 2, 2, 2, 0, 1.6f, -4.6f, 1.8f, 1.6f, 1.6f), PartPose.ZERO);
        gimbal.addOrReplaceChild("gimbal_r", blob(CubeListBuilder.create(), 44, 44, 2, 2, 2, 0, 0, 0, 1.6f, 1.4f, 1.6f), PartPose.offsetAndRotation(0, 1.6f, -4.6f, 0, Mth.PI / 4, 0));
        root.addOrReplaceChild("sensor", CubeListBuilder.create().texOffs(26, 14).addBox(-1.5f, 0, -1.5f, 3, 2, 3, new CubeDeformation(0, -0.5f, 0)), PartPose.offset(0, 22.5f, 0));
        for (int s = 0; s < 2; s++) {
            PartDefinition arm = root.addOrReplaceChild("arm" + s, tube(0), PartPose.offsetAndRotation(0, 21.2f, 0, 0, (s == 0 ? 1 : -1) * Mth.PI / 4, 0));
            arm.addOrReplaceChild("arm_r", tube(-0.16f), PartPose.rotation(Mth.PI / 4, 0, 0));
        }
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * R, z = (i < 2 ? -1 : 1) * R;
            float p = (i == 0 || i == 3) ? 0.26f : -0.26f;
            CubeListBuilder m = can(-0.02f).texOffs(42, 32).addBox(-1.5f, -0.5f, -1.5f, 3, 1, 3, new CubeDeformation(0, -0.2f, 0));
            blob(m, 54, 32, 1, 4, 1, 0, 1.45f, 0, 0.6f, 2.3f, 0.6f);
            blob(m, 56, 38, 2, 1, 2, 0, 2.4f, 0, 1.4f, 0.6f, 1.4f);
            PartDefinition motor = root.addOrReplaceChild("motor" + i, m, PartPose.offset(x, 21.3f, z));
            motor.addOrReplaceChild("motor_r", can(-0.18f), PartPose.rotation(0, Mth.PI / 4, 0));
            PartDefinition rotor = root.addOrReplaceChild("rotor" + i, CubeListBuilder.create().texOffs(38, 32).addBox(-0.5f, -1.5f, -0.5f, 1, 2, 1, new CubeDeformation(0.15f, -0.2f, 0.15f)),
                    PartPose.offset(x, 19.3f, z));
            rotor.addOrReplaceChild("hub_r", CubeListBuilder.create().texOffs(38, 32).addBox(-0.5f, -1.5f, -0.5f, 1, 2, 1, new CubeDeformation(0.15f, -0.28f, 0.15f)),
                    PartPose.rotation(0, Mth.PI / 4, 0));
            for (int b = 0; b < 2; b++) rotor.addOrReplaceChild("blade" + b, CubeListBuilder.create()
                    .texOffs(0, 36).addBox(0.4f, -0.5f, -1, 3, 1, 2, new CubeDeformation(0, -0.4f, -0.25f))
                    .texOffs(12, 36).addBox(3.4f, -0.5f, -0.5f, 2, 1, 1, new CubeDeformation(0, -0.41f, 0))
                    .texOffs(20, 36).addBox(5.2f, -0.5f, -0.5f, 1, 1, 1, new CubeDeformation(-0.2f, -0.42f, -0.22f)),
                    PartPose.offsetAndRotation(0, -0.9f, 0, p, b * Mth.PI, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static CubeListBuilder blob(CubeListBuilder b, int u, int v, int bw, int bh, int bd, float cx, float cy, float cz, float w, float h, float l) {
        return b.texOffs(u, v).addBox(cx - bw / 2f, cy - bh / 2f, cz - bd / 2f, bw, bh, bd, new CubeDeformation((w - bw) / 2, (h - bh) / 2, (l - bd) / 2));
    }

    private static void seg(PartDefinition body, String n, float cy, float z0, float z1, float w, float h, float c) {
        float l = z1 - z0, zc = (z0 + z1) / 2, f = c * (float) Math.sqrt(2) + 0.1f, t = 0.6f * c;
        PartDefinition part = body.addOrReplaceChild(n, blob(blob(CubeListBuilder.create(), 0, 44, 5, 3, 7, 0, cy, zc, w, h - 2 * c, l), 0, 44, 5, 3, 7, 0, cy, zc, w - 2 * c, h, l - 0.16f), PartPose.ZERO);
        for (int k = 0; k < 4; k++) {
            int sx = k < 2 ? 1 : -1, sy = k % 2 == 0 ? 1 : -1;
            part.addOrReplaceChild(n + "_c" + k, blob(CubeListBuilder.create(), 0, 44, 5, 3, 7, 0, t / 2, zc, f, t, l - 0.32f),
                    PartPose.offsetAndRotation(sx * (w / 2 - c / 2), cy + sy * (h / 2 - c / 2), 0, 0, 0, (float) Math.atan2(sx, -sy)));
        }
    }

    private static CubeListBuilder tube(float a) {
        CubeListBuilder t = CubeListBuilder.create().texOffs(0, 32).addBox(-9, -0.5f, -0.5f, 18, 1, 1, new CubeDeformation(a, 0.15f, -0.23f))
                .texOffs(0, 32).addBox(-9, -0.5f, -0.5f, 18, 1, 1, new CubeDeformation(a - 0.08f, -0.23f, 0.15f));
        for (float x : new float[] {-4.5f, 3.5f}) t.texOffs(24, 36).addBox(x, -0.5f, -0.5f, 1, 1, 1, new CubeDeformation(0.1f + a, 0.45f, -0.11f))
                .texOffs(24, 36).addBox(x, -0.5f, -0.5f, 1, 1, 1, new CubeDeformation(0.02f + a, -0.11f, 0.45f));
        return t;
    }

    private static CubeListBuilder can(float y) {
        return CubeListBuilder.create().texOffs(0, 14).addBox(-1, -2, -1, 2, 2, 2, new CubeDeformation(0.2f, y, -0.5f))
                .texOffs(0, 14).addBox(-1, -2, -1, 2, 2, 2, new CubeDeformation(-0.5f, y - 0.08f, 0.2f));
    }

    @Override
    public void setupAnim(SurveyDroneEntity e, float pos, float amt, float age, float yaw, float pitch) {
    }

    public void spin(SurveyDroneEntity e, float pt) {
        float a = Mth.lerp(pt, e.rotorO, e.rotor);
        for (int i = 0; i < 4; i++) rotors[i].yRot = (i == 0 || i == 3) ? a : -a;
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        root.render(ps, vc, light, overlay, color);
    }
}
