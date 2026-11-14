package com.sqc.nuclearsq.robotics.pet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.pet.RoboPetEntity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RoboPetModel extends EntityModel<RoboPetEntity> {
    public static final ModelLayerLocation DOG = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "robo_dog"), "main");
    public static final ModelLayerLocation CAT = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "robo_cat"), "main");
    private final float bodyY;
    private final ModelPart root, body, head, tail, visor, legFl, legFr, legBl, legBr;
    private final ModelPart[] legs, lows, ears, segs;
    private static final float[] WALK = {0f, 0.5f, 0.75f, 0.25f}, TROT = {0f, 0.5f, 0.5f, 0f}, GALLOP = {0f, 0.1f, 0.6f, 0.5f};
    private static final float[][] DOG_POSE = {new float[10], {4.2f, -0.45f, -0.1f, 0f, -1.4f, 2.6f, 0f, 7.25f, 0.45f, -0.6f},
            {6f, 0f, -1.4f, 0f, 1.4f, 0f, 6f, 6f, 0f, 0.05f}, {4.2f, -0.45f, -0.1f, 0f, -1.4f, 2.6f, 0f, 7.25f, 0.45f, -0.6f},
            {1.4f, 0.35f, -0.9f, 0f, 0f, 0f, 3.8f, 0f, -0.45f, 0.9f}};
    private static final float[][] CAT_POSE = {new float[10], {2.6f, -0.45f, -0.1f, 0f, -1.4f, 2.6f, 0f, 5.25f, 0.45f, -0.9f},
            {6f, 0f, -1.4f, 0f, 1.4f, 0f, 6f, 6f, 0f, -0.3f}, {2.6f, -0.45f, -0.1f, 0f, -1.4f, 2.6f, 0f, 5.25f, 0.45f, -0.9f},
            {1f, 0.35f, -0.9f, 0f, 0f, 0f, 3f, 0f, -0.45f, 1f}};

    public RoboPetModel(ModelPart root, boolean cat) {
        this.root = root;
        this.bodyY = cat ? 16 : 14;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.tail = body.getChild("tail");
        this.visor = head.getChild("visor");
        this.legFl = root.getChild("leg_fl");
        this.legFr = root.getChild("leg_fr");
        this.legBl = root.getChild("leg_bl");
        this.legBr = root.getChild("leg_br");
        this.legs = new ModelPart[] {legFl, legFr, legBl, legBr};
        this.lows = new ModelPart[] {legFl.getChild("leg_fl_low"), legFr.getChild("leg_fr_low"), legBl.getChild("leg_bl_low"), legBr.getChild("leg_br_low")};
        this.ears = new ModelPart[] {head.getChild("ear_l"), head.getChild("ear_r")};
        ModelPart t2 = tail.getChild("tail2"), t3 = t2.getChild("tail3");
        this.segs = cat ? new ModelPart[] {t2, t3, t3.getChild("tail4")} : new ModelPart[] {t2, t3};
    }

    public static LayerDefinition createLayer(boolean cat) {
        return cat ? createCat() : createDog();
    }

    private static LayerDefinition createDog() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6f, -7f, -11f, 12f, 7f, 22f).texOffs(68, 0).addBox(-4f, -9f, -11f, 8f, 2f, 22f).texOffs(0, 52).addBox(-4f, -10f, -8f, 8f, 1f, 16f).texOffs(48, 52).addBox(-4f, 0f, -7f, 8f, 1f, 14f).texOffs(32, 69).addBox(-5f, -10f, -12f, 10f, 9f, 2f).texOffs(0, 111).addBox(-6f, -3f, -13f, 12f, 3f, 2f).texOffs(56, 69).addBox(-3f, -11f, -3f, 6f, 2f, 8f).texOffs(88, 81).addBox(-2f, -14f, -1f, 4f, 3f, 4f).texOffs(62, 98).addBox(2f, -12f, 6f, 2f, 2f, 4f).texOffs(74, 98).addBox(-4f, -12f, 6f, 2f, 2f, 4f).texOffs(92, 52).addBox(6f, -7f, -10f, 2f, 6f, 6f).texOffs(108, 52).addBox(-8f, -7f, -10f, 2f, 6f, 6f).texOffs(0, 69).addBox(6f, -7f, 4f, 2f, 6f, 6f).texOffs(16, 69).addBox(-8f, -7f, 4f, 2f, 6f, 6f), PartPose.offset(0f, 14f, 0f));
        PartDefinition body_bv10 = body.addOrReplaceChild("body_bv10", CubeListBuilder.create().texOffs(0, 29).addBox(-3f, 0f, -11f, 3f, 1f, 22f), PartPose.offsetAndRotation(6f, -7f, 0f, 0f, 0f, 0.7854f));
        PartDefinition body_bv00 = body.addOrReplaceChild("body_bv00", CubeListBuilder.create().texOffs(0, 29).addBox(0f, 0f, -11f, 3f, 1f, 22f), PartPose.offsetAndRotation(-6f, -7f, 0f, 0f, 0f, -0.7854f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(50, 29).addBox(-5f, -5f, -9f, 10f, 9f, 9f).texOffs(80, 111).addBox(-5f, -6f, -10f, 10f, 2f, 1f).texOffs(84, 69).addBox(-3f, 0f, -15f, 6f, 4f, 6f).texOffs(102, 111).addBox(-2f, 0f, -16f, 4f, 2f, 1f).texOffs(86, 98).addBox(-3f, 4f, -14f, 6f, 1f, 5f).texOffs(108, 69).addBox(5f, -2f, -6f, 2f, 5f, 5f).texOffs(0, 81).addBox(-7f, -2f, -6f, 2f, 5f, 5f), PartPose.offset(0f, -7f, -12f));
        PartDefinition visor = head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(40, 111).addBox(1f, -3f, -10f, 3f, 3f, 1f).texOffs(48, 111).addBox(-4f, -3f, -10f, 3f, 3f, 1f), PartPose.offset(0f, 0f, 0f));
        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(32, 81).addBox(-1f, -4.5f, -2f, 2f, 5f, 3f).texOffs(104, 81).addBox(-1f, -8.5f, -2f, 2f, 4f, 3f), PartPose.offsetAndRotation(4f, -5f, -4f, 0f, 0f, 0.15f));
        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(42, 81).addBox(-1f, -4.5f, -2f, 2f, 5f, 3f).texOffs(114, 81).addBox(-1f, -8.5f, -2f, 2f, 4f, 3f), PartPose.offsetAndRotation(-4f, -5f, -4f, 0f, 0f, -0.15f));
        PartDefinition antenna = head.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(52, 81).addBox(0f, -7f, 0f, 1f, 7f, 1f).texOffs(28, 111).addBox(-1f, -9f, -1f, 3f, 2f, 3f), PartPose.offset(3f, -5f, -1f));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(14, 81).addBox(-2f, -2f, -1f, 4f, 4f, 5f), PartPose.offset(0f, -6.5f, 11f));
        PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(56, 81).addBox(-1f, -1f, -1f, 2f, 2f, 6f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.3f, 0f, 0f));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(72, 81).addBox(-2f, -2f, -1f, 4f, 4f, 4f), PartPose.offsetAndRotation(0f, 0f, 5f, 0.3f, 0f, 0f));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(0, 91).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(108, 98).addBox(-1f, 2f, -1f, 2f, 4f, 2f).texOffs(16, 91).addBox(-3f, -1f, -2f, 1f, 3f, 4f).texOffs(56, 111).addBox(-1f, 2f, 1f, 2f, 3f, 1f), PartPose.offset(-4.5f, 14f, -7f));
        PartDefinition leg_fl_low = leg_fl.addOrReplaceChild("leg_fl_low", CubeListBuilder.create().texOffs(0, 105).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(112, 111).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(26, 91).addBox(-2f, 2f, -3f, 4f, 2f, 5f).texOffs(16, 105).addBox(-3f, -1f, -2f, 1f, 2f, 4f), PartPose.offset(0f, 6f, 0f));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(44, 91).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(26, 105).addBox(-1f, 2f, -1f, 2f, 4f, 2f).texOffs(60, 91).addBox(2f, -1f, -2f, 1f, 3f, 4f).texOffs(62, 111).addBox(-1f, 2f, 1f, 2f, 3f, 1f), PartPose.offset(4.5f, 14f, -7f));
        PartDefinition leg_fr_low = leg_fr.addOrReplaceChild("leg_fr_low", CubeListBuilder.create().texOffs(34, 105).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(120, 111).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(70, 91).addBox(-2f, 2f, -3f, 4f, 2f, 5f).texOffs(50, 105).addBox(2f, -1f, -2f, 1f, 2f, 4f), PartPose.offset(0f, 6f, 0f));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create().texOffs(88, 91).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(60, 105).addBox(-1f, 2f, -1f, 2f, 4f, 2f).texOffs(104, 91).addBox(-3f, -1f, -2f, 1f, 3f, 4f).texOffs(68, 111).addBox(-1f, 2f, 1f, 2f, 3f, 1f), PartPose.offset(-4.5f, 14f, 7f));
        PartDefinition leg_bl_low = leg_bl.addOrReplaceChild("leg_bl_low", CubeListBuilder.create().texOffs(68, 105).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(0, 116).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(0, 98).addBox(-2f, 2f, -3f, 4f, 2f, 5f).texOffs(84, 105).addBox(-3f, -1f, -2f, 1f, 2f, 4f), PartPose.offset(0f, 6f, 0f));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create().texOffs(18, 98).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(94, 105).addBox(-1f, 2f, -1f, 2f, 4f, 2f).texOffs(34, 98).addBox(2f, -1f, -2f, 1f, 3f, 4f).texOffs(74, 111).addBox(-1f, 2f, 1f, 2f, 3f, 1f), PartPose.offset(4.5f, 14f, 7f));
        PartDefinition leg_br_low = leg_br.addOrReplaceChild("leg_br_low", CubeListBuilder.create().texOffs(102, 105).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(8, 116).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(44, 98).addBox(-2f, 2f, -3f, 4f, 2f, 5f).texOffs(118, 105).addBox(2f, -1f, -2f, 1f, 2f, 4f), PartPose.offset(0f, 6f, 0f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static LayerDefinition createCat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(48, 0).addBox(-4f, -6f, -10f, 8f, 4f, 20f).texOffs(0, 0).addBox(-2f, -8f, -10f, 4f, 8f, 20f).texOffs(82, 28).addBox(-2f, -9f, -7f, 4f, 1f, 14f).texOffs(0, 49).addBox(-2f, 0f, -6f, 4f, 1f, 12f).texOffs(32, 49).addBox(-5f, -9f, -11f, 10f, 10f, 2f).texOffs(56, 49).addBox(-2f, -12f, -2f, 4f, 3f, 6f).texOffs(98, 69).addBox(0f, -11f, 4f, 2f, 3f, 2f).texOffs(106, 69).addBox(-2f, -11f, 4f, 2f, 3f, 2f), PartPose.offset(0f, 16f, 0f));
        PartDefinition body_bv10 = body.addOrReplaceChild("body_bv10", CubeListBuilder.create().texOffs(0, 28).addBox(-3f, 0f, -10f, 3f, 1f, 20f), PartPose.offsetAndRotation(4f, -6f, 0f, 0f, 0f, 0.7854f));
        PartDefinition body_bv00 = body.addOrReplaceChild("body_bv00", CubeListBuilder.create().texOffs(0, 28).addBox(0f, 0f, -10f, 3f, 1f, 20f), PartPose.offsetAndRotation(-4f, -6f, 0f, 0f, 0f, -0.7854f));
        PartDefinition body_bv11 = body.addOrReplaceChild("body_bv11", CubeListBuilder.create().texOffs(0, 28).addBox(-3f, -1f, -10f, 3f, 1f, 20f), PartPose.offsetAndRotation(4f, -2f, 0f, 0f, 0f, -0.7854f));
        PartDefinition body_bv01 = body.addOrReplaceChild("body_bv01", CubeListBuilder.create().texOffs(0, 28).addBox(0f, -1f, -10f, 3f, 1f, 20f), PartPose.offsetAndRotation(-4f, -2f, 0f, 0f, 0f, 0.7854f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(46, 28).addBox(-5f, -4f, -8f, 10f, 8f, 8f).texOffs(86, 76).addBox(-5f, -5f, -9f, 10f, 2f, 1f).texOffs(28, 76).addBox(-2f, 0f, -9f, 4f, 3f, 1f).texOffs(24, 81).addBox(-1f, 0f, -10f, 2f, 1f, 1f), PartPose.offset(0f, -6f, -11f));
        PartDefinition visor = head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(38, 76).addBox(1f, -3f, -9f, 3f, 3f, 1f).texOffs(46, 76).addBox(-4f, -3f, -9f, 3f, 3f, 1f), PartPose.offset(0f, 0f, 0f));
        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(114, 69).addBox(-2f, -2.5f, -1f, 4f, 3f, 2f).texOffs(0, 76).addBox(-1f, -5.5f, -1f, 2f, 3f, 2f), PartPose.offsetAndRotation(4f, -4f, -3f, 0f, 0f, 0.2f));
        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(8, 76).addBox(-2f, -2.5f, -1f, 4f, 3f, 2f).texOffs(20, 76).addBox(-1f, -5.5f, -1f, 2f, 3f, 2f), PartPose.offsetAndRotation(-4f, -4f, -3f, 0f, 0f, -0.2f));
        PartDefinition whisk0_l = head.addOrReplaceChild("whisk0_l", CubeListBuilder.create().texOffs(30, 81).addBox(-0.5f, 0f, 0f, 6f, 1f, 1f), PartPose.offsetAndRotation(5f, 1f, -7f, 0f, 0f, -0.12f));
        PartDefinition whisk1_l = head.addOrReplaceChild("whisk1_l", CubeListBuilder.create().texOffs(44, 81).addBox(-0.5f, 0f, 0f, 6f, 1f, 1f), PartPose.offsetAndRotation(5f, 2f, -7f, 0f, 0f, 0.18f));
        PartDefinition whisk0_r = head.addOrReplaceChild("whisk0_r", CubeListBuilder.create().texOffs(58, 81).addBox(-5.5f, 0f, 0f, 6f, 1f, 1f), PartPose.offsetAndRotation(-5f, 1f, -7f, 0f, 0f, 0.12f));
        PartDefinition whisk1_r = head.addOrReplaceChild("whisk1_r", CubeListBuilder.create().texOffs(72, 81).addBox(-5.5f, 0f, 0f, 6f, 1f, 1f), PartPose.offsetAndRotation(-5f, 2f, -7f, 0f, 0f, -0.18f));
        PartDefinition tag = body.addOrReplaceChild("tag", CubeListBuilder.create().texOffs(108, 76).addBox(-1f, -2f, -12f, 2f, 2f, 1f), PartPose.offset(0f, 0f, 0f));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(76, 49).addBox(-2f, -2f, -1f, 4f, 4f, 5f), PartPose.offset(0f, -5.5f, 10f));
        PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(94, 49).addBox(-2f, -2f, -1f, 4f, 4f, 5f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.3f, 0f, 0f));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(112, 49).addBox(-1f, -1f, -1f, 2f, 2f, 5f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.3f, 0f, 0f));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(0, 62).addBox(-2f, -2f, -1f, 4f, 4f, 3f), PartPose.offsetAndRotation(0f, 0f, 4f, 0.3f, 0f, 0f));
        PartDefinition leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(14, 62).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(54, 76).addBox(-1f, 2f, -1f, 2f, 2f, 2f), PartPose.offset(-3f, 16f, -6f));
        PartDefinition leg_fl_low = leg_fl.addOrReplaceChild("leg_fl_low", CubeListBuilder.create().texOffs(34, 69).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(114, 76).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(30, 62).addBox(-2f, 2f, -3f, 4f, 2f, 5f), PartPose.offset(0f, 4f, 0f));
        PartDefinition leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(48, 62).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(62, 76).addBox(-1f, 2f, -1f, 2f, 2f, 2f), PartPose.offset(3f, 16f, -6f));
        PartDefinition leg_fr_low = leg_fr.addOrReplaceChild("leg_fr_low", CubeListBuilder.create().texOffs(50, 69).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(0, 81).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(64, 62).addBox(-2f, 2f, -3f, 4f, 2f, 5f), PartPose.offset(0f, 4f, 0f));
        PartDefinition leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create().texOffs(82, 62).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(70, 76).addBox(-1f, 2f, -1f, 2f, 2f, 2f), PartPose.offset(-3f, 16f, 6f));
        PartDefinition leg_bl_low = leg_bl.addOrReplaceChild("leg_bl_low", CubeListBuilder.create().texOffs(66, 69).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(8, 81).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(98, 62).addBox(-2f, 2f, -3f, 4f, 2f, 5f), PartPose.offset(0f, 4f, 0f));
        PartDefinition leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create().texOffs(0, 69).addBox(-2f, -1f, -2f, 4f, 3f, 4f).texOffs(78, 76).addBox(-1f, 2f, -1f, 2f, 2f, 2f), PartPose.offset(3f, 16f, 6f));
        PartDefinition leg_br_low = leg_br.addOrReplaceChild("leg_br_low", CubeListBuilder.create().texOffs(82, 69).addBox(-2f, -1f, -2f, 4f, 2f, 4f).texOffs(16, 81).addBox(-1f, 1f, -1f, 2f, 1f, 2f).texOffs(16, 69).addBox(-2f, 2f, -3f, 4f, 2f, 5f), PartPose.offset(0f, 4f, 0f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(RoboPetEntity e, float pos, float amt, float age, float netHeadYaw, float headPitch) {
        boolean cat = e.isCat(), dead = e.lying(), stalk = cat && e.sniff() > 0, sniff = !cat && e.sniff() > 0;
        int act = e.act();
        float pt = age - e.tickCount, k = e.actWeight(pt), pet = dead ? 0f : e.petWeight(pt), n = e.getId(), sitK = act == 1 || act == 3 ? k : 0f;
        float[] pose = (cat ? CAT_POSE : DOG_POSE)[Math.min(act, 4)];
        float g = amt < 0.6f ? Mth.clamp((amt - 0.25f) / 0.3f, 0f, 1f) : 1f + Mth.clamp((amt - 0.85f) / 0.15f, 0f, 1f), gal = Math.max(0f, g - 1f);
        float duty = g < 1f ? Mth.lerp(g, 0.65f, 0.5f) : Mth.lerp(gal, 0.5f, 0.35f);
        float leg = cat ? 0.25f : 0.3125f, stride = Math.max(2f * leg * 0.68f / duty, amt * 1.5f);
        float c = e.gait += (pos - e.gaitPos) * (Mth.TWO_PI / 4f / stride);
        e.gaitPos = pos;
        float sinA = Mth.clamp(duty * stride / (2f * leg), 0f, 0.84f), amp = Math.max(Mth.lerp(pt, e.ampWo, e.ampW), e.turnW);
        for (int i = 0; i < 4; i++) {
            float off = g < 1f ? Mth.lerp(g, WALK[i], TROT[i]) : Mth.lerp(gal, TROT[i], GALLOP[i]);
            float u = Mth.positiveModulo(c / Mth.TWO_PI - off, 1f), rot, knee;
            if (u < duty) {
                rot = (float) Math.asin(sinA * (2f * u / duty - 1f));
                knee = 0f;
            } else {
                float w = (u - duty) / (1f - duty);
                rot = (float) Math.asin(sinA) * Mth.cos(w * Mth.PI);
                knee = Mth.sin(w * Mth.PI) * (0.9f + 0.4f * Math.min(g, 1f));
            }
            int o = i < 2 ? 0 : 2;
            legs[i].xRot = Mth.lerp(k, rot * amp - (stalk ? 0.6f : 0f), pose[2 + o]);
            lows[i].xRot = Mth.lerp(k, knee * amp + (stalk ? 1.2f : 0f), pose[3 + o]);
            legs[i].y = bodyY + Mth.lerp(k, stalk ? 1.4f : 0f, pose[6 + o / 2]);
        }
        if (k == 0f && e.getY() - e.yo > 0.05) {
            legFl.xRot = legFr.xRot = -1f;
            legBl.xRot = legBr.xRot = 1f;
            for (ModelPart l : lows) l.xRot = 0f;
        }
        boolean happy = !cat && !dead && act == 0 && gal == 0f && e.getOwner() != null && e.distanceToSqr(e.getOwner()) < 16;
        float bob = Mth.sin(2f * c) * (0.2f + 0.6f * Math.min(g, 1f)) * amp + gal * Mth.sin(c) * 1.2f;
        body.y = bodyY + Mth.lerp(k, (stalk ? 2f : 0f) + bob, pose[0]) - pet * (cat ? 1.2f : 0.4f) + (act == 5 && !cat ? Mth.sin(age * 1.3f) * 0.3f * k : 0f);
        body.xRot = Mth.lerp(k, Mth.sin(c) * 0.12f * gal, pose[1]) - pet * (cat ? 0.08f : 0f);
        body.yRot = (happy || act == 7 ? Mth.sin(age * 0.7f) * 0.06f : stalk && amt < 0.1f ? Mth.sin(age * 1.1f) * 0.08f : 0f) + (cat ? 0f : Mth.sin(age * 0.8f) * 0.1f * pet);
        head.yRot = dead ? 0f : netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = dead ? 0.5f : headPitch * Mth.DEG_TO_RAD + pose[8] * k
                + (cat ? 0.15f * Math.min(amt * 3f, 1f) * (1f - gal) : Mth.cos(2f * c) * 0.06f * amp + 0.2f * gal);
        head.zRot = 0f;
        if (!dead && !cat) {
            float sn = sniff ? 1f : Mth.clamp((Mth.sin(age * 0.03f + n) - 0.5f) * 4f, 0f, 1f) * Mth.clamp(amt * 20f, 0f, 1f) * (1f - Mth.clamp((g - 0.3f) * 5f, 0f, 1f));
            head.xRot = Mth.lerp(sn, head.xRot, 0.6f + Mth.sin(age * (e.sniff() == 2 ? 1.2f : 0.6f)) * 0.12f);
            head.yRot = Mth.lerp(sn, head.yRot, Mth.sin(age * 0.15f) * 0.3f);
        }
        if (act == 3 && cat) {
            legFl.xRot = Mth.lerp(k, legFl.xRot, -1.3f);
            lows[0].xRot = Mth.lerp(k, lows[0].xRot, 1.5f);
            head.xRot += (0.5f + Mth.sin(age * 0.9f) * 0.08f) * k;
            head.yRot += 0.35f * k;
        } else if (act == 3) {
            legBr.xRot = Mth.lerp(k, legBr.xRot, -1.9f + Mth.sin(age * 2.4f) * 0.35f);
            lows[3].xRot = Mth.lerp(k, lows[3].xRot, 1f);
            head.zRot += 0.35f * k;
            head.yRot -= 0.3f * k;
        } else if (act == 5) {
            head.xRot += cat ? -0.55f * k : (-0.15f + Mth.sin(age * 1.3f) * 0.06f) * k;
        } else if (act == 6 && !cat) {
            head.zRot += 0.5f * k * ((e.getId() + e.tickCount / 200) % 2 == 0 ? 1f : -1f);
        }
        if (pet > 0f) {
            head.xRot = Mth.lerp(pet, head.xRot, cat ? -0.35f + Mth.sin(age * 0.2f) * 0.15f : -0.4f);
            if (cat) head.yRot = Mth.lerp(pet, head.yRot, Mth.sin(age * 0.2f) * 0.5f);
            head.zRot = Mth.lerp(pet, head.zRot, cat ? Mth.cos(age * 0.2f) * 0.3f : Mth.sin(age * 0.15f) * 0.25f);
        }
        float earX = (sniff || stalk ? 0.25f : 0f) - 0.8f * gal - (act == 4 || (act == 5 && cat) ? 0.5f * k : 0f) + 0.4f * pet;
        for (int s = 0; s < 2; s++) {
            float tw = Mth.sin(age * 0.07f + n + s * 2f);
            ears[s].xRot = dead ? -0.4f : earX + Math.max(0f, tw - 0.95f) * 20f * Mth.sin(age * 2f) * 0.3f;
            ears[s].yRot = cat && !dead ? Mth.sin(age * 0.03f + n + s * 3f) * 0.35f : 0f;
        }
        boolean wag = happy || act == 7;
        float base = cat ? (stalk ? -0.1f : Mth.lerp(gal, 1.2f, 0.2f)) : sniff ? 0.9f : Mth.lerp(gal, 0.4f, 0.1f);
        float wf = wag ? 0.7f : cat ? 0.06f : sniff ? 0.15f : 0.3f, wa = dead ? 0f : (wag ? 0.55f : cat ? 0.2f : 0.25f) * (1f - 0.8f * gal);
        if (pet > 0f) {
            base = Mth.lerp(pet, base, cat ? 1.5f : 0.5f);
            wf = Mth.lerp(pet, wf, cat ? 0.06f : 0.9f);
            wa = Mth.lerp(pet, wa, cat ? 0.05f : 0.7f);
        }
        tail.xRot = Mth.lerp(k, base, pose[9]);
        tail.yRot = Mth.sin(age * wf) * wa + (cat ? 0.6f * sitK : 0f);
        for (int j = 0; j < segs.length; j++) {
            segs[j].xRot = cat ? Mth.lerp(pet, (stalk ? 0f : (0.1f + 0.25f * j) * (1f - gal) * (1f - k) + 0.3f * k), 0.05f) : 0.3f;
            segs[j].yRot = Mth.sin(age * wf - 0.6f * (j + 1)) * wa * 0.6f + (cat ? 0.5f * sitK : 0f);
        }
        if (cat && !dead) segs[segs.length - 1].yRot += Mth.sin(age * (stalk || act == 6 ? 0.8f : 0.25f)) * (act == 6 ? 0.3f + 0.3f * k : 0.3f) + Mth.sin(age * 0.9f) * 0.15f * pet;
        boolean nap = act == 2 && k > 0.9f && Mth.sin(age * 0.01f + n) > 0f;
        visor.visible = !dead && !nap && pet < 0.6f && (e.tickCount + e.getId() * 7) % 90 > 2;
    }

    public void mouth(PoseStack ps) {
        body.translateAndRotate(ps);
        head.translateAndRotate(ps);
        ps.translate(0, bodyY == 16 ? 0.14 : 0.2, bodyY == 16 ? -0.62 : -0.9);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }
}
