package com.sqc.nuclearsq.client;

public final class ScrollbarDrag {
    private int x, y, w, h, thumb, max, grab;
    private boolean on, drag;

    public void clear() {
        on = false;
    }

    public void track(int x, int y, int w, int h, int thumb, int max) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.thumb = thumb;
        this.max = max;
        on = max > 0;
    }

    public int press(double mx, double my, int scroll) {
        if (!on || mx < x - 2 || mx >= x + w + 1 || my < y || my >= y + h) return -1;
        int ty = y + Math.round((h - thumb) * scroll / (float) max);
        grab = my >= ty && my < ty + thumb ? (int) my - ty : thumb / 2;
        drag = true;
        return to(my);
    }

    public int drag(double my) {
        return drag ? to(my) : -1;
    }

    public void release() {
        drag = false;
    }

    private int to(double my) {
        int span = Math.max(1, h - thumb);
        return Math.max(0, Math.min(max, Math.round((float) ((my - grab - y) * max / span))));
    }
}
