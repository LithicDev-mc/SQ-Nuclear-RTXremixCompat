package com.sqc.nuclearsq.turbine.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import javax.annotation.Nullable;

public class TurbineGeoModel extends GeoModel<TurbineControllerBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "geo/turbine.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/block/turbine.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "animations/turbine.animation.json");

    @Override
    public ResourceLocation getModelResource(TurbineControllerBlockEntity animatable, @Nullable GeoRenderer<TurbineControllerBlockEntity> renderer) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(TurbineControllerBlockEntity animatable, @Nullable GeoRenderer<TurbineControllerBlockEntity> renderer) { return TEXTURE; }

    @Deprecated
    @Override
    public ResourceLocation getModelResource(TurbineControllerBlockEntity animatable) { return MODEL; }

    @Deprecated
    @Override
    public ResourceLocation getTextureResource(TurbineControllerBlockEntity animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(TurbineControllerBlockEntity animatable) { return ANIMATION; }
}
