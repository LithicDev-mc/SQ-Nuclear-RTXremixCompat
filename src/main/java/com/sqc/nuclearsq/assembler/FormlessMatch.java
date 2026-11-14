package com.sqc.nuclearsq.assembler;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

public final class FormlessMatch {
    private FormlessMatch() {}

    @Nullable
    public static int[][] plan(List<? extends Predicate<ItemStack>> needs, int[] counts, List<ItemStack> slots) {
        int n = needs.size(), m = slots.size();
        boolean any = false;
        boolean[][] fits = new boolean[n][m];
        for (int j = 0; j < m; j++) {
            ItemStack s = slots.get(j);
            if (s.isEmpty()) continue;
            any = true;
            boolean known = false;
            for (int i = 0; i < n; i++) {
                fits[i][j] = needs.get(i).test(s);
                known |= fits[i][j];
            }
            if (!known) return null;
        }
        if (!any) return null;
        int size = n + m + 2, sink = size - 1;
        int[][] cap = new int[size][size];
        int want = 0;
        for (int i = 0; i < n; i++) {
            cap[0][1 + i] = counts[i];
            want += counts[i];
            for (int j = 0; j < m; j++) if (fits[i][j]) cap[1 + i][1 + n + j] = Integer.MAX_VALUE;
        }
        for (int j = 0; j < m; j++) cap[1 + n + j][sink] = slots.get(j).getCount();
        int[][] flow = new int[size][size];
        int total = 0;
        int[] prev = new int[size];
        while (total < want) {
            java.util.Arrays.fill(prev, -1);
            prev[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && prev[sink] < 0) {
                int u = queue.poll();
                for (int v = 0; v < size; v++) {
                    if (prev[v] < 0 && cap[u][v] - flow[u][v] > 0) {
                        prev[v] = u;
                        queue.add(v);
                    }
                }
            }
            if (prev[sink] < 0) break;
            int push = Integer.MAX_VALUE;
            for (int v = sink; v != 0; v = prev[v]) push = Math.min(push, cap[prev[v]][v] - flow[prev[v]][v]);
            for (int v = sink; v != 0; v = prev[v]) {
                flow[prev[v]][v] += push;
                flow[v][prev[v]] -= push;
            }
            total += push;
        }
        if (total < want) return null;
        int[][] plan = new int[n][m];
        for (int i = 0; i < n; i++) for (int j = 0; j < m; j++) plan[i][j] = Math.max(0, flow[1 + i][1 + n + j]);
        return plan;
    }
}
