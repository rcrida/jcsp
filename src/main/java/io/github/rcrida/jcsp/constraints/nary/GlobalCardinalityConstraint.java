package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that requires each tracked value to appear a specific number of times
 * across a set of variables: {@code count(vars, v) == cardinality.get(v)} for each entry.
 * <p>
 * Values not present in the cardinality map are unconstrained (open GCC).
 * <p>
 * Equivalent to MiniZinc's {@code global_cardinality(vars, values, counts)} constraint.
 * Generalises {@link CountConstraint} (single value) and {@link AllDiffConstraint}
 * (all counts == 1).
 * <p>
 * {@link #propagate}/{@link #explainInfeasible} implement Régin's flow-based generalized arc
 * consistency algorithm — the same matching + residual-graph SCC idea {@link AllDiffConstraint}
 * uses for its own Régin (1994) algorithm, generalized from 0/1 bipartite matching to a flow
 * network with per-tracked-value exact capacities (Régin, "Generalized Arc Consistency for Global
 * Cardinality Constraint", AAAI 1996). Every untracked value is merged into one shared sink node
 * rather than modelled individually: this constraint never needs to know <em>which</em> untracked
 * value a variable takes, only that it can reach one, so merging is lossless for GAC purposes and
 * keeps the flow network's size independent of how many distinct untracked values appear.
 *
 * @see <a href="https://doi.org/10.1609/aaai.v1.10380">Régin (1996)</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GlobalCardinalityConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {
    @Singular private final Map<T, Integer> cardinalities;

    public static <T> GlobalCardinalityConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                                        @NonNull Map<T, Integer> cardinalities) {
        // A structural (domain-independent) infeasibility: no assignment of `variables` can ever
        // supply more total quota than there are variables to supply it. Failing fast here beats
        // discovering it only as an unexplained UNSAT deep in search -- and for exactly this shape
        // (Σ quotas > n), the flow-based propagator's own violating-subset extraction can return
        // empty (see findViolatingSubset's Javadoc), so search wouldn't even get a useful nogood.
        assert cardinalities.values().stream().mapToInt(Integer::intValue).sum() <= variables.size()
                : "sum of cardinalities exceeds variable count: no assignment can satisfy this GCC";
        return GlobalCardinalityConstraint.<T>builder()
                .variables(variables)
                .cardinalities(cardinalities)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        Map<T, Integer> counts = new HashMap<>();
        for (T v : values) counts.merge(v, 1, Integer::sum);

        for (var entry : cardinalities.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) > entry.getValue()) return false;
        }
        if (values.size() < getVariables().size()) return true;
        for (var entry : cardinalities.entrySet()) {
            if (!Objects.equals(counts.getOrDefault(entry.getKey(), 0), entry.getValue())) return false;
        }
        return true;
    }

    /**
     * The bipartite variable/value flow network for one {@link #propagate}/{@link
     * #explainInfeasible} call. Node numbering: {@code [0,n)} variables, {@code [n, n+t)} tracked
     * values ({@code t = cardinalities.size()}, in a fixed iteration order), {@code n+t} = the
     * single merged sink for every untracked value.
     */
    @SuppressWarnings("unchecked")
    private record FlowNetwork<T>(List<Variable<T>> vars, List<T> trackedValues,
                                   List<List<Integer>> varAdj, int[] quotas) {
        int untrackedNode() { return vars.size() + trackedValues.size(); }
        int bipartiteNodeCount() { return untrackedNode() + 1; }
    }

    @SuppressWarnings("unchecked")
    private FlowNetwork<T> buildNetwork(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) getVariables());
        List<T> trackedValues = new ArrayList<>(cardinalities.keySet());
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

        int[] quotas = new int[trackedValues.size()];
        for (int k = 0; k < trackedValues.size(); k++) quotas[k] = cardinalities.get(trackedValues.get(k));

        return new FlowNetwork<>(vars, trackedValues, varAdj, quotas);
    }

    /**
     * Minimal Edmonds-Karp max-flow: BFS-shortest augmenting paths, residual capacities tracked
     * via paired reverse edges (edge {@code e} and its reverse {@code e ^ 1}, the standard idiom
     * for allocating edges in consecutive forward/reverse pairs). Deliberately not extracted into
     * a shared top-level class: {@link GlobalCardinalityConstraint} is this codebase's only
     * consumer of real (non-0/1) flow, unlike {@link AllDiffConstraint}'s bipartite matching, which
     * only ever needs 0/1 capacities.
     */
    private static final class MaxFlow {
        private final int n;
        private final int[] edgeTo;
        private final int[] capacity;
        private int edgeCount;
        private final List<List<Integer>> adj;

        MaxFlow(int n, int maxEdges) {
            this.n = n;
            edgeTo = new int[maxEdges];
            capacity = new int[maxEdges];
            adj = new ArrayList<>(n);
            for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        }

        int addEdge(int u, int w, int cap) {
            int fwd = edgeCount;
            edgeTo[edgeCount] = w; capacity[edgeCount] = cap; edgeCount++;
            adj.get(u).add(fwd);
            int rev = edgeCount;
            edgeTo[edgeCount] = u; capacity[edgeCount] = 0; edgeCount++;
            adj.get(w).add(rev);
            return fwd;
        }

        boolean hasFlow(int forwardEdge) {
            return capacity[forwardEdge ^ 1] > 0;
        }

        int maxflow(int s, int t) {
            int total = 0;
            while (true) {
                int[] parentEdge = bfsAugmentingPath(s, t);
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

        private int[] bfsAugmentingPath(int s, int t) {
            int[] parentEdge = new int[n];
            Arrays.fill(parentEdge, -1);
            boolean[] visited = new boolean[n];
            visited[s] = true;
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int e : adj.get(u)) {
                    int w = edgeTo[e];
                    if (capacity[e] > 0 && !visited[w]) {
                        visited[w] = true;
                        parentEdge[w] = e;
                        queue.add(w);
                    }
                }
            }
            return parentEdge;
        }

        /** Nodes reachable from {@code s} via positive-residual-capacity edges in the current graph. */
        boolean[] reachableFrom(int s) {
            boolean[] visited = new boolean[n];
            visited[s] = true;
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int e : adj.get(u)) {
                    int w = edgeTo[e];
                    if (capacity[e] > 0 && !visited[w]) {
                        visited[w] = true;
                        queue.add(w);
                    }
                }
            }
            return visited;
        }
    }

    /**
     * The feasibility flow computed over {@link FlowNetwork}, shared by {@link #propagate} and
     * {@link #explainInfeasible} so the flow-with-lower-bounds computation lives in exactly one
     * place. {@link #varEdgeIndex} records each variable's candidate edges (as forward-edge
     * indices into {@link #flow}) so both callers can query which candidate currently carries
     * flow without recomputing the network.
     */
    private record FlowResult<T>(FlowNetwork<T> network, MaxFlow flow, boolean feasible,
                                  List<List<CandidateEdge>> varEdgeIndex, int superSource) {}

    /** One variable's candidate: which node it would route to, and that edge's forward index into {@link MaxFlow}. */
    private record CandidateEdge(int candidate, int forwardEdge) {}

    /**
     * Builds the reduced flow-with-lower-bounds network (the standard supersource/supersink
     * elimination of edge lower bounds) and computes max-flow feasibility.
     * <p>
     * Every {@code (S, var)} edge and every {@code (trackedValue, T)} edge is forced ({@code lo ==
     * hi == 1} / {@code n_v}), so both collapse to zero reduced capacity and are omitted entirely
     * — they can never carry flow in the reduced graph. Their forced contribution is captured
     * instead via the supersource edge into {@code T} (total {@code Σ n_v}) and the supersink edge
     * out of each tracked value ({@code n_v} each), exactly as the standard reduction prescribes.
     */
    private FlowResult<T> computeFlow(@NonNull Map<Variable<?>, Domain<?>> domains) {
        FlowNetwork<T> network = buildNetwork(domains);
        int n = network.vars().size();
        int t = network.trackedValues().size();
        int untrackedNode = network.untrackedNode();

        int sourceOriginal = untrackedNode + 1;
        int sinkOriginal = untrackedNode + 2;
        int superSource = untrackedNode + 3;
        int superSink = untrackedNode + 4;
        int totalNodes = untrackedNode + 5;

        int sumQuotas = 0;
        for (int q : network.quotas()) sumQuotas += q;

        int candidateEdgeCount = 0;
        for (List<Integer> adj : network.varAdj()) candidateEdgeCount += adj.size();
        // edges: n (S'->var) + 1 (S'->T) + candidateEdges (var->tracked/untracked) + 1 (U->T)
        // + t (trackedValue->T') + 1 (T->S) + 1 (S->T'); each addEdge allocates 2 slots.
        int maxEdges = 2 * (n + 1 + candidateEdgeCount + 1 + t + 1 + 1);

        MaxFlow flow = new MaxFlow(totalNodes, maxEdges);
        for (int i = 0; i < n; i++) flow.addEdge(superSource, i, 1);
        flow.addEdge(superSource, sinkOriginal, sumQuotas);

        List<List<CandidateEdge>> varEdgeIndex = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Integer> adj = network.varAdj().get(i);
            List<CandidateEdge> candidates = new ArrayList<>(adj.size());
            for (int candidate : adj) {
                candidates.add(new CandidateEdge(candidate, flow.addEdge(i, candidate, 1)));
            }
            varEdgeIndex.add(candidates);
        }

        flow.addEdge(untrackedNode, sinkOriginal, n);
        for (int k = 0; k < t; k++) flow.addEdge(network.vars().size() + k, superSink, network.quotas()[k]);
        flow.addEdge(sinkOriginal, sourceOriginal, n + sumQuotas + 1);
        flow.addEdge(sourceOriginal, superSink, n);

        int required = n + sumQuotas;
        int achieved = flow.maxflow(superSource, superSink);

        return new FlowResult<>(network, flow, achieved == required, varEdgeIndex, superSource);
    }

    /**
     * Builds the bipartite residual graph used for GAC filtering: for each variable's currently
     * unused candidate edge, a forward edge {@code (var, candidate)}; for its currently-used one, a
     * reversed edge {@code (candidate, var)} — the same construction {@link
     * AllDiffConstraint#propagate} uses for 0/1 matching (only reachable via {@link #computeFlow}
     * having already confirmed feasibility, so exactly one candidate per variable carries flow).
     * <p>
     * Deliberately omits the untracked sink's own {@code (untrackedNode, sinkOriginal)} edge and
     * its residual capacity from this graph — unlike a tracked value (exactly saturated whenever
     * feasible, {@code lo == hi}), the untracked sink has real slack, so it might look like that
     * slack needs its own residual representation for completeness. It doesn't: the sink's
     * capacity is fixed at the total variable count, a strict upper bound that can never actually
     * bind (at most that many variables exist to compete for it in the first place), so its
     * aggregate capacity never constrains anything beyond what each variable's own edge to it
     * already determines — a variable with the untracked sink among its candidates can always
     * reach it via that one edge, independent of how many other variables currently also do.
     * Modelling the sink's own slack explicitly would therefore only ever confirm "yes, room
     * available", never add discriminating information the per-variable edges don't already give.
     */
    private List<List<Integer>> buildResidualGraph(FlowResult<T> result) {
        int bipartiteNodes = result.network().bipartiteNodeCount();
        List<List<Integer>> graph = new ArrayList<>(bipartiteNodes);
        for (int i = 0; i < bipartiteNodes; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < result.network().vars().size(); i++) {
            for (CandidateEdge edge : result.varEdgeIndex().get(i)) {
                if (result.flow().hasFlow(edge.forwardEdge())) {
                    graph.get(edge.candidate()).add(i);
                } else {
                    graph.get(i).add(edge.candidate());
                }
            }
        }
        return graph;
    }

    private int[] tarjanSCC(List<List<Integer>> graph, int n) {
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

    private void strongconnect(int v, List<List<Integer>> graph,
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

    /**
     * Régin's GAC propagator, generalized from bipartite matching to flow-with-lower-bounds:
     * {@link #computeFlow} finds a feasible assignment (infeasible ⇒ no completion exists given
     * current domains), then {@link #buildResidualGraph} + {@link #tarjanSCC} identify every
     * currently-unused candidate edge that could <em>never</em> be part of any feasible
     * assignment. The merged untracked-value node is checked the same way as any tracked value —
     * an untracked <em>value</em> is never individually quota-limited, but a specific variable's
     * edge to the merged node can still be GAC-unsafe if every feasible completion needs that
     * variable to supply a tracked value's quota instead. When that edge is unsafe, every
     * untracked value currently in the variable's domain is pruned (there is no single value to
     * cite — the merged node stands for all of them).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        FlowResult<T> result = computeFlow(domains);
        if (!result.feasible()) return Optional.empty();

        List<List<Integer>> residual = buildResidualGraph(result);
        int[] scc = tarjanSCC(residual, result.network().bipartiteNodeCount());
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
                        if (!cardinalities.containsKey(val)) builder.delete(val);
                    }
                } else {
                    builder.delete(result.network().trackedValues().get(candidate - result.network().vars().size()));
                }
            }
            if (builder != null) updates.put(var, builder.build());
        }
        return Optional.of(updates);
    }

    /**
     * Finds the violating variable subset via the standard max-flow-min-cut construction: nodes
     * still reachable from the flow-with-lower-bounds reduction's supersource, restricted to
     * variable-nodes, once no more augmenting paths exist. This subsumes both Hall-type failure
     * modes a fixed-cardinality GCC can have — too many variables chasing too little combined
     * value capacity, or too few variables able to reach a high-quota value — without needing to
     * distinguish them: max-flow-min-cut duality certifies infeasibility either way from the same
     * reachable-set computation, the same way a single matching-based computation already covers
     * every {@link AllDiffConstraint} Hall violation without a separate case for each shape.
     */
    private Optional<List<Variable<?>>> findViolatingSubset(@NonNull Map<Variable<?>, Domain<?>> domains) {
        FlowResult<T> result = computeFlow(domains);
        if (result.feasible()) return Optional.empty();

        boolean[] reachable = result.flow().reachableFrom(result.superSource());
        List<Variable<?>> z = new ArrayList<>();
        for (int i = 0; i < result.network().vars().size(); i++) {
            if (reachable[i]) z.add(result.network().vars().get(i));
        }
        // z can genuinely be empty: when combined quotas structurally exceed the variable count
        // (Σ n_v > n) the resulting shortfall isn't attributable to any specific variable's own
        // routing failure — every individual variable could, in isolation, still route
        // successfully; the deficiency is a pure aggregate-count mismatch the min-cut locates
        // entirely on the value/bookkeeping side of the network. Confirmed reachable empirically
        // (not just a theoretical guard), not a defensive check against something impossible.
        return z.isEmpty() ? Optional.empty() : Optional.of(z);
    }

    /**
     * Attributes infeasibility to the violating subset found by {@link #findViolatingSubset},
     * mirroring {@link AllDiffConstraint#explainInfeasible}'s exact two-tier fallback: a ground
     * reason via {@link Propagatable#allSingletonReason} when every violator is currently
     * singleton, else a {@link RangeNogoodConstraint} over the same subset's current bounds. Which
     * one of the two Hall-type conditions actually failed is never inspected — the violating
     * subset alone is enough for either fallback, exactly as it is for {@link AllDiffConstraint}.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return findViolatingSubset(domains).flatMap(zVars -> {
            Map<Variable<?>, Object> ground = Propagatable.allSingletonReason(zVars, domains);
            if (!ground.isEmpty()) return GroundNogoodConstraint.fromReason(ground);
            return RangeNogoodConstraint.fromCurrentBounds(zVars, domains);
        });
    }

    @Override
    public String getRelation() {
        return "GlobalCardinality(" + cardinalities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}
