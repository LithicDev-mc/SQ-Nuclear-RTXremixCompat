package com.sqc.nuclearsq.reactor;

import java.util.SplittableRandom;

public final class NeutronMC {
    public static final int NX = 7, NZ = 7, NY = 8, HISTORIES = 4000;
    public static final float MIN_FACTOR = 0.75f, MAX_FACTOR = 1.25f;
    public static final float COUPLING = 0.4f;
    private static final double ALBEDO = 0.8, NU = 2.45;
    private static final double[][] FUEL = {{0.10, 0.40, 0.10}, {1.60, 0.85, 0.05}};
    private static final double[][] WATER = {{0.00, 0.0, 0.50}, {0.015, 0.0, 0.50}};
    private static final double[] THERMALIZE = {0.45, 0.50, 0.55, 0.65, 0.75};
    private static final long SEED = 0x5EEDL;

    private static final double[] K_REF = new double[THERMALIZE.length];
    private SplittableRandom rng = new SplittableRandom(SEED);
    private final boolean[] fuel = new boolean[NX * NY * NZ];
    private float factor = 1f;

    public double k(int[] cellX, int[] cellZ, float[] depth, int moderator) {
        rng = new SplittableRandom(SEED);
        java.util.Arrays.fill(fuel, false);
        int n = 0;
        for (int i = 0; i < cellX.length; i++) {
            int top = Math.round(Mth01(depth[i]) * NY);
            for (int y = 0; y < top; y++) { fuel[(y * NZ + cellZ[i]) * NX + cellX[i]] = true; n++; }
        }
        if (n == 0) return 0;
        double therm = THERMALIZE[Math.max(0, Math.min(moderator, THERMALIZE.length - 1))];
        int[] fuelIdx = new int[n];
        for (int c = 0, j = 0; c < fuel.length; c++) if (fuel[c]) fuelIdx[j++] = c;
        double born = 0;
        for (int h = 0; h < HISTORIES; h++) {
            int c0 = fuelIdx[rng.nextInt(n)];
            double x = c0 % NX + rng.nextDouble(), z = (c0 / NX) % NZ + rng.nextDouble(), y = c0 / (NX * NZ) + rng.nextDouble();
            int g = 0;
            for (int guard = 0; guard < 200; guard++) {
                double uz = 2 * rng.nextDouble() - 1, phi = 2 * Math.PI * rng.nextDouble(), s = Math.sqrt(1 - uz * uz);
                double[] sg = sigma(x, y, z, g);
                double d = -Math.log(1 - rng.nextDouble()) / (sg[0] + sg[2]);
                double nxp = x + s * Math.cos(phi) * d, nzp = z + uz * d, nyp = y + s * Math.sin(phi) * d;
                if (nxp < 0 || nzp < 0 || nyp < 0 || nxp >= NX || nzp >= NZ || nyp >= NY) {
                    if (rng.nextDouble() < ALBEDO) { g = 1; x = Math.min(NX - 1e-6, Math.max(0, nxp)); z = Math.min(NZ - 1e-6, Math.max(0, nzp)); y = Math.min(NY - 1e-6, Math.max(0, nyp)); continue; }
                    break;
                }
                x = nxp; z = nzp; y = nyp;
                sg = sigma(x, y, z, g);
                if (rng.nextDouble() * (sg[0] + sg[2]) < sg[0]) {
                    if (rng.nextDouble() < sg[1]) born += NU;
                    break;
                }
                if (g == 0 && !isFuel(x, y, z) && rng.nextDouble() < therm) g = 1;
            }
        }
        return born / HISTORIES;
    }

    private static float Mth01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private boolean isFuel(double x, double y, double z) { return fuel[(((int) y) * NZ + (int) z) * NX + (int) x]; }

    private double[] sigma(double x, double y, double z, int g) { return isFuel(x, y, z) ? FUEL[g] : WATER[g]; }

    private double kRef(int[] cx, int[] cz, int moderator) {
        int m = Math.max(0, Math.min(moderator, K_REF.length - 1));
        if (K_REF[m] == 0) {
            float[] full = new float[cx.length];
            java.util.Arrays.fill(full, 1f);
            K_REF[m] = new NeutronMC().k(cx, cz, full, m);
        }
        return K_REF[m];
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, Float> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public float tick(int[] cx, int[] cz, float[] depth, int moderator) {
        long key = 0;
        for (float d : depth) key = key * (NY + 1) + Math.round(Mth01(d) * NY);
        key = key * THERMALIZE.length + Math.max(0, Math.min(moderator, THERMALIZE.length - 1));
        key = key * 16 + depth.length;
        Float f = CACHE.get(key);
        if (f == null) {
            double k = k(cx, cz, depth, moderator);
            f = k <= 0 ? 1f : (float) Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, 1f + COUPLING * (float) (k / kRef(cx, cz, moderator) - 1)));
            CACHE.put(key, f);
        }
        factor = f;
        return factor;
    }

    public float factor() { return factor; }
}
