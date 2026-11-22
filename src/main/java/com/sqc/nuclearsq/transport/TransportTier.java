package com.sqc.nuclearsq.transport;

public enum TransportTier {
    BASIC("basic", 1000, 4096, 4),
    REINFORCED("reinforced", 4000, 32768, 16),
    HARDENED("hardened", 16000, 524288, 64),
    SUPERCONDUCTOR("superconductor", 0, 33554432, 0);

    public final String name;
    public final int pipeRate, cableRate, itemRate;

    TransportTier(String name, int pipeRate, int cableRate, int itemRate) {
        this.name = name;
        this.pipeRate = pipeRate;
        this.cableRate = cableRate;
        this.itemRate = itemRate;
    }
}
