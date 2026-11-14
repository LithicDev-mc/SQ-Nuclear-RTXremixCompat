package com.sqc.nuclearsq.data.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataInstrumentBlock;
import com.sqc.nuclearsq.data.DataInstrumentBlockEntity;
import com.sqc.nuclearsq.data.DataPanelBlock;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class InstrumentRenderer implements BlockEntityRenderer<DataInstrumentBlockEntity> {
    private static final int FACE = 0xFF07100A, FRAME = 0xFF1C2226, SCALE = 0xFFB9C4C9, RED = 0xFFE04A3C;
    private static final int GREEN = 0xFF41FF7A, AMBER = 0xFFFFB23A, DIM = 0xFF5F6B70;
    private final java.util.Map<Long, Float> shown = new java.util.HashMap<>();

    public InstrumentRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(DataInstrumentBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), net.minecraft.world.phys.AABB.unitCubeFromLowerCorner(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos())))) return;
        if (!(be.getBlockState().getBlock() instanceof DataInstrumentBlock block)) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 3 / 16f);
        d.fill(6, 6, 58, 58, FRAME);
        d.layer();
        d.fill(7, 7, 57, 57, FACE);
        d.layer();
        if (!be.linked()) {
            d.center(Component.translatable("data.nuclearsq.screen.no_signal"), 32, 29, 0.42f, 46, AMBER);
            d.end();
            return;
        }
        d.center(be.name(), 32, 9, 0.3f, 46, DIM);
        int stateColor = DataEntry.color(be.state());
        d.fill(51, 9, 54, 12, stateColor);
        Component value = Component.literal(be.text());
        switch (block.kind) {
            case DataInstrumentBlock.GAUGE -> {
                float cx = 32, cy = 38, r = 19;
                for (int i = 0; i <= 10; i++) {
                    float deg = -120 + i * 24;
                    d.tick(cx, cy, deg, i % 5 == 0 ? r - 5 : r - 3, r, 0.5f, i >= 8 ? RED : SCALE);
                }
                d.layer();
                float target = be.scaled() ? be.frac() : 0;
                long key = be.getBlockPos().asLong();
                float cur = shown.getOrDefault(key, target);
                cur += (target - cur) * Mth.clamp(0.15f * (1 + partialTick), 0, 1);
                shown.put(key, cur);
                d.needle(cx, cy, -120 + cur * 240, r - 2, 0.6f, 0xFFFF8040);
                d.layer();
                d.fill(cx - 1.5f, cy - 1.5f, cx + 1.5f, cy + 1.5f, SCALE);
                d.center(value, 32, 44, 0.42f, 46, GREEN);
                d.center(be.label(), 32, 51, 0.28f, 46, DIM);
            }
            case DataInstrumentBlock.BAR -> {
                d.center(be.label(), 32, 16, 0.3f, 46, DIM);
                d.fill(11, 25, 53, 37, 0xFF020604);
                d.layer();
                float f = be.scaled() ? be.frac() : 0;
                int c = f >= 0.9f ? RED : f >= 0.75f ? AMBER : GREEN;
                d.fill(12, 26, 12 + 40 * f, 36, c);
                d.layer();
                for (int i = 1; i < 10; i++) d.fill(12 + i * 4, 26, 12.4f + i * 4, i % 5 == 0 ? 36 : 28, 0x80000000);
                d.center(value, 32, 42, 0.42f, 46, GREEN);
                if (be.scaled()) d.center(Component.literal(Math.round(f * 100) + " %"), 32, 50, 0.3f, 46, DIM);
            }
            default -> {
                d.center(be.label(), 32, 17, 0.3f, 46, DIM);
                d.fill(10, 25, 54, 43, 0xFF020604);
                d.layer();
                d.center(value, 32, 29, 0.85f, 42, stateColor == 0xFF8792A0 ? GREEN : stateColor);
                if (be.scaled()) {
                    d.fill(10, 47, 54, 49, 0xFF0B3319);
                    d.layer();
                    d.fill(10, 47, 10 + 44 * be.frac(), 49, GREEN);
                }
            }
        }
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
