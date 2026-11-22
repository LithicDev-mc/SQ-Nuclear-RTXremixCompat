package com.sqc.nuclearsq.progression;

import java.util.List;

import com.sqc.nuclearsq.progression.PrimaryLoop.Diagram;
import com.sqc.nuclearsq.progression.PrimaryLoop.Flow;
import com.sqc.nuclearsq.progression.PrimaryLoop.Node;

public final class ReactorLoop {
    public static final List<Node> NODES = List.of(
            new Node("steam_generator", "steam_generator_controller_sqc", "big_steam_generator", 168, 40, 104),
            new Node("dryer", "steam_dryer_controller_sqc", "big_dryer", 278, 40, 96),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", 372, 40, 80),
            new Node("condenser", "condenser_controller_sqc", "plant_condenser", 372, 230, 84),
            new Node("treatment", "water_treatment_controller_sqc", "plant_treatment", 278, 230, 106),
            new Node("pump", "feedwater_pump_controller_sqc", "plant_fwpump", 168, 230, 106),
            new Node("reactor", "control_rod_drive_controller_sqc", "rod_drive", 50, 40, 90),
            new Node("shaft", "shaft_generator_controller_sqc", "energy_shaft", 466, 40, 90),
            new Node("substation", "substation_controller_sqc", "transport_substation", 466, 140, 90),
            new Node("tower", "cooling_tower_large_controller_sqc", "big_tower", 466, 230, 90),
            new Node("assembler", PlantOverview.ASSEMBLER_ITEM, "reactor_assembler", 50, 140, 96));

    public static final List<Flow> FLOWS = List.of(
            new Flow(6, 0, "hot", new int[] {50, 33, 168, 33}, 107, 25, 0, 70),
            new Flow(0, 6, "cold", new int[] {168, 47, 50, 47}, 100, 62, 0, 70),
            new Flow(0, 1, "wetsteam", new int[] {168, 40, 278, 40}, 222, 30, 0, 66),
            new Flow(1, 2, "steam", new int[] {278, 40, 372, 40}, 322, 30, 0, 52),
            new Flow(2, 3, "exhaust", new int[] {372, 40, 372, 230}, 366, 140, 1, 90),
            new Flow(3, 4, "water", new int[] {372, 230, 278, 230}, 322, 220, 0, 52),
            new Flow(4, 5, "feedwater", new int[] {278, 230, 168, 230}, 221, 220, 0, 68),
            new Flow(5, 0, "feedwater", new int[] {168, 230, 168, 40}, 174, 140, -1, 90),
            new Flow(2, 7, "shaft", new int[] {372, 40, 466, 40}, 418, 30, 0, 44),
            new Flow(7, 8, "power", new int[] {466, 40, 466, 140}, 460, 95, 1, 50),
            new Flow(3, 9, "coolant", new int[] {372, 230, 466, 230}, 418, 244, 0, 56),
            new Flow(10, 6, "rods", new int[] {50, 140, 50, 40}, 56, 95, -1, 100));

    public static final int[] ORDER = {6, 0, 1, 2, 7, 8, 3, 9, 4, 5, 10};

    public static final Diagram DIAGRAM = new Diagram(NODES, FLOWS, ORDER, 512, 272, -1, null, 6);

    private ReactorLoop() {}
}
