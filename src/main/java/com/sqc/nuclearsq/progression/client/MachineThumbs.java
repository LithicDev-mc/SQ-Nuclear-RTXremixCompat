package com.sqc.nuclearsq.progression.client;

import java.util.HashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MachineThumbs {
    private MachineThumbs() {}

    private static final int BIG = 128, SMALL = 64;
    private static final Map<String, String> NODE_IMAGE = Map.of("fuel_rod_head_sqc", "fuel_rod_assembled_sqc");
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();
    private static ResourceManager checkedWith;

    public static ResourceLocation location(String itemId, boolean small) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID,
                "textures/gui/compendium/machines/" + itemId + (small ? "_small" : "") + ".png");
    }

    public static boolean has(String itemId) {
        return exists(location(itemId, false)) && exists(location(itemId, true));
    }

    private static boolean exists(ResourceLocation rl) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        if (rm != checkedWith) {
            EXISTS.clear();
            checkedWith = rm;
        }
        return EXISTS.computeIfAbsent(rl, k -> rm.getResource(k).isPresent());
    }

    public static void drawStack(GuiGraphics g, ItemStack st, int x, int y) {
        if (st.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (id.getNamespace().equals(NuclearSQ.MODID) && has(id.getPath())) drawImage(g, id.getPath(), x - 1, y - 1, 18, 1f);
        else g.renderItem(st, x, y);
    }

    public static boolean draw(GuiGraphics g, String itemId, int x, int y, int size, float zoom) {
        String img = NODE_IMAGE.getOrDefault(itemId, itemId);
        if (has(img)) return drawImage(g, img, x, y, size, zoom);
        return drawImage(g, itemId, x, y, size, zoom);
    }

    private static boolean drawImage(GuiGraphics g, String itemId, int x, int y, int size, float zoom) {
        if (has(itemId)) {
            double px = size * zoom * Minecraft.getInstance().getWindow().getGuiScale();
            boolean small = px <= (BIG + SMALL) / 2.0;
            int n = small ? SMALL : BIG;
            g.blit(location(itemId, small), x, y, size, size, 0, 0, n, n, n, n);
            return true;
        }
        var it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, itemId));
        if (it == Items.AIR) return false;
        g.renderItem(new ItemStack(it), x + (size - 16) / 2, y + (size - 16) / 2);
        return true;
    }
}
