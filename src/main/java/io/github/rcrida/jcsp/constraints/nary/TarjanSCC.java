package io.github.rcrida.jcsp.constraints.nary;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Tarjan's strongly-connected-components algorithm over a plain adjacency-list directed graph,
 * shared by {@link AllDiffConstraint} and {@link GlobalCardinalityConstraint}'s residual-graph GAC
 * filtering — both run Régin-style matching/flow algorithms whose GAC step is "two nodes are safe
 * to connect iff they're in the same SCC of the residual graph", differing only in how the residual
 * graph itself is built (0/1 matching vs. flow-with-lower-bounds).
 */
final class TarjanSCC {
    private TarjanSCC() {}

    /**
     * @param graph adjacency list over node indices {@code [0, n)}
     * @param n     number of nodes
     * @return each node's component index, positioned by node index
     */
    static int[] compute(List<List<Integer>> graph, int n) {
        int[] disc = new int[n];
        int[] low = new int[n];
        int[] scc = new int[n];
        boolean[] onStack = new boolean[n];
        Arrays.fill(disc, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        int[] counter = {0};
        int[] sccCount = {0};
        for (int i = 0; i < n; i++) {
            if (disc[i] == -1) strongconnect(i, graph, disc, low, scc, onStack, stack, counter, sccCount);
        }
        return scc;
    }

    private static void strongconnect(int v, List<List<Integer>> graph,
                                       int[] disc, int[] low, int[] scc,
                                       boolean[] onStack, Deque<Integer> stack,
                                       int[] counter, int[] sccCount) {
        disc[v] = low[v] = counter[0]++;
        stack.push(v);
        onStack[v] = true;
        for (int w : graph.get(v)) {
            if (disc[w] == -1) {
                strongconnect(w, graph, disc, low, scc, onStack, stack, counter, sccCount);
                low[v] = Math.min(low[v], low[w]);
            } else if (onStack[w]) {
                low[v] = Math.min(low[v], disc[w]);
            }
        }
        if (low[v] == disc[v]) {
            int component = sccCount[0]++;
            int w;
            do {
                w = stack.pop();
                onStack[w] = false;
                scc[w] = component;
            } while (w != v);
        }
    }
}
