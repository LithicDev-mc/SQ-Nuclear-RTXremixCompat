package com.sqc.nuclearsq.nanobot.client;

public final class BotUv {
    private BotUv() {}

    public static final int W = 512, H = 256;

    public enum Kind {
        ABD(0, 0, 50, 36, 60),
        BODY(220, 0, 40, 26, 46),
        HEAD(392, 0, 28, 22, 26),
        LED(0, 96, 12, 4, 46),
        TIBIA(116, 96, 6, 6, 54),
        FEMUR(236, 96, 8, 8, 40),
        FEMUR_WARN(332, 96, 8, 8, 40),
        KNEE(428, 96, 10, 10, 10),
        SHINE_A(0, 156, 12, 2, 24),
        SHINE_B(72, 156, 8, 2, 12),
        EYE(112, 156, 8, 8, 4),
        CHELI(136, 156, 4, 4, 12),
        HAIR_A(168, 156, 2, 12, 2),
        HAIR_H(176, 156, 2, 10, 2);

        public final int x, y, dx, dy, dz;

        Kind(int x, int y, int dx, int dy, int dz) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public float[] rect(int face) {
            int px, py, pw, ph;
            switch (face) {
                case 0 -> { px = x + dz; py = y + dz; pw = dx; ph = dy; }
                case 1 -> { px = x + 2 * dz + dx; py = y + dz; pw = dx; ph = dy; }
                case 2 -> { px = x; py = y + dz; pw = dz; ph = dy; }
                case 3 -> { px = x + dz + dx; py = y + dz; pw = dz; ph = dy; }
                case 4 -> { px = x + dz; py = y; pw = dx; ph = dz; }
                default -> { px = x + dz + dx; py = y; pw = dx; ph = dz; }
            }
            return new float[] {px / (float) W, py / (float) H, (px + pw) / (float) W, (py + ph) / (float) H};
        }
    }
}
