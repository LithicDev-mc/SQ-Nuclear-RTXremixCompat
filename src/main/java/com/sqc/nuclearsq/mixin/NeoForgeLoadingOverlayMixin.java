package com.sqc.nuclearsq.mixin;

import com.sqc.nuclearsq.client.MenuStyle;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeoForgeLoadingOverlay.class)
public class NeoForgeLoadingOverlayMixin {
    @Shadow
    private float currentProgress;
    @Shadow
    private long fadeOutStart;

    @Inject(method = "render", at = @At("TAIL"))
    private void nuclearsq$startScreen(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        float t = fadeOutStart > -1L ? (Util.getMillis() - fadeOutStart) / 1000f : -1f;
        MenuStyle.startScreen(currentProgress, 1f - Mth.clamp(t - 1f, 0f, 1f));
    }
}
