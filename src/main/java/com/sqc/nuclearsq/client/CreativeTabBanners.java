package com.sqc.nuclearsq.client;

import com.sqc.nuclearsq.registry.ModCreativeTabs;

import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class CreativeTabBanners {
    private CreativeTabBanners() {}

    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("fuel", 0xE6C828), Map.entry("hulls", 0xC8783C),
            Map.entry("water", 0x3C6EE6), Map.entry("parts", 0xB48C50), Map.entry("modules", 0x9B6EDC),
            Map.entry("machines", 0xE68228), Map.entry("ores", 0x64C864),
            Map.entry("transport", 0x2F97C8), Map.entry("data", 0x34CF77),
            Map.entry("protection", 0xE6C828));

    private static String group(ItemStack st) {
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? "" : cd.copyTag().getString("g");
    }

    private static String idOf(ItemStack st) {
        var key = BuiltInRegistries.ITEM.getKey(st.getItem());
        return key.getNamespace().equals(NuclearSQ.MODID) ? key.getPath() : "";
    }

    @SubscribeEvent
    public static void onTooltipHide(RenderTooltipEvent.Pre e) {
        if (idOf(e.getItemStack()).equals("spacer_sqc") && group(e.getItemStack()).isEmpty()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        String id = idOf(e.getItemStack());
        String g = id.startsWith("header_") ? id.substring(7, id.length() - 4)
                : id.equals("spacer_sqc") ? group(e.getItemStack()) : "";
        if (g.isEmpty()) return;
        var tip = e.getToolTip();
        tip.clear();
        tip.add(Component.translatable("item.nuclearsq.header_" + g + "_sqc").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String line : Component.translatable("tooltip.nuclearsq.group." + g).getString().split("\\n"))
            tip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof CreativeModeInventoryScreen s)) return;
        GuiGraphics g = e.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < 45 && i < s.getMenu().slots.size(); i++) {
            Slot slot = s.getMenu().slots.get(i);
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            var key = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (!key.getNamespace().equals(NuclearSQ.MODID) || !key.getPath().startsWith("header_")) continue;
            if (i % 9 != 0) continue;
            String id = key.getPath().substring("header_".length(), key.getPath().length() - "_sqc".length());
            int rgb = COLORS.getOrDefault(id, 0x888888);
            int x = s.getGuiLeft() + slot.x - 1, y = s.getGuiTop() + slot.y - 1, w = 9 * 18, h = 18;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | brighten(rgb, 1.0f), 0xFF000000 | brighten(rgb, 0.55f));
            g.drawString(font, st.getHoverName().getString().replace("§6", ""), x + 6, y + 5, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    private static int brighten(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), gr = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (gr << 8) | b;
    }
}
