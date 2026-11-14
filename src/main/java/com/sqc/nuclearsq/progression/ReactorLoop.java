package com.sqc.nuclearsq.progression;

import java.util.List;

public final class ReactorLoop {
    public record Node(String key, String item, String page, int parent, int dx, int dy) {}

    public static final List<Node> NODES = List.of(
            new Node("steam_generator", "steam_generator_controller_sqc", "big_steam_generator", -1, 0, 0),
            new Node("dryer", "steam_dryer_controller_sqc", "big_dryer", -1, 0, 0),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", -1, 0, 0),
            new Node("condenser", "condenser_controller_sqc", "plant_condenser", -1, 0, 0),
            new Node("treatment", "water_treatment_controller_sqc", "plant_treatment", -1, 0, 0),
            new Node("pump", "feedwater_pump_controller_sqc", "plant_fwpump", -1, 0, 0),
            new Node("reactor", "control_rod_drive_controller_sqc", "rod_drive", 0, -62, 44),
            new Node("shaft", "shaft_generator_controller_sqc", "energy_shaft", 2, -62, -52),
            new Node("substation", "substation_controller_sqc", "transport_substation", 2, 62, -52),
            new Node("tower", "cooling_tower_large_controller_sqc", "big_tower", 3, 72, -36),
            new Node("assembler", PlantOverview.ASSEMBLER_ITEM, "reactor_assembler", 5, -70, 6));

    public static final int RING = 6;

    public static final int[] ORDER = {0, 6, 1, 2, 7, 8, 3, 9, 4, 5, 10};

    private ReactorLoop() {}
}
