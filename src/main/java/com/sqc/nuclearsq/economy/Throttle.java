package com.sqc.nuclearsq.economy;

public final class Throttle {
    private final int max;
    private final long window;
    private long start;
    private int count;

    public Throttle(int max, long window) {
        this.max = max;
        this.window = window;
    }

    public void hit(long now) {
        if (now - start >= window || now < start) {
            start = now;
            count = 0;
        }
        if (++count > max) throw new BankException("too many requests, slow down");
    }
}
