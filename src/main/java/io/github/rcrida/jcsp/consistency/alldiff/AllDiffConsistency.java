package io.github.rcrida.jcsp.consistency.alldiff;

import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.*;

/**
 * Applies Régin's GAC propagator for {@link AllDiffConstraint}, iterating to fixpoint.
 * <p>
 * Standard pairwise arc consistency (AC3) decomposes AllDiff into binary ≠ constraints
 * and misses higher-order inferences — e.g. naked pairs/triples. This propagator detects
 * any such redundant values by:
 * <ol>
 *   <li>Building a bipartite graph (variables ↔ domain values) and finding a maximum matching.
 *       If no perfect matching exists, the constraint is infeasible (Hall's theorem).</li>
 *   <li>Constructing a directed graph from the matching and finding its Strongly Connected
 *       Components (SCCs) via Tarjan's algorithm.</li>
 *   <li>Removing every unmatched edge that crosses SCC boundaries — these values can never
 *       appear in any solution to the AllDiff.</li>
 * </ol>
 *
 * @see <a href="https://doi.org/10.1016/0004-3702(94)90060-4">Régin (1994)</a>
 */
@Slf4j
public class AllDiffConsistency {
    public static final AllDiffConsistency INSTANCE = new AllDiffConsistency();

    private AllDiffConsistency() {}

    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<AllDiffConstraint<?>> constraints = (List) csp.getConstraints().stream()
                .filter(c -> c instanceof AllDiffConstraint<?>)
                .toList();

        if (constraints.isEmpty()) return Optional.of(csp);

        var current = csp;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AllDiffConstraint<?> constraint : constraints) {
                var result = propagate(constraint, current.getVariableDomains());
                if (result.isEmpty()) {
                    log.warn("AllDiffConsistency: infeasible detected");
                    return Optional.empty();
                }
                var updates = result.get();
                if (!updates.isEmpty()) {
                    var builder = current.toBuilder();
                    for (var entry : updates.entrySet()) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        var variable = (Variable) entry.getKey();
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        var domain   = (Domain) entry.getValue();
                        builder.variableDomainEntry(variable, domain);
                    }
                    current = builder.build();
                    changed = true;
                    log.debug("AllDiffConsistency: pruned domains for {}", updates.keySet());
                }
            }
        }
        log.info("AllDiffConsistency: fixpoint reached");
        return Optional.of(current);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<Map<Variable<?>, Domain<?>>> propagate(
            AllDiffConstraint<T> constraint,
            Map<Variable<?>, Domain<?>> domains) {

        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) constraint.getVariables());
        int n = vars.size();

        // Enumerate all distinct values across domains; assign each a compact integer index
        List<Object> valueList = new ArrayList<>();
        Map<Object, Integer> valueIndex = new LinkedHashMap<>();
        for (Variable<T> v : vars) {
            Domain<T> dom = (Domain<T>) domains.get(v);
            for (T val : dom.toList()) {
                if (!valueIndex.containsKey(val)) {
                    valueIndex.put(val, valueList.size());
                    valueList.add(val);
                }
            }
        }
        int m = valueList.size();

        // Build variable adjacency lists (variable i → value indices in its domain)
        List<List<Integer>> varAdj = new ArrayList<>(n);
        for (Variable<T> v : vars) {
            Domain<T> dom = (Domain<T>) domains.get(v);
            varAdj.add(dom.stream().map(val -> valueIndex.get(val)).toList());
        }

        // --- Step 1: Maximum bipartite matching via DFS augmenting paths ---
        // matchVar[i] = value index matched to variable i (-1 = unmatched)
        // matchVal[j] = variable index matched to value j (-1 = unmatched)
        int[] matchVar = new int[n];
        int[] matchVal = new int[m];
        Arrays.fill(matchVar, -1);
        Arrays.fill(matchVal, -1);

        int matchingSize = 0;
        for (int i = 0; i < n; i++) {
            if (augment(i, varAdj, matchVar, matchVal, new boolean[m])) matchingSize++;
        }

        if (matchingSize < n) return Optional.empty(); // Hall violation

        // --- Step 2: Build directed graph ---
        // Node layout: 0..n-1 = variables, n..n+m-1 = values, n+m = free node T
        int freeNode = n + m;
        int totalNodes = freeNode + 1;
        List<List<Integer>> graph = new ArrayList<>(totalNodes);
        for (int i = 0; i < totalNodes; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int mv = matchVar[i];
            graph.get(n + mv).add(i);            // matched: value → variable (reversed)
            for (int vj : varAdj.get(i)) {
                if (vj != mv) graph.get(i).add(n + vj); // unmatched: variable → value
            }
        }
        // Free values → T, and T → all values.
        // T acts as a dummy variable whose domain spans all values, ensuring that unmatched
        // values can participate in alternating cycles with the real variables. Without the
        // T → all-values edges, unmatched values would be isolated sinks and be incorrectly
        // pruned from every domain.
        boolean hasFreeValues = false;
        for (int j = 0; j < m; j++) {
            if (matchVal[j] == -1) {
                graph.get(n + j).add(freeNode);
                hasFreeValues = true;
            }
        }
        if (hasFreeValues) {
            for (int j = 0; j < m; j++) graph.get(freeNode).add(n + j);
        }

        // --- Step 3: Tarjan's SCC ---
        int[] scc = tarjanSCC(graph, totalNodes);

        // --- Step 4: Prune values in different SCCs from the matched variable ---
        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int mv = matchVar[i];
            Domain<T> dom = (Domain<T>) domains.get(vars.get(i));
            Domain.Builder<T> builder = null;
            for (int vj : varAdj.get(i)) {
                if (vj != mv && scc[i] != scc[n + vj]) {
                    if (builder == null) builder = dom.toBuilder();
                    builder.delete(valueList.get(vj));
                }
            }
            if (builder != null) {
                updates.put(vars.get(i), builder.build());
            }
        }
        return Optional.of(updates);
    }

    /** DFS augmenting path for bipartite matching. Returns true if variable {@code u} was matched. */
    private boolean augment(int u, List<List<Integer>> adj, int[] matchVar, int[] matchVal,
                             boolean[] visited) {
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                visited[v] = true;
                if (matchVal[v] == -1 || augment(matchVal[v], adj, matchVar, matchVal, visited)) {
                    matchVar[u] = v;
                    matchVal[v] = u;
                    return true;
                }
            }
        }
        return false;
    }

    /** Tarjan's SCC — returns component id per node (equal id = same SCC). */
    private int[] tarjanSCC(List<List<Integer>> graph, int n) {
        int[] disc     = new int[n];
        int[] low      = new int[n];
        int[] scc      = new int[n];
        boolean[] onStack = new boolean[n];
        Arrays.fill(disc, -1);

        Deque<Integer> stack = new ArrayDeque<>();
        int[] counter  = {0};
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
}
