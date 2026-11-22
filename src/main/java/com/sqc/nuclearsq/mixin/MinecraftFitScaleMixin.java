package com.sqc.nuclearsq.mixin;

import com.sqc.nuclearsq.client.FitScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFitScaleMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @ModifyArg(method = "resizeDisplay", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;setGuiScale(D)V"))
    private double nuclearsq$fitScale(double scale) {
        return FitScale.fit(screen, (int) scale);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void nuclearsq$fitBefore(@Nullable Screen next, CallbackInfo ci) {
        FitScale.apply((Minecraft) (Object) this, next);
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void nuclearsq$fitAfter(@Nullable Screen next, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.getWindow().getGuiScale() != FitScale.fit(screen, FitScale.base(mc))) mc.resizeDisplay();
    }
}
