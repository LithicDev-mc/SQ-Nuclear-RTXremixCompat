package com.sqc.nuclearsq.progression;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class TextMarks {
    private TextMarks() {}

    public static final String ITEM = "nsqi:";
    public static final String ICON_GAP = "   ";

    private static final Pattern TAG = Pattern.compile("\\*\\*|\\{(c|hover|item|f)=([^}]*)\\}|\\{/(c|hover|f)\\}");
    public static final Pattern BLOCK = Pattern.compile("^\\{(info|/info|drop|/drop|recipe)(?:=(.*))?\\}$");

    private static final Map<String, Integer> COL = Map.of("gold", 0xE8A33D, "cyan", 0xB4C2FF, "rot", 0xFF6B6B, "gruen", 0x5FE08A,
            "grau", 0x9AA0A8, "weiss", 0xFFFFFF, "orange", 0xFF9A4D, "lila", 0xC58BFF);

    private static final Map<String, String> FONT = Map.of("standard", "default", "uniform", "uniform", "zauber", "alt", "illager", "illageralt");

    public static Integer color(String name) {
        String n = name.trim();
        if (n.matches("#[0-9a-fA-F]{6}")) return Integer.parseInt(n.substring(1), 16);
        return COL.get(n.toLowerCase(java.util.Locale.ROOT));
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id.trim() : NuclearSQ.MODID + ":" + id.trim());
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).filter(i -> i != Items.AIR).orElse(null);
    }

    public static MutableComponent parse(String s, ToIntFunction<String> link, String linkPrefix) {
        MutableComponent out = Component.empty();
        ArrayDeque<Style> open = new ArrayDeque<>();
        Style cur = Style.EMPTY;
        boolean bold = false;
        int pos = 0;
        Matcher m = TAG.matcher(s);
        while (true) {
            boolean more = m.find();
            String part = s.substring(pos, more ? m.start() : s.length());
            if (!part.isEmpty()) {
                Style st = bold ? cur.withColor(0xFFFFFF).withBold(true) : cur;
                int idx = bold && link != null ? link.applyAsInt(part) : -1;
                if (idx >= 0) st = cur.withColor(0xB4C2FF).withUnderlined(true).withInsertion(linkPrefix + idx);
                out.append(Component.literal(part).withStyle(st));
            }
            if (!more) return out;
            pos = m.end();
            String tag = m.group(1), arg = m.group(2);
            if (m.group(3) != null) {
                if (!open.isEmpty()) cur = open.pop();
            } else if (tag == null) {
                bold = !bold;
            } else if (tag.equals("item")) {
                Item it = item(arg);
                out.append(it == null ? Component.literal(arg).withStyle(cur)
                        : Component.literal(ICON_GAP).append(it.getDescription()).withStyle(cur.withInsertion(ITEM + BuiltInRegistries.ITEM.getKey(it))));
            } else {
                open.push(cur);
                if (tag.equals("c")) {
                    Integer c = color(arg);
                    if (c != null) cur = cur.withColor(c);
                } else if (tag.equals("f")) {
                    String f = FONT.get(arg.trim().toLowerCase(java.util.Locale.ROOT));
                    if (f != null) cur = cur.withFont(ResourceLocation.withDefaultNamespace(f));
                } else {
                    cur = cur.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(arg)));
                }
            }
        }
    }
}
