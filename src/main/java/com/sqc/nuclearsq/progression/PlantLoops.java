package com.sqc.nuclearsq.progression;

import java.util.List;

import com.sqc.nuclearsq.progression.PrimaryLoop.Diagram;
import com.sqc.nuclearsq.progression.PrimaryLoop.Flow;
import com.sqc.nuclearsq.progression.PrimaryLoop.Node;

public final class PlantLoops {
    public static final List<Node> EXTENDED_NODES = List.of(
            new Node("pwr_containment", "containment_controller_sqc", "big_containment", 163, 30, 100),
            new Node("pwr_drive", "control_rod_drive_controller_sqc", "rod_drive", 202, 60, 100),
            new Node("pwr_steam_generator", "steam_generator_controller_sqc", "big_steam_generator", 330, 60, 100),
            new Node("pwr_pump", "main_coolant_pump_controller_sqc", "cool_bigpumps", 264, 156, 124),
            new Node("pwr_emergency", "emergency_cooling_controller_sqc", "big_emergency", 47, 60, 100),
            new Node("pwr_intake", "intake_pump_controller_sqc", "plant_intake", 47, 225, 100),
            new Node("pwr_treatment", "water_treatment_controller_sqc", "plant_treatment", 330, 232, 110),
            new Node("pwr_separator", "steam_separator_controller_sqc", "big_separator", 468, 60, 100),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", 468, 150, 100),
            new Node("pwr_pressurizer", "pressurizer_controller_sqc", "big_pressurizer", 272, 98, 100),
            new Node("pwr_relief", "relief_tank_controller_sqc", "big_relief_tank", 400, 98, 100),
            new Node("pwr_hp_injection", "high_pressure_injection_controller_sqc", "big_hp_injection", 424, 214, 130),
            new Node("tower", "cooling_tower_large_controller_sqc", "big_tower", 476, 268, 100));

    public static final List<Flow> EXTENDED_FLOWS = List.of(
            new Flow(1, 2, "hot", new int[] {202, 60, 330, 60}, 264, 50, 0, 84),
            new Flow(2, 3, "cold", new int[] {321, 60, 321, 156, 264, 156}, 314, 140, 1, 52),
            new Flow(3, 1, "cold", new int[] {264, 156, 202, 156, 202, 60}, 196, 126, 1, 50),
            new Flow(4, 1, "fill", new int[] {47, 60, 202, 60}, 103, 50, 0, 72),
            new Flow(5, 4, "water", new int[] {47, 225, 47, 60}, 40, 142, 1, 40),
            new Flow(5, 6, "bigpipe", new int[] {47, 232, 330, 232}, 189, 242, 0, 240),
            new Flow(6, 2, "feedwater", new int[] {339, 232, 339, 60}, 344, 172, -1, 110),
            new Flow(2, 7, "wetsteam", new int[] {330, 60, 468, 60}, 412, 50, 0, 66),
            new Flow(7, 8, "steam", new int[] {468, 60, 468, 150}, 462, 105, 1, 40),
            new Flow(5, 1, "hydraulic", new int[] {47, 135, 150, 135, 150, 72, 183, 72}, 96, 125, 0, 86),
            new Flow(3, 9, "cold", new int[] {202, 98, 254, 98}, 228, 108, 0, 40),
            new Flow(9, 10, "relief", new int[] {290, 98, 382, 98}, 358, 112, 0, 60),
            new Flow(10, 11, "cold", new int[] {414, 116, 414, 196}, 420, 150, -1, 40),
            new Flow(11, 3, "cold", new int[] {406, 204, 202, 204, 202, 156}, 280, 214, 0, 40),
            new Flow(10, 12, "coolant", new int[] {418, 92, 500, 92, 500, 268, 494, 268}, 496, 186, 1, 60));

    public static final Diagram EXTENDED = new Diagram(EXTENDED_NODES, EXTENDED_FLOWS, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 512, 300, 0, new int[] {143, 8, 376, 192}, 4);

