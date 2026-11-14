package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.progression.PlantOverview.Edge;
import com.sqc.nuclearsq.progression.PlantOverview.Group;
import com.sqc.nuclearsq.progression.PlantOverview.Node;

public final class SecretWeaponsTree {
    public static final List<Node> NODES = List.of(
            new Node("assembler", PlantOverview.ASSEMBLER_ITEM, "reactor_assembler", Group.ASSEMBLY, 0, 1),
            new Node("uranium_core", "uranium_core_sqc", "rod_fuel", Group.FUEL, 1, 0),
            new Node("bomb_small", "nuclear_bomb_sqc", "weapons_bomb", Group.WEAPON, 1, 1),
            new Node("bomb_large", "nuclear_bomb_large_sqc", "weapons_bomb_large", Group.WEAPON, 2, 1),
            new Node("weapons_core", "weapons_plutonium_core_sqc", "weapons_processing", Group.FUEL, 2, 0),
            new Node("bomb_tsar", "nuclear_bomb_tsar_sqc", "weapons_tsar", Group.WEAPON, 3, 1));

    public static final List<Edge> EDGES = new ArrayList<>();

    static {
        edge("assembler", "bomb_small");
        edge("uranium_core", "bomb_small");
        edge("bomb_small", "bomb_large");
        edge("weapons_core", "bomb_large");
        edge("bomb_large", "bomb_tsar");
        edge("weapons_core", "bomb_tsar");
    }

    private SecretWeaponsTree() {}

    public static int index(String key) {
        for (int i = 0; i < NODES.size(); i++) if (NODES.get(i).key().equals(key)) return i;
        throw new IllegalArgumentException("Kein Knoten " + key);
    }

    private static void edge(String from, String to) {
        EDGES.add(new Edge(index(from), index(to), false));
    }
}
