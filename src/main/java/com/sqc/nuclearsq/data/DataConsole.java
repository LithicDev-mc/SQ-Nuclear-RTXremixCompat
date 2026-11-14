package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class DataConsole {
    private DataConsole() {}

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("temp", List.of("vessel_temp", "heat")),
            Map.entry("hitze", List.of("heat", "vessel_temp")),
            Map.entry("drossel", List.of("throttle")),
            Map.entry("tiefe", List.of("rod")),
            Map.entry("stab", List.of("rod")),
            Map.entry("leistung", List.of("power", "fe_rate")),
            Map.entry("brennstoff", List.of("fuel")),
            Map.entry("dampf", List.of("steam")),
            Map.entry("durchfluss", List.of("primary_flow")),
            Map.entry("strom", List.of("energy")),
            Map.entry("drehzahl", List.of("rpm")),
            Map.entry("betrieb", List.of("enable", "run")),
            Map.entry("enable", List.of("enable", "run")),
            Map.entry("run", List.of("run", "enable")));

    private static MutableComponent t(String key, Object... args) {
        return Component.translatable("data.nuclearsq.console." + key, args);
    }

    private static Component err(Component c) {
        return c.copy().withStyle(ChatFormatting.RED);
    }

    private static ChatFormatting stateColor(int state) {
        return switch (state) {
            case DataSource.OK -> ChatFormatting.GREEN;
            case DataSource.WARN -> ChatFormatting.GOLD;
            case DataSource.ALARM -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    private static final List<String> FREE = List.of("help", "hilfe", "?", "rechen", "compute", "cpu", "ram");

    public static List<Component> runChecked(Level level, BlockPos console, @Nullable ServerPlayer player, String line) {
        String cmd = line.trim().toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (!cmd.isEmpty() && !FREE.contains(cmd)) {
            ComputeNet.Status s = ComputeNet.status(level, console);
            if (!s.served()) return List.of(ComputeNet.missing(s));
        }
        return run(level, console, player, line);
    }

    public static List<Component> run(Level level, BlockPos console, @Nullable ServerPlayer player, String line) {
        List<Component> out = new ArrayList<>();
        String[] a = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) return out;
        List<DataSource> net = DataNet.scan(level, console);
        switch (a[0]) {
            case "help", "hilfe", "?" -> {
                for (String k : new String[] {"list", "info", "get", "set", "open", "watch", "compute", "clear"}) out.add(t("help." + k));
            }
            case "rechen", "compute", "cpu", "ram" -> out.addAll(ComputeNet.report(ComputeNet.status(level, console)));
            case "list", "liste", "ls" -> {
                if (net.isEmpty()) out.add(err(Component.translatable("data.nuclearsq.msg.none")));
                for (int i = 0; i < net.size(); i++) {
                    DataSource s = net.get(i);
                    out.add(Component.literal(String.format(Locale.ROOT, "%2d ", i + 1)).append(s.name())
                            .append(Component.literal("  [").append(s.stateText()).append("]").withStyle(stateColor(s.state())))
                            .append(t("dist", (int) Math.round(Math.sqrt(s.pos().distSqr(console))))));
                }
            }
            case "info", "i" -> {
                DataSource s = pick(net, a, 1, out);
                if (s == null) break;
                out.add(s.name().copy().withStyle(ChatFormatting.BOLD).append(Component.literal("  [").append(s.stateText())
                        .append("]").withStyle(stateColor(s.state()))));
                for (DataValue v : s.values()) out.add(valueLine(v));
                if (!s.settable().isEmpty()) out.add(t("settable", String.join(", ", s.settable())));
            }
            case "get", "wert", "watch", "beobachte" -> {
                DataSource s = pick(net, a, 1, out);
                if (s == null) break;
                DataValue v = a.length > 2 ? value(s, a[2]) : null;
                if (v == null) out.add(err(t("no_value", a.length > 2 ? a[2] : "?")));
                else out.add(Component.literal(String.format(Locale.ROOT, "%2d ", net.indexOf(s) + 1)).append(valueLine(v)));
            }
            case "set", "setze" -> {
                DataSource s = pick(net, a, 1, out);
                if (s == null) break;
                if (a.length < 4) {
                    out.add(err(t("usage.set")));
                    break;
                }
                String key = settableKey(s, a[2]);
                Double value = number(a[3]);
                if (player != null && !s.mayControl(player)) out.add(err(Component.translatable("message.nuclearsq.access.denied")));
                else if (key == null) out.add(err(t("not_settable", a[2], s.settable().isEmpty() ? "-" : String.join(", ", s.settable()))));
                else if (value == null) out.add(err(t("bad_number", a[3])));
                else {
                    Component e = s.set(key, value);
                    if (e != null) out.add(err(e));
                    else out.add(t("done", s.name(), key, DataValue.number(value)).withStyle(ChatFormatting.GREEN));
                }
            }
            case "open", "oeffne" -> {
                DataSource s = pick(net, a, 1, out);
                if (s != null && player != null) DataTerminalBlock.open(player, console, s);
            }
            default -> out.add(err(t("unknown", a[0])));
        }
        return out;
    }

    private static Component valueLine(DataValue v) {
        MutableComponent c = Component.literal("   " + v.key() + "  ").withStyle(ChatFormatting.DARK_GRAY);
        return c.append(v.label().copy().withStyle(ChatFormatting.GRAY)).append(Component.literal(": " + v.text()).withStyle(ChatFormatting.WHITE));
    }

    @Nullable
    private static DataSource pick(List<DataSource> net, String[] a, int i, List<Component> out) {
        if (a.length <= i) {
            out.add(err(t("need_index")));
            return null;
        }
        try {
            int n = Integer.parseInt(a[i]);
            if (n >= 1 && n <= net.size()) return net.get(n - 1);
        } catch (NumberFormatException ignored) {
            for (DataSource s : net) if (s.type().contains(a[i])) return s;
        }
        out.add(err(t("bad_index", a[i], net.size())));
        return null;
    }

    @Nullable
    public static DataValue value(DataSource s, String name) {
        String key = resolve(s.values().stream().map(DataValue::key).toList(), name);
        return key == null ? null : s.value(key);
    }

    @Nullable
    private static String settableKey(DataSource s, String name) {
        return resolve(s.settable(), name);
    }

    @Nullable
    private static String resolve(List<String> keys, String name) {
        if (keys.contains(name)) return name;
        for (String k : ALIASES.getOrDefault(name, List.of())) if (keys.contains(k)) return k;
        for (String k : keys) if (k.contains(name)) return k;
        return null;
    }

    @Nullable
    private static Double number(String s) {
        return switch (s) {
            case "on", "ein", "an", "true", "start" -> 1.0;
            case "off", "aus", "false", "stop" -> 0.0;
            default -> {
                try {
                    yield Double.parseDouble(s.replace(',', '.'));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }
}