    public static final List<Node> FULL_NODES = List.of(
            new Node("pwr_containment", "containment_controller_sqc", "big_containment", 163, 30, 100),
            new Node("pwr_drive", "control_rod_drive_controller_sqc", "rod_drive", 202, 60, 100),
            new Node("pwr_steam_generator", "steam_generator_controller_sqc", "big_steam_generator", 330, 60, 100),
            new Node("pwr_pump", "main_coolant_pump_controller_sqc", "cool_bigpumps", 264, 156, 124),
            new Node("pwr_emergency", "emergency_cooling_controller_sqc", "big_emergency", 47, 60, 100),
            new Node("pwr_intake", "intake_pump_controller_sqc", "plant_intake", 47, 225, 100),
            new Node("pwr_treatment", "water_treatment_controller_sqc", "plant_treatment", 200, 290, 110),
            new Node("pwr_separator", "steam_separator_controller_sqc", "big_separator", 468, 60, 100),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", 468, 150, 100),
            new Node("pwr_pressurizer", "pressurizer_controller_sqc", "big_pressurizer", 272, 98, 100),
            new Node("pwr_relief", "relief_tank_controller_sqc", "big_relief_tank", 400, 98, 100),
            new Node("pwr_hp_injection", "high_pressure_injection_controller_sqc", "big_hp_injection", 402, 206, 130),
            new Node("pump", "feedwater_pump_controller_sqc", "plant_fwpump", 300, 246, 100),
            new Node("condenser", "condenser_controller_sqc", "plant_condenser", 480, 290, 100),
            new Node("tower", "cooling_tower_xl_controller_sqc", "big_tower", 384, 290, 100));

    public static final List<Flow> FULL_FLOWS = List.of(
            new Flow(1, 2, "hot", new int[] {202, 60, 330, 60}, 264, 50, 0, 84),
            new Flow(2, 3, "cold", new int[] {321, 60, 321, 156, 264, 156}, 314, 140, 1, 52),
            new Flow(3, 1, "cold", new int[] {264, 156, 202, 156, 202, 60}, 196, 126, 1, 50),
            new Flow(4, 1, "fill", new int[] {47, 60, 202, 60}, 103, 50, 0, 72),
            new Flow(5, 4, "water", new int[] {47, 225, 47, 60}, 40, 142, 1, 40),
            new Flow(5, 6, "bigpipe", new int[] {47, 225, 47, 290, 200, 290}, 104, 300, 0, 240),
            new Flow(6, 12, "feedwater", new int[] {218, 290, 300, 290, 300, 264}, 256, 284, 0, 80),
            new Flow(12, 2, "feedwater", new int[] {318, 246, 339, 246, 339, 60}, 344, 170, -1, 110),
            new Flow(2, 7, "wetsteam", new int[] {330, 60, 468, 60}, 412, 50, 0, 66),
            new Flow(7, 8, "steam", new int[] {468, 60, 468, 150}, 474, 105, -1, 40),
            new Flow(5, 1, "hydraulic", new int[] {47, 135, 150, 135, 150, 72, 183, 72}, 96, 125, 0, 86),
            new Flow(3, 9, "cold", new int[] {202, 98, 254, 98}, 228, 108, 0, 40),
            new Flow(9, 10, "relief", new int[] {290, 98, 382, 98}, 358, 112, 0, 60),
            new Flow(10, 11, "cold", new int[] {414, 116, 414, 188}, 420, 150, -1, 40),
            new Flow(11, 3, "cold", new int[] {384, 206, 202, 206, 202, 156}, 262, 216, 0, 40),
            new Flow(8, 13, "exhaust", new int[] {468, 168, 468, 272}, 462, 256, 1, 50),
            new Flow(13, 14, "coolant", new int[] {462, 290, 402, 290}, 432, 280, 0, 60),
            new Flow(13, 6, "water", new int[] {480, 308, 480, 322, 200, 322, 200, 308}, 300, 318, 0, 60));

    public static final Diagram FULL = new Diagram(FULL_NODES, FULL_FLOWS, new int[] {0, 1, 2, 3, 4, 5, 6, 12, 7, 8, 13, 14, 9, 10, 11}, 512, 330, 0, new int[] {143, 8, 376, 192}, 4);

    private PlantLoops() {}
}
