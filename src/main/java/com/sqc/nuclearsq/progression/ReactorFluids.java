package com.sqc.nuclearsq.progression;

import java.util.List;
import java.util.Map;

public final class ReactorFluids {
    public record End(String machine, List<String> ports) {
        public String port() { return ports.get(0); }
    }

    public record Link(End from, List<End> to) {}

    public record Fluid(String key, String fluid, int color, List<Link> links) {
        public String nameKey() {
            return fluid.startsWith("minecraft:") ? "block.minecraft." + fluid.substring(10) : "fluid_type." + fluid.replace(':', '.');
        }
    }

    public static final String TURBINE = "turbine";

    public static final Map<String, String> PAGES = Map.ofEntries(
            Map.entry("intake_pump", "plant_intake"),
            Map.entry("water_treatment", "plant_treatment"),
            Map.entry("emergency_cooling", "big_emergency"),
            Map.entry("control_rod_drive", "rod_drive"),
            Map.entry("condenser", "plant_condenser"),
            Map.entry("steam_separator", "big_separator"),
            Map.entry("steam_dryer", "big_dryer"),
            Map.entry("reheater", "eff_reheater"),
            Map.entry("feedwater_heater", "eff_preheater"),
            Map.entry("feedwater_pump", "plant_fwpump"),
            Map.entry("containment", "big_containment"),
            Map.entry("steam_generator", "big_steam_generator"),
            Map.entry("main_coolant_pump", "cool_bigpumps"),
            Map.entry("high_pressure_pump", "cool_hppump"),
            Map.entry("pressurizer", "big_pressurizer"),
            Map.entry("ion_exchanger", "rod_exchanger"),
            Map.entry("relief_tank", "big_relief_tank"),
            Map.entry("high_pressure_injection", "big_hp_injection"),
            Map.entry("steam_purifier", "plant_purifier"),
            Map.entry(TURBINE, "energy_turbine"),
            Map.entry("cooling_tower_large", "big_tower"),
            Map.entry("cooling_tower_xl", "big_tower"));

    private static End e(String machine, String... ports) { return new End(machine, List.of(ports)); }

    private static Link l(End from, End... to) { return new Link(from, List.of(to)); }

    private static final End WT_SMALL_IN = e("water_treatment", "water_in_9", "water_in_10");
    private static final End SG_FEED = e("steam_generator", "feedwater_in");
    private static final End CT_FEED_IN = e("containment", "feedwater_in");
    private static final End CT_FEED_OUT = e("containment", "feedwater_out");
    private static final End CT_TREAT_IN = e("containment", "treatment_in");
    private static final End CT_TREAT_OUT = e("containment", "treatment_out");
    private static final End DRIVE_COLD = e("control_rod_drive", "primary_cold_in");
    private static final End DRIVE_HYD = e("control_rod_drive", "hydraulic_in");
    private static final End MCP_IN = e("main_coolant_pump", "primary_in");
    private static final End HPP_IN = e("high_pressure_pump", "primary_in");
    private static final End PZR_IN = e("pressurizer", "primary_in");
    private static final End SEP_IN = e("steam_separator", "wet_steam_in");
    private static final End DRY_IN = e("steam_dryer", "wet_steam_in");
    private static final End SEP_OUT = e("steam_separator", "steam_out");
    private static final End DRY_OUT = e("steam_dryer", "steam_out");
    private static final End PUR_IN = e("steam_purifier", "steam_in");
    private static final End TURB_STEAM = e(TURBINE, "steam");
    private static final End RH_IN = e("reheater", "steam_in");
    private static final End TOWER_IN = e("cooling_tower_large", "coolant_in");
    private static final End TOWER_XL_IN = e("cooling_tower_xl", "coolant_in");

