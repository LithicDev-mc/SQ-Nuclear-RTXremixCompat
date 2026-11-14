package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;

public final class PlantOverview {
    public enum Group { START, FUEL, CORE, SAFETY, STEAM, POWER, CYCLE, ASSEMBLY, STORAGE, WEAPON }

    public record Node(String key, String item, String page, Group group, int col, int row) {}

    public record Edge(int from, int to, boolean ring) {}

    public static final String ASSEMBLER_ITEM = "reactor_assembler_controller_sqc";

    public static final java.util.Set<String> ICON_ONLY = java.util.Set.of("solar_panel_sqc", "solar_collector_sqc", "rtg_sqc");

    public static final List<Node> NODES = List.of(
            new Node("coal", "coal_generator_controller_sqc", "energy_start", Group.START, 0, 2),
            new Node("solar_panel", "solar_panel_sqc", "energy_solar", Group.START, 1, 0),
            new Node("solar_collector", "solar_collector_sqc", "energy_solar", Group.START, 1, 1),
            new Node("solar", "solar_field_controller_sqc", "energy_solar", Group.START, 1, 2),
            new Node("rtg", "rtg_sqc", "energy_rtg", Group.START, 0, 4),
            new Node("wind_small", "wind_turbine_small_controller_sqc", "energy_wind", Group.POWER, 1, 6),
            new Node("wind_medium", "wind_turbine_medium_controller_sqc", "energy_wind", Group.POWER, 1, 5),
            new Node("wind_large", "wind_turbine_large_controller_sqc", "energy_wind", Group.POWER, 1, 4),
            new Node("ore", "ore_mill_controller_sqc", "rod_ore_mill", Group.FUEL, 0, 3),
            new Node("centrifuge", "enrichment_centrifuge_controller_sqc", "rod_centrifuge", Group.FUEL, 1, 3),
            new Node("rod", "fuel_rod_head_sqc", "rod_build", Group.FUEL, 2, 3),
            new Node("pumps", "main_coolant_pump_controller_sqc", "cool_bigpumps", Group.CORE, 5, 2),
            new Node("tower", "cooling_tower_large_controller_sqc", "big_tower", Group.CORE, 6, 3),
            new Node("purifier", "steam_purifier_controller_sqc", "plant_purifier", Group.STEAM, 5, 3),
            new Node("reheater", "reheater_controller_sqc", "eff_reheater", Group.STEAM, 6, 2),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", Group.STEAM, 7, 3),
            new Node("shaft", "shaft_generator_controller_sqc", "energy_shaft", Group.POWER, 9, 3),
            new Node("substation", "substation_controller_sqc", "transport_substation", Group.POWER, 10, 3),
            new Node("mast", "power_mast_sqc", "transport_grid", Group.POWER, 11, 3),
            new Node("mast_large", "power_mast_large_sqc", "transport_grid", Group.POWER, 12, 3),
            new Node("battery", "battery_small_controller_sqc", "transport_battery", Group.POWER, 13, 3),
            new Node("condenser", "condenser_controller_sqc", "plant_condenser", Group.STEAM, 7, 4),
            new Node("treatment", "water_treatment_controller_sqc", "plant_treatment", Group.STEAM, 6, 4),
            new Node("preheater", "feedwater_heater_controller_sqc", "eff_preheater", Group.STEAM, 6, 5),
            new Node("fwpump", "feedwater_pump_controller_sqc", "plant_fwpump", Group.STEAM, 5, 4),
            new Node("desk", "control_desk_controller_sqc", "data_desk", Group.SAFETY, 2, 4),
            new Node("containment", "containment_controller_sqc", "big_containment", Group.SAFETY, 3, 1),
            new Node("relief_tank", "relief_tank_controller_sqc", "big_relief_tank", Group.SAFETY, 4, 0),
            new Node("pressurizer", "pressurizer_controller_sqc", "big_pressurizer", Group.CORE, 2, 1),
            new Node("emergency", "emergency_cooling_controller_sqc", "big_emergency", Group.SAFETY, 5, 0),
            new Node("hp_injection", "high_pressure_injection_controller_sqc", "big_hp_injection", Group.SAFETY, 3, 0),
            new Node("steam_generator", "steam_generator_controller_sqc", "big_steam_generator", Group.CORE, 4, 1),
            new Node("separator", "steam_separator_controller_sqc", "big_separator", Group.STEAM, 5, 1),
            new Node("dryer", "steam_dryer_controller_sqc", "big_dryer", Group.STEAM, 6, 1),
            new Node("drive", "control_rod_drive_controller_sqc", "rod_drive", Group.CORE, 3, 2),
            new Node("rack", "spent_fuel_rack_controller_sqc", "rod_spent", Group.CYCLE, 3, 5),
            new Node("shower", "decon_shower_sqc", "prot_shower", Group.CYCLE, 3, 6),
            new Node("castor", "castor_large_sqc", "rod_castor", Group.CYCLE, 2, 5),
            new Node("reprocessing", "reprocessing_plant_controller_sqc", "reprocessing_plant", Group.CYCLE, 3, 3),
            new Node("repository", "waste_repository_controller_sqc", "rod_repository", Group.CYCLE, 2, 6),
            new Node("breeder", "fast_breeder_controller_sqc", "fast_breeder", Group.CYCLE, 4, 3),
            new Node("weapons", "weapons_processing_controller_sqc", "weapons_processing", Group.CYCLE, 4, 4),
            new Node("oil_derrick", "oil_derrick_controller_sqc", "oil_derrick", Group.FUEL, 8, 5),
            new Node("distillation_column", "distillation_column_controller_sqc", "distillation_column", Group.FUEL, 9, 5),
            new Node("cracker", "cracker_controller_sqc", "cracker", Group.FUEL, 10, 5),
            new Node("gas_generator", "gas_generator_controller_sqc", "gas_generator", Group.POWER, 11, 5),
            new Node("polymer", "polymer_plant_controller_sqc", "rod_polymer", Group.FUEL, 9, 6),
            new Node("fluid_buffer", "fluid_buffer_controller_sqc", "fluid_buffer", Group.FUEL, 10, 6));

