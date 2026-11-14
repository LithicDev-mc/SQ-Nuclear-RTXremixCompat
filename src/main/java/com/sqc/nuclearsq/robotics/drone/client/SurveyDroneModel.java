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
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, 0, -2, 8, 3, 4)
                .texOffs(4, 21).addBox(-2, 0, -4, 4, 3, 8, new CubeDeformation(-0.01f, -0.08f, -0.01f)), PartPose.offset(0, 18, 0));
        for (int s = 0; s < 2; s++) body.addOrReplaceChild("diag" + s, CubeListBuilder.create().texOffs(30, 21)
                .addBox(-4, 0, -1.5f, 8, 3, 3, new CubeDeformation(-0.02f, -0.16f, -0.02f)), PartPose.rotation(0, (s == 0 ? 1 : -1) * Mth.PI / 4, 0));
        root.addOrReplaceChild("sensor", CubeListBuilder.create().texOffs(26, 14).addBox(-1.5f, 0, -1.5f, 3, 2, 3), PartPose.offset(0, 21, 0));
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -3 : 3;
            root.addOrReplaceChild("skid" + s, CubeListBuilder.create().texOffs(32, 0).addBox(-0.5f, 0, -4, 1, 1, 8), PartPose.offset(x, 23, 0));
            root.addOrReplaceChild("strut" + s, CubeListBuilder.create().texOffs(0, 18).addBox(-0.5f, 0, -2.5f, 1, 2, 1)
                    .texOffs(0, 18).addBox(-0.5f, 0, 1.5f, 1, 2, 1), PartPose.offset(x, 21, 0));
            root.addOrReplaceChild("arm" + s, CubeListBuilder.create().texOffs(0, 11).addBox(-9, 0, -1, 18, 1, 2),
                    PartPose.offsetAndRotation(0, 18.5f, 0, 0, (s == 0 ? 1 : -1) * Mth.PI / 4, 0));
        }
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * R, z = (i < 2 ? -1 : 1) * R;
            root.addOrReplaceChild("motor" + i, CubeListBuilder.create().texOffs(0, 14).addBox(-1, -2, -1, 2, 2, 2), PartPose.offset(x, 18.5f, z));
            PartDefinition rotor = root.addOrReplaceChild("rotor" + i, CubeListBuilder.create().texOffs(8, 14).addBox(-4, -1, -0.5f, 8, 1, 1),
                    PartPose.offset(x, 16.5f, z));
            rotor.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(8, 14).addBox(-4, -2, -0.5f, 8, 1, 1),
                    PartPose.rotation(0, Mth.PI / 2, 0));
        }
        return LayerDefinition.create(mesh, 64, 32);
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
