package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.progression.PlantOverview.Edge;
import com.sqc.nuclearsq.progression.PlantOverview.Group;
import com.sqc.nuclearsq.progression.PlantOverview.Node;

public final class AssemblyTree {
    public static final List<Node> NODES = List.of(
            new Node("coal", "coal_generator_controller_sqc", "energy_start", Group.START, 0, 0),
            new Node("solar", "solar_field_controller_sqc", "energy_solar", Group.START, 0, 2),
            new Node("small_parts", "small_parts_assembler_controller_sqc", "small_parts_assembler", Group.ASSEMBLY, 1, 1),
            new Node("clean_room", "clean_room_controller_sqc", "clean_room", Group.ASSEMBLY, 4, 0),
            new Node("assembler", PlantOverview.ASSEMBLER_ITEM, "reactor_assembler", Group.ASSEMBLY, 2, 1),
            new Node("equipment", "equipment_assembler_controller_sqc", "equipment_assembler", Group.ASSEMBLY, 3, 3),
            new Node("plate_mill", "plate_mill_controller_sqc", "plate_mill", Group.ASSEMBLY, 2, 3),
            new Node("hammer_mill", "hammer_mill_controller_sqc", "hammer_mill", Group.ASSEMBLY, 1, 2),
            new Node("exo_station", "exo_station_controller_sqc", "exo_station", Group.ASSEMBLY, 5, 3),
            new Node("micro", "micro_assembler_controller_sqc", "micro_assembler", Group.ASSEMBLY, 2, 0),
            new Node("decon_dock", "decon_dock_controller_sqc", "decon_dock", Group.ASSEMBLY, 4, 3),
            new Node("solar_panel", "solar_panel_sqc", "energy_solar", Group.START, 2, 2),
            new Node("solar_collector", "solar_collector_sqc", "energy_solar", Group.START, 3, 2),
            new Node("rtg", "rtg_sqc", "energy_rtg", Group.START, 1, 3),
            new Node("rod_gripper", "rod_gripper_sqc", "rod_gripper", Group.ASSEMBLY, 5, 2),
            new Node("particle", "particle_assembler_controller_sqc", "particle_assembler", Group.ASSEMBLY, 3, 0),
            new Node("heavy", "heavy_assembler_controller_sqc", "mach_heavy", Group.ASSEMBLY, 4, 1),
            new Node("component", "component_assembler_controller_sqc", "component_assembler", Group.ASSEMBLY, 5, 1),
            new Node("compute_asm", "compute_assembler_controller_sqc", "compute_assembler", Group.ASSEMBLY, 3, 1),
            new Node("quantum", "quantum_computer_controller_sqc", "quantum_computer", Group.ASSEMBLY, 6, 1));

    public static final List<Edge> EDGES = new ArrayList<>();

    static {
        edge("coal", "small_parts");
        edge("solar", "small_parts");
        edge("small_parts", "assembler");
        edge("small_parts", "micro");
        edge("assembler", "solar_panel");
        edge("assembler", "solar_collector");
        edge("assembler", "rtg");
        edge("assembler", "equipment");
        edge("assembler", "plate_mill");
        edge("assembler", "hammer_mill");
        edge("assembler", "rod_gripper");
        edge("equipment", "decon_dock");
        edge("equipment", "exo_station");
        edge("assembler", "particle");
        edge("particle", "heavy");
        edge("heavy", "component");
        edge("heavy", "clean_room");
        edge("assembler", "compute_asm");
        edge("component", "quantum");
    }

    private AssemblyTree() {}

    public static int index(String key) {
        for (int i = 0; i < NODES.size(); i++) if (NODES.get(i).key().equals(key)) return i;
        throw new IllegalArgumentException("Kein Knoten " + key);
    }

    private static void edge(String from, String to) {
        EDGES.add(new Edge(index(from), index(to), false));
    }
}
