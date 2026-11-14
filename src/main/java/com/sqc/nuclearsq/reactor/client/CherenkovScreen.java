package com.sqc.nuclearsq.reactor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.network.CherenkovPayload;
import com.sqc.nuclearsq.reactor.Cherenkov;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public class CherenkovScreen extends Screen {
    private static final int STEP = 9;
    private static final int BAR_H = 10;

    private final BlockPos pos;
    private final int original;
    private int value, sent;
    private int barX;
    private double lastMouseX;

    public CherenkovScreen(BlockPos pos, int level) {
        super(Component.translatable("gui.nuclearsq.cherenkov"));
        this.pos = pos;
        this.original = level;
        this.value = level;
        this.sent = level;
    }

    @Override
    protected void init() {
        barX = width / 2 - original * STEP;
        lastMouseX = width / 2.0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || !(minecraft.level.getBlockEntity(pos) instanceof Cherenkov.Holder)
                || !minecraft.player.getMainHandItem().isEmpty()) {
            onClose();
            return;
        }
        if (value != sent) send(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        lastMouseX = mouseX;
        value = levelAt(mouseX);
        int y = height / 2 + 14, x2 = barX + Cherenkov.MAX * STEP;
        graphics.fill(barX - 3, y - 3, x2 + 4, y + BAR_H + 3, 0xC0101010);
        graphics.fill(barX, y, barX + value * STEP + 1, y + BAR_H, 0xFF4FA8FF);
        for (int i = 0; i <= Cherenkov.MAX; i++) {
            int x = barX + i * STEP;
            graphics.fill(x, y + BAR_H - 3, x + 1, y + BAR_H, i <= value ? 0xFF1C4E8A : 0xFF505050);
        }
        int hx = barX + value * STEP;
        graphics.fill(hx - 2, y - 4, hx + 3, y + BAR_H + 4, 0xFFFFFFFF);
        Component label = Component.empty().append(title).append(": " + value + "/" + Cherenkov.MAX);
        graphics.drawCenteredString(font, label, (barX + x2) / 2, y - 14, 0xFFFFFFFF);
    }

    private int levelAt(double mouseX) {
        return Mth.clamp((int) Math.round((mouseX - barX) / STEP), 0, Cherenkov.MAX);
    }

    private void finish(double mouseX) {
        value = levelAt(mouseX);
        if (value != sent) send(value);
        onClose();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (minecraft != null && minecraft.options.keyUse.matchesMouse(button)) {
            finish(mouseX);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            finish(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyUse.matches(keyCode, scanCode)) {
            finish(lastMouseX);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (sent != original) send(original);
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(int level) {
        PacketDistributor.sendToServer(new CherenkovPayload(pos, level));
        sent = level;
    }
}
