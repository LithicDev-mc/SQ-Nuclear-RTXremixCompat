package com.sqc.nuclearsq.client;

import com.mojang.brigadier.Command;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class PerfProbe {
    private PerfProbe() {}

    public static boolean on;
    public static long preNanos, sPre;
    public static long machines, nanos, replayVertices, bufferDraws, bakes, shadowMachines, shadowNanos, hullCulled, viewCulled, drawn, clusterCulled, sentVertices, chunked, chunkedIdle;
    private static long sChunkedIdle, sChunked, sMachines, sNanos, sVertices, sDraws, sBakes, sShadow, sShadowNanos, sHull, sView, sDrawn, sCluster, sSent;
    private static final java.util.HashMap<Object, Long> KIND = new java.util.HashMap<>();
    private static final java.util.HashMap<Object, long[]> DYN = new java.util.HashMap<>();
    private static String topKinds = "", topDyn = "";
    private static int cacheSize;

    public static long start() {
        return on ? System.nanoTime() : 0L;
    }

    public static void end(long t0) {
        if (!on) return;
        long d = System.nanoTime() - t0;
        if (com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()) {
            shadowMachines++;
            shadowNanos += d;
        } else {
            machines++;
            nanos += d;
        }
    }

    public static void pre(long t0) {
        if (on && t0 != 0L) preNanos += System.nanoTime() - t0;
    }

    public static void kind(Object k, long vertices) {
        KIND.merge(k, vertices, Long::sum);
    }

    public static void dynamic(Object k, long nanos) {
        long[] a = DYN.computeIfAbsent(k, x -> new long[2]);
        a[0]++;
        a[1] += nanos;
    }

    public static void cacheSize(int n) {
        cacheSize = n;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        reportDrawn();
        sPre = preNanos;
        preNanos = 0;
        sMachines = machines; sNanos = nanos; sVertices = replayVertices; sDraws = bufferDraws; sBakes = bakes;
        sShadow = shadowMachines; sShadowNanos = shadowNanos; sHull = hullCulled; sView = viewCulled; sDrawn = drawn; sCluster = clusterCulled; sSent = sentVertices; sChunked = chunked; sChunkedIdle = chunkedIdle;
        topKinds = KIND.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(4)
                .map(e -> e.getKey() + " " + e.getValue() / 1000 + "k").collect(java.util.stream.Collectors.joining(", "));
        topDyn = DYN.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(4)
                .map(e -> e.getKey() + " " + e.getValue()[0] + "x " + String.format("%.1f", e.getValue()[1] / 1e6) + "ms")
                .collect(java.util.stream.Collectors.joining(", "));
        com.sqc.nuclearsq.machine.big.client.ChunkMachines.snapshotCounters();
        DYN.clear();
        KIND.clear();
        machines = nanos = replayVertices = bufferDraws = bakes = shadowMachines = shadowNanos = hullCulled = viewCulled = drawn = clusterCulled = sentVertices = chunked = chunkedIdle = 0;
    }

    public static volatile boolean listNext;
    private static final java.util.Map<String, int[]> LIST = new java.util.TreeMap<>();

    public static void noteDrawn(com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be, String why) {
        if (!listNext) return;
        String k = be.machine() + " (" + why + (be.substation() != null ? ", Umspannwerk" : "") + ")";
        int d = (int) Math.sqrt(com.sqc.nuclearsq.NuclearClientConfig.cameraDistSq(be.getBlockPos()));
        int[] e = LIST.computeIfAbsent(k, x -> new int[] {0, Integer.MAX_VALUE});
        e[0]++;
        e[1] = Math.min(e[1], d);
    }

    private static void reportDrawn() {
        if (!listNext || LIST.isEmpty() || Minecraft.getInstance().player == null) return;
        StringBuilder sb = new StringBuilder("NuclearSQ gezeichnete Maschinen (ein Bild):");
        for (var en : LIST.entrySet()) sb.append("\n").append(en.getValue()[0]).append("x ").append(en.getKey()).append(", naechste ").append(en.getValue()[1]).append(" Bloecke");
        listNext = false;
        LIST.clear();
        Minecraft.getInstance().player.sendSystemMessage(Component.literal(sb.toString()));
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Component cmd = Component.literal("/nsqperf chunk").withStyle(s -> s
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/nsqperf chunk")));
        event.getPlayer().sendSystemMessage(Component.translatable("message.nuclearsq.texture_hint", cmd).withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("nsqperf").executes(c -> {
            on = !on;
            c.getSource().sendSuccess(() -> Component.literal("NuclearSQ Zaehler: " + (on ? "an" : "aus")), false);
            return Command.SINGLE_SUCCESS;
        }).then(net.minecraft.commands.Commands.literal("drawn").executes(c -> {
            listNext = true;
            c.getSource().sendSuccess(() -> Component.literal("NuclearSQ: naechstes Bild wird ausgewertet"), false);
            return Command.SINGLE_SUCCESS;
        })).then(net.minecraft.commands.Commands.literal("chunk").executes(c -> {
            boolean now = !com.sqc.nuclearsq.NuclearClientConfig.chunkBaking();
            com.sqc.nuclearsq.NuclearClientConfig.setChunkBakingNow(now);
            com.sqc.nuclearsq.machine.big.client.ChunkMachines.reset();
            Minecraft.getInstance().levelRenderer.allChanged();
            c.getSource().sendSuccess(() -> Component.literal("NuclearSQ Chunk-Backen: " + (now ? "an" : "aus")), false);
            return Command.SINGLE_SUCCESS;
        })).then(net.minecraft.commands.Commands.literal("baked").executes(c -> {
            boolean now = !com.sqc.nuclearsq.NuclearClientConfig.bakedMachines();
            com.sqc.nuclearsq.NuclearClientConfig.setBakedMachines(now);
            com.sqc.nuclearsq.machine.big.client.ChunkMachines.reset();
            Minecraft.getInstance().levelRenderer.allChanged();
            c.getSource().sendSuccess(() -> Component.literal("NuclearSQ Backen: " + (now ? "an" : "aus")), false);
            return Command.SINGLE_SUCCESS;
        })).then(net.minecraft.commands.Commands.literal("holes").then(net.minecraft.commands.Commands.argument("modus",
                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
            int m = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "modus");
            com.sqc.nuclearsq.machine.big.client.ChunkMachines.holeMode = m;
            Minecraft.getInstance().levelRenderer.allChanged();
            c.getSource().sendSuccess(() -> Component.literal("NuclearSQ Loecher-Modus: " + m), false);
            return Command.SINGLE_SUCCESS;
        }))));
    }

    @SubscribeEvent
    public static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "perf_probe"), PerfProbe::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!on) return;
        var font = Minecraft.getInstance().font;
        String[] lines = {
            "Maschinen: Aufrufe " + sMachines + ", gezeichnet " + sDrawn + "  (" + String.format("%.2f", sNanos / 1e6) + " ms, davon Vorpruefung " + String.format("%.2f", sPre / 1e6) + " ms)",
            "Eckpunkte: gesendet " + sSent + " von " + sVertices + "  Puffer-Aufrufe: " + sDraws,
            "Backen-Eintraege: " + cacheSize + "  Neu gebacken: " + sBakes,
            "Schattenpass: " + sShadow + " Maschinen (" + String.format("%.2f", sShadowNanos / 1e6) + " ms)",
            "Backen: " + (com.sqc.nuclearsq.NuclearClientConfig.bakedMachines() ? "an" : "AUS (/nsqperf baked)") + ", Chunk-Backen: "
                    + (!com.sqc.nuclearsq.NuclearClientConfig.chunkBaking() ? "aus" : com.sqc.nuclearsq.NuclearClientConfig.bakedMachines() ? "an" : "aus (braucht Backen)") + ", im Chunk-Mesh "
                    + com.sqc.nuclearsq.machine.big.client.ChunkMachines.machinesInChunks() + " Maschinen (davon verdeckt ausgeblendet "
                    + com.sqc.nuclearsq.machine.big.client.ChunkMachines.hiddenMeshes() + ", "
                    + com.sqc.nuclearsq.machine.big.client.ChunkMachines.quadCount() + " Vierecke je Art), Renderer ganz uebersprungen " + sChunkedIdle + ", nur Leuchtbild/Bewegtes " + sChunked,
            "Verdeckungstest: " + com.sqc.nuclearsq.client.OcclusionCull.snapTests + " Pruefungen je Bild, davon verdeckt "
                    + com.sqc.nuclearsq.client.OcclusionCull.snapHidden + ", Budget reichte nicht fuer "
                    + com.sqc.nuclearsq.client.OcclusionCull.snapBudget + "; Chunk-Maschinen: "
                    + com.sqc.nuclearsq.machine.big.client.ChunkMachines.counters(),
            "Sichtbar gemeldet wegen: " + com.sqc.nuclearsq.client.OcclusionCull.snapReasons,
            "Weggelassen: Huelle " + sHull + ", Sicht/Verdeckung " + sView + ", Blickfeld-Gruppen " + sCluster,
            "Eckpunkte je Art: " + topKinds,
            "Bewegte Teile je Art (Anzahl, Zeit): " + topDyn};
        for (int i = 0; i < lines.length; i++) g.drawString(font, lines[i], 4, 4 + i * 10, 0xFFFFFF, true);
    }
}
