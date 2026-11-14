package com.sqc.nuclearsq.monitor.client;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.sqc.nuclearsq.monitor.ImageMonitorBlock;
import com.sqc.nuclearsq.monitor.ImageMonitorBlockEntity;
import com.sqc.nuclearsq.monitor.ImageMonitorInputPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class ImageMonitorControlScreen extends Screen {
    private static final int HINT_HEIGHT = 14;
    private static final double MAX_DISTANCE = 8;

    private final BlockPos pos;
    private final Set<Integer> down = new HashSet<>();
    private int lastX = -1, lastY = -1;
    private long lastMove;

    public ImageMonitorControlScreen(BlockPos pos) {
        super(Component.translatable("block.nuclearsq.image_monitor_sqc"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        send(ImageMonitorInputPayload.FULL, width, height - HINT_HEIGHT, 1, "");
    }

    @Override
    public void removed() {
        send(ImageMonitorInputPayload.FULL, 0, 0, 0, "");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ImageMonitorBlockEntity main() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        ImageMonitorBlock.Screen screen = ImageMonitorBlock.screen(level, pos);
        if (screen == null) return null;
        return level.getBlockEntity(screen.origin()) instanceof ImageMonitorBlockEntity be ? be : null;
    }

    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null || main() == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE) {
            onClose();
        }
    }

    private float[] rect(ImageMonitorBlockEntity be) {
        int w = Math.max(1, be.resWidth()), h = Math.max(1, be.resHeight());
        float availW = width, availH = height - HINT_HEIGHT;
        float scale = Math.min(availW / w, availH / h);
        float dw = w * scale, dh = h * scale;
        return new float[]{(availW - dw) / 2, HINT_HEIGHT + (availH - dh) / 2, dw, dh};
    }

    private int[] pixelAt(double mx, double my) {
        ImageMonitorBlockEntity be = main();
        if (be == null) return null;
        float[] r = rect(be);
        if (mx < r[0] || my < r[1] || mx >= r[0] + r[2] || my >= r[1] + r[3]) return null;
        int px = (int) ((mx - r[0]) / r[2] * be.resWidth()) + 1;
        int py = (int) ((my - r[1]) / r[3] * be.resHeight()) + 1;
        return new int[]{Math.min(px, be.resWidth()), Math.min(py, be.resHeight())};
    }

    private void send(int kind, int x, int y, int arg, String text) {
        PacketDistributor.sendToServer(new ImageMonitorInputPayload(pos, kind, x, y, arg, text));
    }

    private static int button(int glfw) {
        return glfw + 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        ImageMonitorBlockEntity be = main();
        if (be != null) {
            ResourceLocation tex = ImageMonitorRenderer.textureOf(be);
            float[] r = rect(be);
            if (tex != null) {
                graphics.blit(tex, Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]),
                        0, 0, be.resWidth(), be.resHeight(), be.resWidth(), be.resHeight());
            }
        }
        graphics.drawCenteredString(font, Component.translatable("screen.nuclearsq.image_monitor.hint"), width / 2, 3, 0xFF8FA8B8);
        if (be != null) {
            String res = be.resWidth() + " × " + be.resHeight();
            graphics.drawString(font, res, width - font.width(res) - 4, 3, 0xFFE6F2FA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hasShiftDown()) {
            onClose();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hasControlDown()) {
            send(ImageMonitorInputPayload.CYCLE, 0, 0, 0, "");
            return true;
        }
        int[] p = pixelAt(mx, my);
        if (p != null) send(ImageMonitorInputPayload.TOUCH, p[0], p[1], button(button), "");
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int[] p = pixelAt(mx, my);
        if (p != null) send(ImageMonitorInputPayload.UP, p[0], p[1], button(button), "");
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int[] p = pixelAt(mx, my);
        if (p != null && (p[0] != lastX || p[1] != lastY)) {
            lastX = p[0];
            lastY = p[1];
            send(ImageMonitorInputPayload.DRAG, p[0], p[1], button(button), "");
        }
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int[] p = pixelAt(mx, my);
        long now = System.currentTimeMillis();
        if (p != null && (p[0] != lastX || p[1] != lastY) && now - lastMove >= 50) {
            lastX = p[0];
            lastY = p[1];
            lastMove = now;
            send(ImageMonitorInputPayload.MOVE, p[0], p[1], 0, "");
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int[] p = pixelAt(mx, my);
        if (p != null && scrollY != 0) send(ImageMonitorInputPayload.SCROLL, p[0], p[1], scrollY > 0 ? -1 : 1, "");
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        boolean held = !down.add(keyCode);
        send(ImageMonitorInputPayload.KEY, 0, 0, keyCode << 1 | (held ? 1 : 0), "");
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        down.remove(keyCode);
        send(ImageMonitorInputPayload.KEY_UP, 0, 0, keyCode, "");
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        send(ImageMonitorInputPayload.CHAR, 0, 0, 0, String.valueOf(codePoint));
        return true;
    }
}
