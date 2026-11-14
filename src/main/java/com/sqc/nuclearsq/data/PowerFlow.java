package com.sqc.nuclearsq.data;

public record PowerFlow(Kind kind, double now, double peak, long usedTotal, long stored, long capacity, double in, double out) {
    public enum Kind { PRODUCER, CONSUMER, STORAGE }

    public static PowerFlow producer(double now, double peak) {
        return new PowerFlow(Kind.PRODUCER, now, peak, 0, 0, 0, 0, 0);
    }

    public static PowerFlow consumer(long usedTotal, double peak) {
        return new PowerFlow(Kind.CONSUMER, 0, peak, usedTotal, 0, 0, 0, 0);
    }

    public static PowerFlow storage(long stored, long capacity, double in, double out) {
        return new PowerFlow(Kind.STORAGE, in - out, 0, 0, stored, capacity, in, out);
    }
}
