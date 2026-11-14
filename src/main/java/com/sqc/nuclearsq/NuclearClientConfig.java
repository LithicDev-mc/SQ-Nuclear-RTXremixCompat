package com.sqc.nuclearsq;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class NuclearClientConfig {
    private NuclearClientConfig() {}

    public enum TerminalStyle { CRT, MODERN }

    public enum NanobotLook { DARK, CHROME }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<TerminalStyle> TERMINAL_STYLE = BUILDER
            .comment("Look of all machine terminals: CRT (green phosphor tube monitor) or MODERN (dark cards).",
                    "Can also be switched in game with the toggle at the top right of every terminal.")
            .defineEnum("terminalStyle", TerminalStyle.MODERN);

    public static final ModConfigSpec.EnumValue<NanobotLook> NANOBOT_LOOK = BUILDER
            .comment("Look of the nano bot spiders: DARK (matte dark body, fine bristles, glints) or CHROME (polished metal).",
                    "Eyes and the back LED glow in both.")
            .defineEnum("nanobotLook", NanobotLook.DARK);

    public static final ModConfigSpec.BooleanValue DEBUG_BOT = BUILDER
            .comment("Debug: once a second write position, surface, light, visibility and render path of every nano bot to the log (for hunting invisible bots).")
            .define("debugBot", false);

    public static final ModConfigSpec.BooleanValue DEBUG_BOT_BOX = BUILDER
            .comment("Debug: draw the collision box of every nano bot as a red frame (for testing wall and block collisions).")
            .define("debugBotBox", false);

    public static final ModConfigSpec.IntValue VIEW_DISTANCE = BUILDER
            .comment("How far (in blocks) machines, fuel rods, and the Cherenkov glow are drawn.",
                    "Never less than the game's own render distance.")
            .defineInRange("viewDistance", 512, 64, 2048);

    public static final ModConfigSpec.IntValue GLOW_DISTANCE = BUILDER
            .comment("How far (in blocks) the reactor effects are drawn: Cherenkov glow, fuel rod glow, glowing machine parts.",
                    "Lower values give more FPS near big reactors.")
            .defineInRange("glowDistance", 96, 16, 512);

    public static final ModConfigSpec.BooleanValue OCCLUSION_CULLING = BUILDER
            .comment("Skip drawing machines, fuel rods and wires that are completely hidden behind solid blocks",
                    "(e.g. while standing inside a closed room). Turn off if something flickers away wrongly.")
            .define("occlusionCulling", true);

    public static final ModConfigSpec.BooleanValue BAKED_MACHINES = BUILDER
            .comment("Draw big static machines (cooling towers, fusion reactor, containment) from a pre-built vertex buffer",
                    "instead of recomputing every cube each frame. Much faster; turn off if a machine looks wrong.")
            .define("bakedMachines", true);

    public static final ModConfigSpec.BooleanValue CHUNK_BAKING = BUILDER
            .comment("Put the static parts of simple machines (batteries, solar field and most other machines) into the world's chunk meshes instead of drawing",
                    "them every frame. Costs almost nothing per frame, also with shader packs. Turn off if such a machine looks wrong",
                    "(in game: /nsqperf chunk).")
            .define("chunkBaking", true);

    public static final ModConfigSpec.IntValue SHADOW_DISTANCE = BUILDER
            .comment("With a shader pack (Iris): big baked machines further away than this (in blocks) cast no shadow.",
                    "Lower values give more FPS. Has no effect without a shader pack.")
            .defineInRange("bakedShadowDistance", 64, 0, 512);

    public static final ModConfigSpec.BooleanValue MACHINE_SHADOWS = BUILDER
            .comment("With a shader pack (Iris): let big machines, monitors, consoles and fuel rods cast shadows.",
                    "Off by default: their shadows look blotchy (cooling towers, turbine, containment).")
            .define("machineShadows", false);

    static {
        BUILDER.push("compendium");
    }

    public static final ModConfigSpec.BooleanValue COMPENDIUM_TIPS = BUILDER
            .comment("Show the tip box at the bottom of compendium pages. Also switched in game (\"Tips\" at the top right).")
            .define("showTips", false);

    public static final ModConfigSpec.BooleanValue COMPENDIUM_ITEMS = BUILDER
            .comment("Show the machine pictures and recipe items under the picture of compendium pages.",
                    "Also switched in game (\"Items\" at the top right).")
            .define("showItems", false);

    public static final ModConfigSpec.BooleanValue COMPENDIUM_SCOPE_PLAYED = BUILDER
            .comment("The build-up of the \"All Items\" compendium page has been watched once (set automatically).",
                    "It then only plays again with \"Build again\".")
            .define("allItemsPlayed", false);

    public static final ModConfigSpec.ConfigValue<String> COMPENDIUM_LAST = BUILDER
            .comment("Last viewed compendium page (set automatically when the compendium is closed).")
            .define("lastPage", "");

    static {
        BUILDER.pop();
        BUILDER.push("effects");
    }

    public static final ModConfigSpec.BooleanValue RED_SKY = BUILDER
            .comment("The sky turns red after a nuclear explosion.")
            .define("redSky", true);

    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE = BUILDER
            .comment("Strength of the screen shake (0 = off).")
            .defineInRange("screenShake", 1.0, 0.0, 3.0);

    public static final ModConfigSpec.IntValue DEBRIS = BUILDER
            .comment("Maximum number of flying rocks at the same time (0 = off).")
            .defineInRange("maxRocks", 1200, 0, 2000);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean modern() {
        try {
            return TERMINAL_STYLE.get() == TerminalStyle.MODERN;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static int viewDistance() {
        int v;
        try {
            v = VIEW_DISTANCE.get();
        } catch (IllegalStateException e) {
            v = 512;
        }
        return Math.max(v, net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
    }

    private static Boolean chunkBakingNow;

    public static void setChunkBakingNow(boolean on) { chunkBakingNow = on; }

    public static boolean chunkBaking() {
        if (chunkBakingNow != null) return chunkBakingNow;
        try {
            return CHUNK_BAKING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean occlusionCulling() {
        try {
            return OCCLUSION_CULLING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int glowDistance() {
        try {
            return GLOW_DISTANCE.get();
        } catch (IllegalStateException e) {
            return 96;
        }
    }

    public static boolean bakedMachines() {
        try {
            return BAKED_MACHINES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static void setBakedMachines(boolean on) { saveFlag(BAKED_MACHINES, on); }

    public static boolean noShadow() {
        boolean on;
        try {
            on = MACHINE_SHADOWS.get();
        } catch (IllegalStateException e) {
            on = false;
        }
        return !on && com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass();
    }

    public static int shadowDistance() {
        try {
            return SHADOW_DISTANCE.get();
        } catch (IllegalStateException e) {
            return 64;
        }
    }

    public static final int[] GLOW_STEPS = {16, 48, 96};

    public static int cycleGlowDistance() {
        int cur = glowDistance(), next = GLOW_STEPS[0];
        for (int i = 0; i < GLOW_STEPS.length; i++) {
            if (cur <= GLOW_STEPS[i]) {
                next = GLOW_STEPS[(i + 1) % GLOW_STEPS.length];
                break;
            }
        }
        try {
            GLOW_DISTANCE.set(next);
            GLOW_DISTANCE.save();
        } catch (IllegalStateException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: glow distance could not be saved ({})", e.getMessage());
        }
        return next;
    }

    public static double cameraDistSq(net.minecraft.core.BlockPos pos) {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean inGlowRange(net.minecraft.core.BlockPos pos) {
        if (com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()) return false;
        double d = glowDistance();
        return cameraDistSq(pos) <= d * d;
    }

    public static boolean compendiumTips() {
        try {
            return COMPENDIUM_TIPS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean compendiumItems() {
        try {
            return COMPENDIUM_ITEMS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean compendiumScopePlayed() {
        try {
            return COMPENDIUM_SCOPE_PLAYED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void setCompendiumScopePlayed() { saveFlag(COMPENDIUM_SCOPE_PLAYED, true); }

    public static void setCompendiumTips(boolean on) { saveFlag(COMPENDIUM_TIPS, on); }

    public static void setCompendiumItems(boolean on) { saveFlag(COMPENDIUM_ITEMS, on); }

    public static String compendiumLast() {
        try {
            return COMPENDIUM_LAST.get();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    public static void setCompendiumLast(String id) {
        try {
            if (id.equals(COMPENDIUM_LAST.get())) return;
            COMPENDIUM_LAST.set(id);
            COMPENDIUM_LAST.save();
        } catch (IllegalStateException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: compendium page could not be saved ({})", e.getMessage());
        }
    }

    private static void saveFlag(ModConfigSpec.BooleanValue v, boolean on) {
        try {
            v.set(on);
            v.save();
        } catch (IllegalStateException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: compendium setting could not be saved ({})", e.getMessage());
        }
    }

    public static void setModern(boolean modern) {
        try {
            TERMINAL_STYLE.set(modern ? TerminalStyle.MODERN : TerminalStyle.CRT);
            TERMINAL_STYLE.save();
        } catch (IllegalStateException e) {
            NuclearSQ.LOGGER.warn("NuclearSQ: terminal style could not be saved ({})", e.getMessage());
        }
    }
}
