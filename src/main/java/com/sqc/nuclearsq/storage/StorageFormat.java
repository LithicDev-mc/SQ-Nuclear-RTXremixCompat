package com.sqc.nuclearsq.storage;

public final class StorageFormat {
    private StorageFormat() {}

    public static String count(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 10_000) return trim(n / 1000.0) + "k";
        if (n < 1_000_000) return (n / 1000) + "k";
        if (n < 10_000_000) return trim(n / 1_000_000.0) + "M";
        if (n < 1_000_000_000) return (n / 1_000_000) + "M";
        return trim(n / 1_000_000_000.0) + "G";
    }

    private static String trim(double v) {
        double r = Math.floor(v * 10) / 10;
        return r == Math.floor(r) ? Long.toString((long) r) : String.valueOf(r).replace('.', ',');
    }
}
