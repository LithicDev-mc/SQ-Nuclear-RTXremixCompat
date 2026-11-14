package com.sqc.nuclearsq.robotics.decon.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.decon.DeconRobotEntity;

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

public class DeconRobotModel extends EntityModel<DeconRobotEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "decon_robot"), "main");
    private static final int LINKS = 13;
    private static final float RUN = 15f, R = 3.25f, AXIS = 19.5f, LOOP = 2 * RUN + 2 * Mth.PI * R;
    private static final float[] WHEEL_R = {3.25f, 3.25f, 2.5f, 2.5f};
    private static final float[] WHEEL_Z = {7.5f, -7.5f, 2.5f, -2.5f};
    private static final int SEGMENTS = 6;
    private static final float ARM_UP = 1.2f;
    private final ModelPart root, boom, scoop, armL, armR, head, lidar, plateTop, plateL, plateR;
    private final ModelPart[][] gauge = new ModelPart[3][SEGMENTS];
    private final ModelPart[][] links = new ModelPart[2][LINKS], wheels = new ModelPart[2][4];

    public DeconRobotModel(ModelPart root) {
        this.root = root;
        this.boom = root.getChild("boom");
        this.scoop = boom.getChild("scoop");
        this.armL = boom.getChild("arm_l");
        this.armR = boom.getChild("arm_r");
        this.head = root.getChild("head");
        this.lidar = head.getChild("lidar");
        this.plateTop = root.getChild("plate_top");
        this.plateL = root.getChild("plate_l");
        this.plateR = root.getChild("plate_r");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            for (int i = 0; i < LINKS; i++) links[s][i] = root.getChild("link_" + side + i);
            for (int i = 0; i < 4; i++) wheels[s][i] = root.getChild("wheel_" + side + i);
        }
        for (int k = 0; k < 3; k++) for (int i = 0; i < SEGMENTS; i++) gauge[k][i] = root.getChild("seg_" + k + i);
    }

    public void setArm(float down) {
        boom.xRot = -ARM_UP * (1 - down);
    }

    public void setTracks(float left, float right) {
        for (int s = 0; s < 2; s++) {
            float way = s == 0 ? left : right;
            for (int i = 0; i < LINKS; i++) place(links[s][i], i * LOOP / LINKS + way);
            for (int i = 0; i < 4; i++) wheels[s][i].xRot = way / WHEEL_R[i];
        }
    }

    private static void place(ModelPart link, float s) {
        s = Mth.positiveModulo(s, LOOP);
        float arc = Mth.PI * R, a, z, y;
        if (s < RUN) {
            a = 0;
            z = s - RUN / 2;
            y = R;
        } else if (s < RUN + arc) {
            a = (s - RUN) / R;
            z = RUN / 2 + R * Mth.sin(a);
            y = R * Mth.cos(a);
        } else if (s < 2 * RUN + arc) {
            a = Mth.PI;
            z = RUN / 2 - (s - RUN - arc);
            y = -R;
        } else {
            a = Mth.PI + (s - 2 * RUN - arc) / R;
            z = -RUN / 2 + R * Mth.sin(a);
            y = R * Mth.cos(a);
        }
        link.y = AXIS + y;
        link.z = z;
        link.xRot = a + Mth.PI;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        PartPose base = PartPose.offset(0, 24, 0);
        p.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-9, -15, -10, 18, 7, 18), base);
        p.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 26).addBox(-5, -8, -11, 10, 6, 22), base);
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            float x = s == 0 ? -7.5f : 7.5f;
            p.addOrReplaceChild("frame_" + side, CubeListBuilder.create().texOffs(0, 56).addBox(-1.5f, -7, -8, 3, 5, 16), PartPose.offset(x + (s == 0 ? 1 : -1), 24, 0));
            p.addOrReplaceChild("fender_" + side, CubeListBuilder.create().texOffs(64, 50).addBox(s == 0 ? -3 : 0, -11, -12, 3, 1, 24), PartPose.offset(s == 0 ? -9 : 9, 24, 0));
            for (int i = 0; i < 4; i++) {
                boolean sprocket = i < 2;
                int d = sprocket ? 5 : 4;
                PartDefinition w = p.addOrReplaceChild("wheel_" + side + i, CubeListBuilder.create(), PartPose.offset(x, AXIS, WHEEL_Z[i]));
                float hw = sprocket ? 1.5f : 1;
                CubeListBuilder box = CubeListBuilder.create().texOffs(sprocket ? 20 : 38, 92);
                w.addOrReplaceChild("a", box.addBox(-hw, -d / 2f, -d / 2f, 2 * hw, d, d), PartPose.ZERO);
                round(w, "b", sprocket ? 20 : 38, 92, -hw, -d / 2f, -d / 2f, 2 * hw, d, d, true);
            }
            for (int i = 0; i < LINKS; i++) {
                p.addOrReplaceChild("link_" + side + i, CubeListBuilder.create().texOffs(0, 92).addBox(-2.5f, -1, -1.5f, 5, 2, 3), PartPose.offset(x, 24, 0));
            }
        }
        p.addOrReplaceChild("rear_panel", CubeListBuilder.create().texOffs(0, 106).addBox(-5, -24, 6, 10, 9, 2), base);
        for (int k = 0; k < 3; k++) {
            float x = -4 + 3 * k;
            p.addOrReplaceChild("button_" + k, CubeListBuilder.create().texOffs(72 + 8 * k, 118).addBox(x, -16, 4, 2, 1, 2), base);
            for (int i = 0; i < SEGMENTS; i++) p.addOrReplaceChild("seg_" + k + i, CubeListBuilder.create().texOffs(8 * k, 118).addBox(x, -17 - i, 8, 2, 1, 1), base);
        }
        p.addOrReplaceChild("contact_l", CubeListBuilder.create().texOffs(24, 118).addBox(-4, -13, 8, 2, 2, 1), base);
        p.addOrReplaceChild("contact_r", CubeListBuilder.create().texOffs(24, 118).addBox(2, -13, 8, 2, 2, 1), base);
        p.addOrReplaceChild("coupling", CubeListBuilder.create().texOffs(64, 118).addBox(-1, -13, 8, 2, 3, 2), base);
        p.addOrReplaceChild("rad_sensor", CubeListBuilder.create().texOffs(72, 40).addBox(-9, -18, 4, 4, 3, 4), base);
        p.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(92, 30).addBox(7, -24, 6, 1, 9, 1), base);
        round(p.addOrReplaceChild("mast", CubeListBuilder.create().texOffs(72, 8).addBox(-2, -4, -2, 4, 8, 4), PartPose.offset(0, 5, -5)), "mast", 72, 8, -2, -4, -2, 4, 8, 4, false);
        PartDefinition h = p.addOrReplaceChild("head", CubeListBuilder.create().texOffs(88, 0).addBox(-5, -5, -4, 10, 5, 8), PartPose.offset(0, 1, -5));
        h.addOrReplaceChild("bezel", CubeListBuilder.create().texOffs(72, 24).addBox(-3, -4, -5, 6, 3, 1), PartPose.ZERO);
        h.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(88, 16).addBox(-2, -4, -6, 4, 2, 1), PartPose.ZERO);
        h.addOrReplaceChild("lamp_l", CubeListBuilder.create().texOffs(100, 16).addBox(-5, -3, -5, 2, 2, 1), PartPose.ZERO);
        h.addOrReplaceChild("lamp_r", CubeListBuilder.create().texOffs(100, 16).addBox(3, -3, -5, 2, 2, 1), PartPose.ZERO);
        h.addOrReplaceChild("head_antenna", CubeListBuilder.create().texOffs(108, 16).addBox(3, -10, 2, 1, 5, 1), PartPose.ZERO);
        round(h.addOrReplaceChild("lidar", CubeListBuilder.create().texOffs(72, 30).addBox(-2, -3, -2, 4, 3, 4), PartPose.offset(-2, -5, 0)), "lidar", 72, 30, -2, -3, -2, 4, 3, 4, false);
        p.addOrReplaceChild("plate_top", CubeListBuilder.create().texOffs(0, 140).addBox(-5, -16, -9, 10, 1, 10), base);
        p.addOrReplaceChild("plate_l", CubeListBuilder.create().texOffs(44, 140).addBox(-10, -15, -8, 1, 4, 14), base);
        p.addOrReplaceChild("plate_r", CubeListBuilder.create().texOffs(44, 140).mirror().addBox(9, -15, -8, 1, 4, 14), base);
        PartDefinition b = p.addOrReplaceChild("boom", CubeListBuilder.create(), PartPose.offset(0, 17, -10));
        b.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(40, 106).addBox(-1, -3, -8, 2, 3, 8), PartPose.offset(-6, 0, 0));
        b.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(40, 106).addBox(-1, -3, -8, 2, 3, 8), PartPose.offset(6, 0, 0));
        round(b.addOrReplaceChild("strut_l", CubeListBuilder.create().texOffs(64, 106).addBox(-1, -5, -1, 2, 5, 2), PartPose.offset(-6, 5, -7)), "strut", 64, 106, -1, -5, -1, 2, 5, 2, false);
        round(b.addOrReplaceChild("strut_r", CubeListBuilder.create().texOffs(64, 106).addBox(-1, -5, -1, 2, 5, 2), PartPose.offset(6, 5, -7)), "strut", 64, 106, -1, -5, -1, 2, 5, 2, false);
        b.addOrReplaceChild("scoop", CubeListBuilder.create().texOffs(0, 124).addBox(-9, -2, -5, 18, 4, 8), PartPose.offset(0, 5, -7));
        return LayerDefinition.create(mesh, 128, 160);
    }

    private static void round(PartDefinition part, String name, int u, int v, float x, float y, float z, float w, float h, float d, boolean xAxis) {
        for (int k = 1; k < 4; k++) {
            float a = k * Mth.PI / 8;
            part.addOrReplaceChild(name + k, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(-0.03f * k)),
                    xAxis ? PartPose.rotation(a, 0, 0) : PartPose.rotation(0, a, 0));
        }
    }

    @Override
    public void setupAnim(DeconRobotEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        float bob = e.controlled() ? (float) Math.sin(age * 0.6f) * 0.06f : 0f;
        scoop.xRot = 0.08f + bob;
        armL.xRot = armR.xRot = bob * 0.5f;
        lidar.yRot = e.controlled() ? age * 0.5f : 0f;
        boolean plated = e.plated();
        plateTop.visible = plated;
        plateL.visible = plated;
        plateR.visible = plated;
        float[] level = {e.energy() / (float) DeconRobotEntity.BATTERY, e.agent() / (float) DeconRobotEntity.AGENT_MAX, e.waste() / (float) DeconRobotEntity.WASTE_MAX};
        for (int k = 0; k < 3; k++) {
            int lit = Mth.ceil(Mth.clamp(level[k], 0f, 1f) * SEGMENTS);
            for (int i = 0; i < SEGMENTS; i++) gauge[k][i].visible = i < lit;
        }
        head.zRot = e.broken() ? 0.45f : 0f;
        head.xRot = e.broken() ? 0.3f + (float) Math.sin(age * 0.15f) * 0.04f : (pitch - 8f) * Mth.DEG_TO_RAD;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }
}
