package com.sqc.nuclearsq.reactor;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.gamedata.GameData;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PartStages {
    private PartStages() {}

    public static final List<String> MODERATORS = List.of("moderator_coal_sqc", "moderator_carbon_sqc", "moderator_obsidian_sqc", "moderator_fluorite_sqc", "moderator_heavy_water_sqc");
    public static final List<String> COOLERS = List.of("heat_exchanger_sqc", "active_cooler_sqc", "cryo_cooling_core_sqc");
    public static final List<String> TRANSLATORS = List.of("translator_basic_sqc", "translator_brass_sqc", "translator_composite_sqc", "translator_glowstone_sqc");

    private static final int[] POWER = {125, 150, 200, 300, 400}, HEAT = {120, 140, 220, 360, 420}, COOLER_ADD = {300, 600, 1000};
    private static final int[] TR_FE = {100, 110, 125, 150}, TR_FLOW = {100, 150, 200, 300};

    public static int stage(List<String> ids, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(NuclearSQ.MODID) ? ids.indexOf(key.getPath()) + 1 : 0;
    }

    public static int moderatorStage(ItemStack s) { return stage(MODERATORS, s); }
    public static int coolerStage(ItemStack s) { return stage(COOLERS, s); }
    public static int translatorStage(ItemStack s) { return stage(TRANSLATORS, s); }

    public static ItemStack stack(List<String> ids, int stage) {
        if (stage < 1 || stage > ids.size()) return ItemStack.EMPTY;
        return new ItemStack(CatalogRegistry.BLOCK_ITEM_MAP.get(ids.get(stage - 1)).get());
    }

    private static int value(String name, int stage, int[] fallback, int none) {
        if (stage < 1 || stage > fallback.length) return none;
        return GameData.cooling().part(name + "_" + stage, fallback[stage - 1]);
    }

    public static float moderatorPower(int stage) { return value("moderator_power_pct", stage, POWER, 100) / 100f; }

    public static float moderatorHeat(int stage) { return value("moderator_heat_pct", stage, HEAT, 100) / 100f; }

    public static float coolerCooling(int stage) { return value("cooler_add_milli", stage, COOLER_ADD, 0) / 1000f; }

    public static int translatorFePct(int stage) { return value("translator_fe_pct", stage, TR_FE, 100); }

    public static int translatorFlowPct(int stage) { return value("translator_flow_pct", stage, TR_FLOW, 100); }
}