    public static final List<Edge> EDGES = new ArrayList<>();

    static {
        edge("coal", "ore", false);
        edge("solar_panel", "solar_collector", false);
        edge("solar_collector", "solar", false);
        edge("solar", "centrifuge", false);
        edge("centrifuge", "rtg", false);
        edge("wind_small", "wind_medium", false);
        edge("wind_medium", "wind_large", false);
        edge("wind_large", "ore", false);
        edge("ore", "centrifuge", false);
        edge("centrifuge", "rod", false);
        edge("rod", "drive", false);
        edge("steam_generator", "pumps", false);
        edge("pumps", "drive", false);
        edge("reheater", "turbine", true);
        edge("turbine", "shaft", false);
        edge("shaft", "substation", false);
        edge("substation", "mast", false);
        edge("mast", "mast_large", false);
        edge("mast_large", "battery", false);
        edge("turbine", "condenser", true);
        edge("tower", "condenser", false);
        edge("condenser", "treatment", true);
        edge("treatment", "preheater", true);
        edge("preheater", "fwpump", true);
        edge("fwpump", "steam_generator", true);
        edge("desk", "drive", false);
        edge("drive", "containment", false);
        edge("containment", "relief_tank", false);
        edge("pressurizer", "drive", false);
        edge("pressurizer", "relief_tank", false);
        edge("relief_tank", "hp_injection", false);
        edge("hp_injection", "drive", false);
        edge("relief_tank", "emergency", false);
        edge("emergency", "drive", false);
        edge("drive", "steam_generator", false);
        edge("steam_generator", "separator", true);
        edge("separator", "dryer", true);
        edge("dryer", "reheater", true);
        edge("dryer", "purifier", false);
        edge("drive", "rack", false);
        edge("rack", "shower", false);
        edge("rack", "castor", false);
        edge("purifier", "reprocessing", false);
        edge("reprocessing", "rod", false);
        edge("castor", "repository", false);
        edge("reprocessing", "breeder", false);
        edge("breeder", "weapons", false);
        edge("oil_derrick", "distillation_column", false);
        edge("distillation_column", "cracker", false);
        edge("cracker", "gas_generator", false);
        edge("distillation_column", "polymer", false);
        edge("distillation_column", "fluid_buffer", false);
        edge("gas_generator", "substation", false);
    }

    private PlantOverview() {}

    public static int index(String key) {
        for (int i = 0; i < NODES.size(); i++) if (NODES.get(i).key().equals(key)) return i;
        throw new IllegalArgumentException("Kein Knoten " + key);
    }

    private static void edge(String from, String to, boolean ring) {
        EDGES.add(new Edge(index(from), index(to), ring));
    }
}
