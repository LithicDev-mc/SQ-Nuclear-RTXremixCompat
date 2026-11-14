package com.sqc.nuclearsq.client;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ItemInfoTooltip {
    private ItemInfoTooltip() {}

    private static final int WIDTH = 220;

    public static String infoKey(ItemStack st) {
        return st.getDescriptionId() + ".info";
    }

    public static boolean wantsInfo(String path) {
        return !path.startsWith("header_") && !path.equals("spacer_sqc");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTooltip(ItemTooltipEvent e) {
        if (e.getEntity() == null) return;
        ItemStack st = e.getItemStack();
        var id = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (!id.getNamespace().equals(NuclearSQ.MODID) || !wantsInfo(id.getPath())) return;
        String key = infoKey(st);
        if (!I18n.exists(key)) return;

        List<Component> add = new ArrayList<>();
        if (Screen.hasShiftDown()) {
            var font = Minecraft.getInstance().font;
            for (String para : I18n.get(key).split("\n")) {
                for (FormattedText line : font.getSplitter().splitLines(para, WIDTH, Style.EMPTY)) {
                    add.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            add.add(Component.translatable("tooltip.nuclearsq.info.hint").withStyle(ChatFormatting.DARK_GRAY));
        }

        List<Component> tip = e.getToolTip();
        int at = tip.size();
        if (e.getFlags().isAdvanced()) {
            String idText = id.toString();
            for (int i = 1; i < tip.size(); i++) {
                if (tip.get(i).getString().equals(idText)) {
                    at = i;
                    break;
                }
            }
        }
        tip.addAll(at, add);
    }
}
