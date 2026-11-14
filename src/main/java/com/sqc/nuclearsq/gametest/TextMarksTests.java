package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.TextMarks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class TextMarksTests {
    private TextMarksTests() {}

    private static final String FLOOR = "floor";

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static List<Style> styles(String text) {
        List<Style> out = new ArrayList<>();
        TextMarks.parse(text, null, "nsq:").getVisualOrderText().accept((i, st, cp) -> out.add(st));
        return out;
    }

    @GameTest(template = FLOOR, batch = "textmarks", timeoutTicks = 20)
    public static void inlineMarks(GameTestHelper h) {
        String raw = "a {c=gold}b{/c} {hover=Tipp}c{/hover} {item=steel_sheet_sqc} {item=nope_x} **d**";
        String plain = TextMarks.parse(raw, null, "nsq:").getString();
        check(!plain.contains("{") && !plain.contains("*"), "Marken im Text: " + plain);
        check(plain.contains("nope_x"), "unbekannte Item-Id muss als Rohtext bleiben: " + plain);
        List<Style> st = styles(raw);
        check(st.get(0).getColor() == null, "a ohne Farbe");
        check(st.get(2).getColor().getValue() == 0xE8A33D, "b gold");
        check(st.get(3).getColor() == null, "Farbe nach {/c} zurueck");
        check(st.get(4).getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString().equals("Tipp"), "c Hover Tipp");
        check(st.get(5).getHoverEvent() == null, "Hover nach {/hover} zurueck");
        check((TextMarks.ITEM + NuclearSQ.MODID + ":steel_sheet_sqc").equals(st.get(6).getInsertion()), "Item-Lauf mit Id");
        check(st.get(st.size() - 1).getColor().getValue() == 0xFFFFFF, "fett weiss");
        List<Style> f = styles("{f=zauber}x{/f}{f=nix}y{/f}");
        check("minecraft:alt".equals(f.get(0).getFont().toString()), "Schrift zauber = minecraft:alt");
        check(f.get(1).getFont().equals(Style.DEFAULT_FONT), "unbekannte Schrift normal");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "textmarks", timeoutTicks = 20)
    public static void blockMarksAndColors(GameTestHelper h) {
        for (String ok : List.of("{info}", "{info=rot}", "{/info}", "{drop=Titel {c=rot}x{/c}}", "{/drop}", "{recipe=steel_sheet_sqc}"))
            check(TextMarks.BLOCK.matcher(ok).matches(), "Block-Marke erkannt: " + ok);
        for (String no : List.of("Text {info}", "{c=rot}", "{recipe=a} dahinter"))
            check(!TextMarks.BLOCK.matcher(no).matches(), "keine Block-Marke: " + no);
        check(TextMarks.color("rot") == 0xFF6B6B, "rot");
        check(TextMarks.color("#12ab34") == 0x12AB34, "Hex");
        check(TextMarks.color("nichts") == null, "unbekannte Farbe");
        check(TextMarks.item("steel_sheet_sqc") != null && TextMarks.item("minecraft:stone") != null && TextMarks.item("nope_x") == null, "Item-Suche");
        h.succeed();
    }
}
