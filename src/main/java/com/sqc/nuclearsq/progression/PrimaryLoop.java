package com.sqc.nuclearsq.progression;

import java.util.List;

public final class PrimaryLoop {
    public record Node(String key, String item, String page, int x, int y, int nameW) {}

    public record Flow(int from, int to, String kind, int[] path, int lx, int ly, int align, int lw) {}

    public record Diagram(List<Node> nodes, List<Flow> flows, int[] order, int width, int height, int hull, int[] hullRect, int ring) {}

    public static final int WIDTH = 512, HEIGHT = 270;

    public static final int HULL = 0;

    public static final int[] HULL_RECT = {143, 8, 376, 192};

    public static final List<Node> NODES = List.of(
            new Node("pwr_containment", "containment_controller_sqc", "big_containment", 163, 30, 100),
            new Node("pwr_drive", "control_rod_drive_controller_sqc", "rod_drive", 202, 60, 100),
            new Node("pwr_steam_generator", "steam_generator_controller_sqc", "big_steam_generator", 330, 60, 100),
            new Node("pwr_pump", "main_coolant_pump_controller_sqc", "cool_bigpumps", 264, 148, 124),
            new Node("pwr_emergency", "emergency_cooling_controller_sqc", "big_emergency", 47, 60, 100),
            new Node("pwr_intake", "intake_pump_controller_sqc", "plant_intake", 47, 225, 100),
            new Node("pwr_treatment", "water_treatment_controller_sqc", "plant_treatment", 330, 225, 110),
            new Node("pwr_separator", "steam_separator_controller_sqc", "big_separator", 468, 60, 100),
            new Node("turbine", "turbine_controller_sqc", "energy_turbine", 468, 150, 100));

    public static final List<Flow> FLOWS = List.of(
            new Flow(1, 2, "hot", new int[] {202, 60, 330, 60}, 264, 50, 0, 84),
            new Flow(2, 3, "cold", new int[] {321, 60, 321, 148, 264, 148}, 314, 108, 1, 52),
            new Flow(3, 1, "cold", new int[] {264, 148, 202, 148, 202, 60}, 209, 108, -1, 50),
            new Flow(4, 1, "fill", new int[] {47, 60, 202, 60}, 103, 50, 0, 72),
            new Flow(5, 4, "water", new int[] {47, 225, 47, 60}, 40, 142, 1, 40),
            new Flow(5, 6, "bigpipe", new int[] {47, 225, 330, 225}, 189, 235, 0, 240),
            new Flow(6, 2, "feedwater", new int[] {339, 225, 339, 60}, 344, 200, -1, 110),
            new Flow(2, 7, "wetsteam", new int[] {330, 60, 468, 60}, 412, 50, 0, 66),
            new Flow(7, 8, "steam", new int[] {468, 60, 468, 150}, 474, 105, -1, 40),
            new Flow(5, 1, "hydraulic", new int[] {47, 135, 150, 135, 150, 72, 183, 72}, 96, 125, 0, 86));

    public static final int[] ORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    public static final Diagram DIAGRAM = new Diagram(NODES, FLOWS, ORDER, WIDTH, HEIGHT, HULL, HULL_RECT, 4);

    private PrimaryLoop() {}
}
