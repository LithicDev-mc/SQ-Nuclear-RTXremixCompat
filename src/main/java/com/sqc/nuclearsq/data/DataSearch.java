package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DataSearch {
    private DataSearch() {}

    public static String fold(String s) {
        s = s.toLowerCase(Locale.ROOT).replace("ß", "ss").replace("-", "").replace('_', ' ');
        s = s.replace("ä", "a").replace("ö", "o").replace("ü", "u");
        return s.replace("ae", "a").replace("oe", "o").replace("ue", "u");
    }

    public static List<String> terms(String query) {
        List<String> out = new ArrayList<>();
        for (String t : fold(query).split("\\s+")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    public static boolean hasAll(String text, List<String> terms) {
        String folded = fold(text);
        String compact = folded.replace(" ", "");
        for (String t : terms) if (!folded.contains(t) && !compact.contains(t)) return false;
        return true;
    }
}
