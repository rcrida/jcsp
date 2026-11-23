package io.github.rcrida.jcsp.constraints.nary;

import java.util.Arrays;
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
        // The component stack, and the depth-first path with how far each node on it has scanned
        // its edges: explicit, because a path can pass through every node, and recursing that deep
        // overflowed the thread's stack at a few thousand. Nodes are visited in the order a
        // recursive search would visit them, so components are numbered the same way.
        int[] stack = new int[n];
        int top = 0;
        int[] path = new int[n];
        int[] scanned = new int[n];
        int counter = 0;
        int sccCount = 0;
        for (int root = 0; root < n; root++) {
            if (disc[root] != -1) continue;
            int depth = 0;
            path[0] = root;
            scanned[0] = 0;
            disc[root] = low[root] = counter++;
            stack[top++] = root;
            onStack[root] = true;
            while (depth >= 0) {
                int v = path[depth];
                List<Integer> edges = graph.get(v);
                if (scanned[depth] < edges.size()) {
                    int w = edges.get(scanned[depth]++);
                    if (disc[w] == -1) {
                        disc[w] = low[w] = counter++;
                        stack[top++] = w;
                        onStack[w] = true;
                        path[++depth] = w;
                        scanned[depth] = 0;
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], disc[w]);
                    }
                    continue;
                }
                if (low[v] == disc[v]) {
                    int component = sccCount++;
                    int w;
                    do {
                        w = stack[--top];
                        onStack[w] = false;
                        scc[w] = component;
                    } while (w != v);
                }
                if (--depth >= 0) {
                    int parent = path[depth];
                    low[parent] = Math.min(low[parent], low[v]);
                }
            }
        }
        return scc;
    }
}