    public static final List<Fluid> FLUIDS = List.of(
            new Fluid("water", "minecraft:water", 0xFF3F76E4, List.of(
                    l(e("intake_pump", "water_out_l_1", "water_out_l_2", "water_out_l_3", "water_out_l_4", "water_out_r_1", "water_out_r_2", "water_out_r_3", "water_out_r_4"),
                            e("water_treatment", "water_in", "water_in_2", "water_in_3", "water_in_4", "water_in_5", "water_in_6", "water_in_7", "water_in_8")),
                    l(e("intake_pump", "water_out_s_1", "water_out_s_2"), e("emergency_cooling", "water_in"), e("containment", "hyd_in"), DRIVE_HYD, WT_SMALL_IN),
                    l(e("containment", "hyd_out"), DRIVE_HYD),
                    l(e("condenser", "water_out"), WT_SMALL_IN),
                    l(e("feedwater_heater", "water_out"), WT_SMALL_IN),
                    l(e("steam_separator", "water_out"), WT_SMALL_IN),
                    l(e("steam_dryer", "water_out"), WT_SMALL_IN),
                    l(e("reheater", "water_out"), WT_SMALL_IN))),
            new Fluid("feedwater", "nuclearsq:feedwater", 0xFF7CC4F0, List.of(
                    l(e("water_treatment", "feedwater_out", "feedwater_out_2", "feedwater_out_3", "feedwater_out_4"),
                            e("feedwater_pump", "feedwater_in"), e("feedwater_heater", "feedwater_in"), CT_FEED_IN, CT_TREAT_IN, SG_FEED, DRIVE_HYD),
                    l(e("feedwater_pump", "feedwater_out"), e("feedwater_heater", "feedwater_in"), CT_FEED_IN, CT_TREAT_IN, SG_FEED, DRIVE_HYD),
                    l(e("feedwater_heater", "feedwater_out"), e("feedwater_pump", "feedwater_in"), CT_FEED_IN, CT_TREAT_IN, SG_FEED, DRIVE_HYD),
                    l(CT_FEED_OUT, SG_FEED, DRIVE_HYD),
                    l(CT_TREAT_OUT, SG_FEED, DRIVE_HYD))),
            new Fluid("preheated_feedwater", "nuclearsq:preheated_feedwater", 0xFF82D2D8, List.of(
                    l(e("feedwater_heater", "feedwater_out"), e("feedwater_pump", "feedwater_in"), CT_FEED_IN, CT_TREAT_IN, SG_FEED, DRIVE_HYD),
                    l(e("feedwater_pump", "feedwater_out"), CT_FEED_IN, CT_TREAT_IN, SG_FEED, DRIVE_HYD),
                    l(CT_FEED_OUT, SG_FEED, DRIVE_HYD),
                    l(CT_TREAT_OUT, SG_FEED, DRIVE_HYD))),
            new Fluid("primary_water", "nuclearsq:primary_water", 0xFF6A7FE0, List.of(
                    l(e("emergency_cooling", "primary_out"), e("containment", "emc_in"), DRIVE_COLD),
                    l(e("containment", "emc_out"), DRIVE_COLD),
                    l(e("steam_generator", "primary_cold_out"), MCP_IN, HPP_IN, e("ion_exchanger", "primary_in")),
                    l(e("ion_exchanger", "primary_out"), MCP_IN, HPP_IN),
                    l(e("main_coolant_pump", "primary_out"), DRIVE_COLD, PZR_IN),
                    l(e("high_pressure_pump", "primary_out"), DRIVE_COLD, PZR_IN),
                    l(e("relief_tank", "primary_out"), e("high_pressure_injection", "primary_in")),
                    l(e("high_pressure_injection", "primary_out"), e("containment", "hpi_in"), DRIVE_COLD),
                    l(e("containment", "hpi_out"), DRIVE_COLD))),
            new Fluid("hot_primary_water", "nuclearsq:hot_primary_water", 0xFFC85AD2, List.of(
                    l(e("control_rod_drive", "primary_hot_out"), e("steam_generator", "primary_hot_in")),
                    l(e("pressurizer", "relief_out"), e("containment", "relief_in"), e("relief_tank", "relief_in")),
                    l(e("containment", "relief_out"), e("relief_tank", "relief_in")))),
            new Fluid("wet_steam", "nuclearsq:wet_steam", 0xFFB6CBD8, List.of(
                    l(e("steam_generator", "wet_steam_out"), e("containment", "wet_steam_in"), SEP_IN, DRY_IN),
                    l(e("containment", "wet_steam_out"), SEP_IN, DRY_IN))),
            new Fluid("contaminated_steam", "nuclearsq:contaminated_steam", 0xFFB4D48C, List.of(
                    l(e("steam_generator", "wet_steam_out"), e("containment", "wet_steam_in"), SEP_IN, DRY_IN),
                    l(e("containment", "wet_steam_out"), SEP_IN, DRY_IN),
                    l(SEP_OUT, PUR_IN),
                    l(DRY_OUT, PUR_IN))),
            new Fluid("steam", "nuclearsq:steam", 0xFFE8E8E8, List.of(
                    l(SEP_OUT, TURB_STEAM, RH_IN, PUR_IN),
                    l(DRY_OUT, TURB_STEAM, RH_IN, PUR_IN),
                    l(e("steam_purifier", "steam_out"), TURB_STEAM, RH_IN))),
            new Fluid("superheated_steam", "nuclearsq:superheated_steam", 0xFFF2CC9C, List.of(
                    l(e("reheater", "steam_out"), TURB_STEAM))),
            new Fluid("exhaust_steam", "nuclearsq:exhaust_steam", 0xFFB8C4CE, List.of(
                    l(e(TURBINE, "water"), e("condenser", "exhaust_in"), e("feedwater_heater", "exhaust_in")))),
            new Fluid("coolant", "nuclearsq:coolant", 0xFF3FB6E8, List.of(
                    l(e("cooling_tower_large", "coolant_out"), e("condenser", "coolant_in"), e("relief_tank", "coolant_in")),
                    l(e("cooling_tower_xl", "coolant_out"), e("condenser", "coolant_in"), e("relief_tank", "coolant_in")))),
            new Fluid("hot_coolant", "nuclearsq:hot_coolant", 0xFFE8742A, List.of(
                    l(e("condenser", "coolant_out"), TOWER_IN, TOWER_XL_IN),
                    l(e("relief_tank", "coolant_out"), TOWER_IN, TOWER_XL_IN))));

    public static String portKey(String machine, String port) {
        return TURBINE.equals(machine) ? "block.nuclearsq.turbine_port_" + port + "_sqc" : "port.nuclearsq." + machine + "." + port;
    }

    public static String controllerItem(String machine) {
        return machine + "_controller_sqc";
    }

    private ReactorFluids() {}
}
