package com.sqc.nuclearsq.transport;

public enum TransportTier {
    BASIC("basic", 1000, 1024, 4),
    REINFORCED("reinforced", 4000, 8192, 16),
    HARDENED("hardened", 16000, 131072, 64),
    SUPERCONDUCTOR("superconductor", 0, 8388608, 0);

    public final String name;
    public final int pipeRate, cableRate, itemRate;

    TransportTier(String name, int pipeRate, int cableRate, int itemRate) {
        this.name = name;
        this.pipeRate = pipeRate;
        this.cableRate = cableRate;
        this.itemRate = itemRate;
    }
}
