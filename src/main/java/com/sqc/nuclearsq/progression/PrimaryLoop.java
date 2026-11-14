package com.sqc.nuclearsq.progression;

import java.util.List;

import com.sqc.nuclearsq.progression.ReactorLoop.Node;

public final class PrimaryLoop {
    public static final List<Node> NODES = List.of(
            new Node("pwr_drive", "control_rod_drive_controller_sqc", "rod_drive", -1, 0, 0),
            new Node("pwr_pressurizer", "pressurizer_controller_sqc", "big_pressurizer", -1, 0, 0),
            new Node("pwr_steam_generator", "steam_generator_controller_sqc", "big_steam_generator", -1, 0, 0),
            new Node("pwr_pump", "main_coolant_pump_controller_sqc", "cool_bigpumps", -1, 0, 0),
            new Node("pwr_relief", "relief_tank_controller_sqc", "big_relief_tank", 1, 100, 0),
            new Node("pwr_hp_injection", "high_pressure_injection_controller_sqc", "big_hp_injection", 0, 22, -62),
            new Node("pwr_emergency", "emergency_cooling_controller_sqc", "big_emergency", 0, 0, 66));

    public static final int RING = 4;
    public static final int TOP = 1;

    public static final int[] ORDER = {0, 5, 6, 1, 4, 2, 3};

    private PrimaryLoop() {}
}
