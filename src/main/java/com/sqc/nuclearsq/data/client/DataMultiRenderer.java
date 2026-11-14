package com.sqc.nuclearsq.data.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataEntry;
import com.sqc.nuclearsq.data.DataMultiBlockEntity;
import com.sqc.nuclearsq.data.DataPanelBlock;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

public class DataMultiRenderer implements BlockEntityRenderer<DataMultiBlockEntity> {
    private static final int FACE = 0xFF07100A, FRAME = 0xFF1C2226, GREEN = 0xFF41FF7A, AMBER = 0xFFFFB23A, DIM = 0xFF5F6B70;

    public DataMultiRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(DataMultiBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        if (com.sqc.nuclearsq.client.PanelCull.skip(be.getLevel(), be.getBlockPos(), net.minecraft.world.phys.AABB.unitCubeFromLowerCorner(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos())))) return;
        if (!be.getBlockState().hasProperty(DataPanelBlock.FACING)) return;
        PanelDraw d = PanelDraw.begin(pose, buffers, be.getBlockState().getValue(DataPanelBlock.FACING), 3 / 16f);
        d.fill(6, 6, 58, 58, FRAME);
        d.layer();
        d.fill(7, 7, 57, 57, FACE);
        d.layer();
        ControlWallBlockEntity.Tile t = be.tile();
        if (t == null) {
            d.center(Component.translatable("data.nuclearsq.screen.no_signal"), 32, 29, 0.42f, 46, AMBER);
            d.end();
            return;
        }
        d.textFit(t.name(), 9, 9, 0.3f, 40, DIM);
        d.fill(52, 9, 55, 12, DataEntry.color(t.state()));
        d.fill(9, 13.5f, 55, 14, FRAME);
        for (int i = 0; i < t.lines().size(); i++) {
            ControlWallBlockEntity.Line ln = t.lines().get(i);
            float y = 16 + i * 10.2f;
            Component value = Component.literal(ln.text());
            float vw = d.width(value) * 0.36f;
            d.textFit(ln.label(), 9, y + 0.6f, 0.28f, 44 - vw, DIM);
            d.right(value, 55, y, 0.36f, GREEN);
            if (!ln.scaled()) continue;
            d.fill(9, y + 5, 55, y + 6.2f, 0xFF0B3319);
            d.layer();
            d.fill(9, y + 5, 9 + 46 * ln.frac(), y + 6.2f, ln.frac() >= 0.9f ? AMBER : GREEN);
        }
        d.end();
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
