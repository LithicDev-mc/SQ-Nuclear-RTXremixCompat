package com.sqc.nuclearsq.network.client;

import com.sqc.nuclearsq.data.DataPayloads;
import com.sqc.nuclearsq.data.client.DataConsoleScreen;
import com.sqc.nuclearsq.explosion.client.ExplosionFlash;
import com.sqc.nuclearsq.explosion.client.MushroomCloud;
import com.sqc.nuclearsq.network.DosimeterPayload;
import com.sqc.nuclearsq.network.ExplosionFlashPayload;
import com.sqc.nuclearsq.network.GeigerPayload;
import com.sqc.nuclearsq.network.MushroomCloudPayload;
import com.sqc.nuclearsq.radiation.client.DosimeterHud;
import com.sqc.nuclearsq.radiation.client.GeigerHud;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void gameData(com.sqc.nuclearsq.gamedata.GameDataPayload p) {
        if (net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) return;
        com.sqc.nuclearsq.gamedata.GameData.applyRemote(p);
    }

    public static void explosionFlash(ExplosionFlashPayload p) {
        net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
        if (me == null) return;
        net.minecraft.world.phys.Vec3 eye = me.getEyePosition(), at = new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z());
        double d = eye.distanceTo(at);
        double look = d < 0.1 ? 1.0 : me.getViewVector(1f).dot(at.subtract(eye).scale(1.0 / d));
        ExplosionFlash.start(p.ticks(), d, p.range(), look);
    }

    public static void mushroomCloud(MushroomCloudPayload p) {
        MushroomCloud.start(p.x(), p.y(), p.z(), p.scale(), p.heavy());
    }

    public static void dataOutput(DataPayloads.Output p) {
        DataConsoleScreen.receive(p.lines());
    }

    public static void dataTree(DataPayloads.Tree p) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.sqc.nuclearsq.data.DataTerminalMenu m && m.containerId == p.containerId()) {
            m.entries = p.entries();
            m.summary = p.summary();
        }
    }

    public static void logbook(com.sqc.nuclearsq.data.LogbookPayload p) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.sqc.nuclearsq.data.LogbookMenu m && m.containerId == p.containerId()) m.append(p.entries());
    }

    public static void power(com.sqc.nuclearsq.data.PowerPayload p) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.sqc.nuclearsq.data.PowerMonitorMenu m && m.containerId == p.containerId()) m.receive(p);
    }

    public static void storageItems(com.sqc.nuclearsq.storage.StoragePayloads.Items p) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.sqc.nuclearsq.storage.StorageTerminalMenu m && m.containerId == p.containerId()) m.receive(p);
    }

    public static void storageCraftables(com.sqc.nuclearsq.storage.StoragePayloads.Craftables p) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.sqc.nuclearsq.storage.StorageTerminalMenu m && m.containerId == p.containerId()) m.receive(p);
    }

    public static void dosimeter(DosimeterPayload p) {
        DosimeterHud.receive(p);
    }

    public static void appLogin(com.sqc.nuclearsq.apps.AppPayloads.State p) {
        com.sqc.nuclearsq.apps.client.LoginMonitorScreen.receive(p);
    }

    public static void appAuction(com.sqc.nuclearsq.apps.AuctionPayloads.State p) {
        com.sqc.nuclearsq.apps.client.AuctionMonitorScreen.receive(p);
    }

    public static void appSession(com.sqc.nuclearsq.apps.AppPayloads.Session p) {
        com.sqc.nuclearsq.apps.client.ClientAppSession.receive(p);
    }

    public static void access(com.sqc.nuclearsq.machine.AccessPayloads.State p) {
        com.sqc.nuclearsq.machine.client.AccessScreen.receive(p);
    }

    public static void geiger(GeigerPayload p) {
        GeigerHud.receive(p);
    }

    public static void radiationDebug(com.sqc.nuclearsq.network.RadiationDebugPayload p) {
        com.sqc.nuclearsq.radiation.client.RadiationDebugClient.receive(p);
    }

    public static void gadgetTemplate(com.sqc.nuclearsq.nanobot.GadgetTemplatePayload p) {
        com.sqc.nuclearsq.nanobot.client.GadgetPreviewClient.setTemplate(p.rels());
    }

    public static void nanoSwarm(com.sqc.nuclearsq.nanobot.NanoSwarmPayload p) {
        com.sqc.nuclearsq.nanobot.client.NanoSwarmClient.receive(p);
    }

    public static void netScan(com.sqc.nuclearsq.data.NetScanPayload p) {
        com.sqc.nuclearsq.data.client.NetScanClient.receive(p);
    }
}
