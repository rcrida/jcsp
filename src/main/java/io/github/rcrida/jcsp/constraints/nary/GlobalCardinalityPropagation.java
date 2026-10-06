package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The flow-with-lower-bounds machinery shared by {@link GlobalCardinalityConstraint} (fixed
 * {@code [min,max]} occurrence ranges) and {@link GlobalCardinalityVariableConstraint} (each
 * tracked value's occurrence count is itself a variable, re-read fresh on every call). Extracted
 * rather than duplicated -- both build the identical bipartite variable/value flow network and run
 * the identical Régin GAC filtering; the only thing that differs between the two callers is
 * <em>where</em> each tracked value's {@code [lo,hi]} bounds come from, which is why every entry
 * point here takes {@code lo}/{@code hi} as plain {@code int[]} rather than reading them from a
 * constraint field directly. See {@link GlobalCardinalityConstraint}'s own Javadoc for the
 * algorithm itself (Régin, "Generalized Arc Consistency for Global Cardinality Constraint", AAAI
 * 1996) and the merged-untracked-sink / excess-edge design; this class only relocates the
 * mechanism, it doesn't change it.
 */
final class GlobalCardinalityPropagation {
    private GlobalCardinalityPropagation() {}

    /**
     * Node numbering: {@code [0,n)} variables, {@code [n, n+t)} tracked values ({@code t =
     * trackedValues.size()}, in caller-supplied order), {@code n+t} = the single merged sink for
     * every untracked value.
     */
    @SuppressWarnings("unchecked")
    private record FlowNetwork<T>(List<Variable<T>> vars, List<T> trackedValues,
                                   List<List<Integer>> varAdj, int[] lo, int[] hi) {
        int untrackedNode() { return vars.size() + trackedValues.size(); }
        int bipartiteNodeCount() { return untrackedNode() + 1; }
    }

    @SuppressWarnings("unchecked")
    private static <T> FlowNetwork<T> buildNetwork(Set<Variable<T>> variables, List<T> trackedValues,
                                                     int[] lo, int[] hi, Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> vars = new ArrayList<>(variables);
        Map<T, Integer> trackedIndex = new HashMap<>();
        for (int k = 0; k < trackedValues.size(); k++) trackedIndex.put(trackedValues.get(k), k);
        int n = vars.size();
        int untrackedNode = n + trackedValues.size();

        List<List<Integer>> varAdj = new ArrayList<>(n);
        for (Variable<T> v : vars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            List<Integer> adj = new ArrayList<>();
            boolean sawUntracked = false;
            for (T val : dom.toList()) {
                Integer idx = trackedIndex.get(val);
                if (idx != null) adj.add(n + idx);
                else sawUntracked = true;
            }
            if (sawUntracked) adj.add(untrackedNode);
            varAdj.add(adj);
        }

        return new FlowNetwork<>(vars, trackedValues, varAdj, lo, hi);
    }

    /**
     * Minimal Edmonds-Karp max-flow: BFS-shortest augmenting paths, residual capacities tracked
     * via paired reverse edges (edge {@code e} and its reverse {@code e ^ 1}, the standard idiom
     * for allocating edges in consecutive forward/reverse pairs).
     */
    private static final class MaxFlow {
        private final int n;
        private final int[] edgeTo;
        private final int[] capacity;
        private int edgeCount;
        /**
         * Per-node incident edge indices as plain {@code int[]} rows with their own lengths, rather
         * than {@code List<List<Integer>>}. Every enqueue and every adjacency step in
         * {@link #bfsAugmentingPath} boxed an {@code int} otherwise, and node indices here run well
         * past {@link Integer}'s cache, so each one allocated: JFR on {@code BinPacking-tab-n1c1w4a}
         * put {@link ArrayDeque#addLast} alone at 71% of the whole solve, and this method's stack at
         * roughly 85%. Rows are appended to in insertion order and grown by doubling, which keeps
         * iteration order identical to the {@link List} form -- max-flow's *value* is independent of
         * augmenting-path order, but which specific maximum flow is found is not, and
         * {@link #findViolatingSubset} reads a min-cut off it.
         */
        private final int[][] adj;
        private final int[] adjSize;
        /** Reused across the augmentations of one {@link #maxflow}, which runs one BFS per unit of flow. */
        private final int[] queue;
        private final int[] parentEdge;
        private final boolean[] visited;

        MaxFlow(int n, int maxEdges) {
            this.n = n;
            edgeTo = new int[maxEdges];
            capacity = new int[maxEdges];
            adj = new int[n][];
            adjSize = new int[n];
            queue = new int[n];
            parentEdge = new int[n];
            visited = new boolean[n];
        }

        private void append(int node, int edge) {
            int[] row = adj[node];
            if (row == null) {
                row = new int[4];
                adj[node] = row;
            } else if (adjSize[node] == row.length) {
                row = Arrays.copyOf(row, row.length * 2);
                adj[node] = row;
            }
            row[adjSize[node]++] = edge;
        }

        int addEdge(int u, int w, int cap) {
            int fwd = edgeCount;
            edgeTo[edgeCount] = w; capacity[edgeCount] = cap; edgeCount++;
            append(u, fwd);
            int rev = edgeCount;
            edgeTo[edgeCount] = u; capacity[edgeCount] = 0; edgeCount++;
            append(w, rev);
            return fwd;
        }

        boolean hasFlow(int forwardEdge) {
            return capacity[forwardEdge ^ 1] > 0;
        }

        /** Whether {@code forwardEdge} has remaining forward residual capacity (room to carry more flow). */
        boolean hasResidualCapacity(int forwardEdge) {
            return capacity[forwardEdge] > 0;
        }

        /**
         * Pushes this path's bottleneck along {@code forwardEdges}, returning how much moved (zero
         * when any edge is already saturated). Only sound for a genuine source-to-sink path, which
         * is why {@link #greedyWarmStart} enumerates the two fixed path shapes rather than taking
         * arbitrary edge lists.
         */
        int pushPath(int... forwardEdges) {
            int bottleneck = Integer.MAX_VALUE;
            for (int e : forwardEdges) bottleneck = Math.min(bottleneck, capacity[e]);
            if (bottleneck == 0) return 0;
            for (int e : forwardEdges) {
                capacity[e] -= bottleneck;
                capacity[e ^ 1] += bottleneck;
            }
            return bottleneck;
        }

        int maxflow(int s, int t) {
            int total = 0;
            while (true) {
                bfsAugmentingPath(s, t);
                if (parentEdge[t] == -1) return total;
                int bottleneck = Integer.MAX_VALUE;
                for (int v = t; v != s; v = edgeTo[parentEdge[v] ^ 1]) {
                    bottleneck = Math.min(bottleneck, capacity[parentEdge[v]]);
                }
                for (int v = t; v != s; v = edgeTo[parentEdge[v] ^ 1]) {
                    capacity[parentEdge[v]] -= bottleneck;
                    capacity[parentEdge[v] ^ 1] += bottleneck;
                }
                total += bottleneck;
            }
        }

        /** Leaves the path in {@link #parentEdge}, where {@code parentEdge[t] == -1} means none exists. */
        private void bfsAugmentingPath(int s, int t) {
            Arrays.fill(parentEdge, -1);
            Arrays.fill(visited, false);
            visited[s] = true;
            int head = 0;
            int tail = 0;
            queue[tail++] = s;
            while (head < tail) {
                int u = queue[head++];
                int[] row = adj[u];
                for (int i = 0, size = adjSize[u]; i < size; i++) {
                    int e = row[i];
                    int w = edgeTo[e];
                    if (capacity[e] > 0 && !visited[w]) {
                        visited[w] = true;
                        parentEdge[w] = e;
                        queue[tail++] = w;
                    }
                }
            }
        }

        /** Nodes reachable from {@code s} via positive-residual-capacity edges in the current graph. */
        boolean[] reachableFrom(int s) {
            boolean[] reached = new boolean[n];
            reached[s] = true;
            int head = 0;
            int tail = 0;
            queue[tail++] = s;
            while (head < tail) {
                int u = queue[head++];
                int[] row = adj[u];
                for (int i = 0, size = adjSize[u]; i < size; i++) {
                    int e = row[i];
                    int w = edgeTo[e];
                    if (capacity[e] > 0 && !reached[w]) {
                        reached[w] = true;
                        queue[tail++] = w;
                    }
                }
            }
            return reached;
        }
    }

    /**
     * The feasibility flow computed over {@link FlowNetwork}, shared by {@code propagate} and
     * {@code explainInfeasible} callers so the flow-with-lower-bounds computation lives in exactly
     * one place. {@link #varEdgeIndex} records each variable's candidate edges (as forward-edge
     * indices into {@link #flow}) so both callers can query which candidate currently carries flow
     * without recomputing the network. {@link #untrackedToSinkEdge} and
     * {@link #excessEdgeByTrackedIndex} expose the two edge families {@link #buildResidualGraph}
     * needs to represent {@code sinkOriginal}'s own residual capacity; {@code
     * excessEdgeByTrackedIndex[k]} is {@code -1} when tracked value {@code k}'s range has
     * {@code min == max} (no excess edge was added, mirroring the exact-count case's forced,
     * zero-reduced-capacity edge).
     */
    record FlowResult<T>(FlowNetwork<T> network, MaxFlow flow, boolean feasible,
                          List<List<CandidateEdge>> varEdgeIndex, int superSource,
                          int sinkOriginal, int untrackedToSinkEdge, int[] excessEdgeByTrackedIndex) {}

    /** One variable's candidate: which node it would route to, and that edge's forward index into {@link MaxFlow}. */
    private record CandidateEdge(int candidate, int forwardEdge) {}

    /**
     * Builds the reduced flow-with-lower-bounds network (the standard supersource/supersink
     * elimination of edge lower bounds) and computes max-flow feasibility.
     * <p>
     * Every {@code (S, var)} edge is forced ({@code lo == hi == 1}), so it collapses to zero
     * reduced capacity and is omitted entirely -- it can never carry flow in the reduced graph.
     * Each {@code (trackedValue, T)} edge has bounds {@code [lo_v, hi_v]}: its forced {@code lo_v}
     * portion is likewise omitted (captured instead via the supersource/supersink edges below), but
     * its excess {@code hi_v - lo_v} portion is real reduced capacity and gets its own edge
     * (skipped when {@code lo_v == hi_v}, the exact-count case, where it would be zero-capacity
     * anyway).
     */
    static <T> FlowResult<T> computeFlow(Set<Variable<T>> variables, List<T> trackedValues,
                                          int[] lo, int[] hi, Map<Variable<?>, Domain<?>> domains) {
        FlowNetwork<T> network = buildNetwork(variables, trackedValues, lo, hi, domains);
        int n = network.vars().size();
        int t = network.trackedValues().size();
        int untrackedNode = network.untrackedNode();

        int sourceOriginal = untrackedNode + 1;
        int sinkOriginal = untrackedNode + 2;
        int superSource = untrackedNode + 3;
        int superSink = untrackedNode + 4;
        int totalNodes = untrackedNode + 5;

        int sumLo = 0;
        for (int v : network.lo()) sumLo += v;

        int candidateEdgeCount = 0;
        for (List<Integer> adj : network.varAdj()) candidateEdgeCount += adj.size();
        // edges: n (S'->var) + 1 (S'->T) + candidateEdges (var->tracked/untracked) + 1 (U->T)
        // + t (trackedValue->T', forced lo portion) + t (trackedValue->T, excess hi-lo portion,
        // only some actually added) + 1 (T->S) + 1 (S->T'); each addEdge allocates 2 slots.
        int maxEdges = 2 * (n + 1 + candidateEdgeCount + 1 + t + t + 1 + 1);

        MaxFlow flow = new MaxFlow(totalNodes, maxEdges);
        int[] sourceToVarEdge = new int[n];
        for (int i = 0; i < n; i++) sourceToVarEdge[i] = flow.addEdge(superSource, i, 1);
        int sourceToSinkOriginalEdge = flow.addEdge(superSource, sinkOriginal, sumLo);

        List<List<CandidateEdge>> varEdgeIndex = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Integer> adj = network.varAdj().get(i);
            List<CandidateEdge> candidates = new ArrayList<>(adj.size());
            for (int candidate : adj) {
                candidates.add(new CandidateEdge(candidate, flow.addEdge(i, candidate, 1)));
            }
            varEdgeIndex.add(candidates);
        }

        int untrackedToSinkEdge = flow.addEdge(untrackedNode, sinkOriginal, n);
        int[] excessEdgeByTrackedIndex = new int[t];
        int[] trackedToSuperSinkEdge = new int[t];
        for (int k = 0; k < t; k++) {
            trackedToSuperSinkEdge[k] = flow.addEdge(network.vars().size() + k, superSink, network.lo()[k]);
            int excess = network.hi()[k] - network.lo()[k];
            excessEdgeByTrackedIndex[k] = excess > 0
                    ? flow.addEdge(network.vars().size() + k, sinkOriginal, excess)
                    : -1;
        }
        int sinkToSourceEdge = flow.addEdge(sinkOriginal, sourceOriginal, n + sumLo + 1);
        int sourceToSuperSinkEdge = flow.addEdge(sourceOriginal, superSink, n);

        int required = n + sumLo;
        int achieved = greedyWarmStart(flow, varEdgeIndex, sourceToVarEdge, trackedToSuperSinkEdge,
                sourceToSinkOriginalEdge, sinkToSourceEdge, sourceToSuperSinkEdge, n)
                + flow.maxflow(superSource, superSink);

        return new FlowResult<>(network, flow, achieved == required, varEdgeIndex, superSource,
                sinkOriginal, untrackedToSinkEdge, excessEdgeByTrackedIndex);
    }

    /**
     * Saturates the two augmenting-path shapes that need no search, before
     * {@link MaxFlow#maxflow} looks for the rest: {@code S' -> var -> trackedValue -> T'} for each
     * variable that can take some tracked value with quota left, and the bookkeeping path
     * {@code S' -> sinkOriginal -> sourceOriginal -> T'} carrying the forced lower-bound portion.
     * <p>
     * Edmonds-Karp spends one whole breadth-first sweep per unit of flow, and this network needs
     * {@code n + sumLo} of them -- on {@code BinPacking-tab-n1c1w4a} about 240 sweeps over 5,000
     * edges for every single call. Nearly all of that flow follows one of the two shapes above and
     * can be placed directly, leaving the search to handle only the variables that actually
     * contend. This is a warm start, not a different algorithm: these are ordinary augmenting
     * paths, so the subsequent {@link MaxFlow#maxflow} still runs to saturation and still returns
     * the same maximum, which is what keeps feasibility and
     * {@link #propagateFromFlow}'s filtering unchanged.
     * <p>
     * Which <em>particular</em> maximum flow is reached does change, and
     * {@link #findViolatingSubset} reads a min-cut off it, so an infeasible network can now cite a
     * different (equally valid) violating subset than it did before.
     *
     * @return how much flow was placed, to be added to what {@link MaxFlow#maxflow} finds next
     */
    private static int greedyWarmStart(MaxFlow flow, List<List<CandidateEdge>> varEdgeIndex,
                                            int[] sourceToVarEdge, int[] trackedToSuperSinkEdge,
                                            int sourceToSinkOriginalEdge, int sinkToSourceEdge,
                                            int sourceToSuperSinkEdge, int n) {
        int placed = 0;
        for (int i = 0; i < n; i++) {
            for (CandidateEdge edge : varEdgeIndex.get(i)) {
                // Candidates are numbered from n, tracked values first, so this is the tracked index
                // and equals t exactly for the merged untracked node -- the one candidate this shape
                // doesn't cover, since its route to the sink runs through sinkOriginal. Left to the
                // search below.
                int trackedIndex = edge.candidate() - n;
                if (trackedIndex == trackedToSuperSinkEdge.length) continue;
                int pushed = flow.pushPath(sourceToVarEdge[i], edge.forwardEdge(),
                        trackedToSuperSinkEdge[trackedIndex]);
                placed += pushed;
                if (pushed > 0) break;
            }
        }
        return placed + flow.pushPath(sourceToSinkOriginalEdge, sinkToSourceEdge, sourceToSuperSinkEdge);
    }

    /**
     * Builds the residual graph used for GAC filtering: for each variable's currently unused
     * candidate edge, a forward edge {@code (var, candidate)}; for its currently-used one, a
     * reversed edge {@code (candidate, var)} -- the same construction {@link AllDiffConstraint#propagate}
     * uses for 0/1 matching (only reachable via {@link #computeFlow} having already confirmed
     * feasibility, so exactly one candidate per variable carries flow).
     * <p>
     * Also includes {@code sinkOriginal} itself as one extra node (index {@code
     * network.bipartiteNodeCount()}, one past the untracked sink), with residual edges for the
     * untracked sink's own {@code (untrackedNode, sinkOriginal)} edge and each tracked value's
     * excess-capacity edge -- needed for the range case (a real {@code [min,max]} range's excess
     * edge has genuine residual capacity that can bridge otherwise-unconnected tracked values
     * through {@code sinkOriginal}), safely omittable only for an all-exact-count network (every
     * edge's reduced capacity would be zero either way).
     */
    private static <T> List<List<Integer>> buildResidualGraph(FlowResult<T> result) {
        int bipartiteNodes = result.network().bipartiteNodeCount();
        int sinkNode = bipartiteNodes;
        List<List<Integer>> graph = new ArrayList<>(bipartiteNodes + 1);
        for (int i = 0; i < bipartiteNodes + 1; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < result.network().vars().size(); i++) {
            for (CandidateEdge edge : result.varEdgeIndex().get(i)) {
                if (result.flow().hasFlow(edge.forwardEdge())) {
                    graph.get(edge.candidate()).add(i);
                } else {
                    graph.get(i).add(edge.candidate());
                }
            }
        }

        int untrackedNode = result.network().untrackedNode();
        addResidualBothWays(graph, result.flow(), result.untrackedToSinkEdge(), untrackedNode, sinkNode);

        int n = result.network().vars().size();
        int[] excessEdges = result.excessEdgeByTrackedIndex();
        for (int k = 0; k < excessEdges.length; k++) {
            if (excessEdges[k] != -1) {
                addResidualBothWays(graph, result.flow(), excessEdges[k], n + k, sinkNode);
            }
        }
        return graph;
    }

    private static void addResidualBothWays(List<List<Integer>> graph, MaxFlow flow, int forwardEdge, int from, int to) {
        if (flow.hasResidualCapacity(forwardEdge)) graph.get(from).add(to);
        if (flow.hasFlow(forwardEdge)) graph.get(to).add(from);
    }

    /**
     * Régin's GAC filtering, generalized from bipartite matching to flow-with-lower-bounds: given
     * an already-feasible {@link FlowResult}, {@link #buildResidualGraph} + {@link TarjanSCC}
     * identify every currently-unused candidate edge that could <em>never</em> be part of any
     * feasible assignment. The merged untracked-value node is checked the same way as any tracked
     * value -- an untracked <em>value</em> is never individually quota-limited, but a specific
     * variable's edge to the merged node can still be GAC-unsafe if every feasible completion needs
     * that variable to supply a tracked value's quota instead. When that edge is unsafe, every
     * untracked value currently in the variable's domain is pruned (there is no single value to
     * cite -- the merged node stands for all of them).
     *
     * @param trackedValueSet {@code result}'s own tracked values, as a {@link Set} for the
     *                        untracked-node membership check (callers already have this list built
     *                        for {@link #computeFlow}, so it's threaded through rather than rebuilt)
     */
    @SuppressWarnings("unchecked")
    static <T> Map<Variable<?>, Domain<?>> propagateFromFlow(FlowResult<T> result, Set<T> trackedValueSet,
                                                               Map<Variable<?>, Domain<?>> domains) {
        List<List<Integer>> residual = buildResidualGraph(result);
        int[] scc = TarjanSCC.compute(residual, result.network().bipartiteNodeCount() + 1);
        int untrackedNode = result.network().untrackedNode();

        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        for (int i = 0; i < result.network().vars().size(); i++) {
            Variable<T> var = result.network().vars().get(i);
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
            DiscreteDomain.Builder<T> builder = null;
            for (CandidateEdge edge : result.varEdgeIndex().get(i)) {
                int candidate = edge.candidate();
                if (result.flow().hasFlow(edge.forwardEdge()) || scc[i] == scc[candidate]) continue;
                if (builder == null) builder = dom.toBuilder();
                if (candidate == untrackedNode) {
                    for (T val : dom.toList()) {
                        if (!trackedValueSet.contains(val)) builder.delete(val);
                    }
                } else {
                    builder.delete(result.network().trackedValues().get(candidate - result.network().vars().size()));
                }
            }
            if (builder != null) updates.put(var, builder.build());
        }
        return updates;
    }

    /**
     * Finds the violating variable subset via the standard max-flow-min-cut construction: nodes
     * still reachable from the flow-with-lower-bounds reduction's supersource, restricted to
     * variable-nodes, once no more augmenting paths exist. This subsumes both Hall-type failure
     * modes a bounded GCC can have -- too many variables chasing too little combined value
     * capacity, or too few variables able to reach a high-minimum value -- without needing to
     * distinguish them: max-flow-min-cut duality certifies infeasibility either way from the same
     * reachable-set computation.
     */
    static <T> Optional<List<Variable<?>>> findViolatingSubset(FlowResult<T> result) {
        if (result.feasible()) return Optional.empty();

        boolean[] reachable = result.flow().reachableFrom(result.superSource());
        List<Variable<?>> z = new ArrayList<>();
        for (int i = 0; i < result.network().vars().size(); i++) {
            if (reachable[i]) z.add(result.network().vars().get(i));
        }
        // z can genuinely be empty: when combined minimums structurally exceed the variable count
        // (Σ lo_v > n) the resulting shortfall isn't attributable to any specific variable's own
        // routing failure -- the deficiency is a pure aggregate-count mismatch the min-cut locates
        // entirely on the value/bookkeeping side of the network.
        return z.isEmpty() ? Optional.empty() : Optional.of(z);
    }
}
