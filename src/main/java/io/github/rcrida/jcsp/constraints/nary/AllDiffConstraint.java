package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.consistency.PropagationResult;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Represents the "all-different" constraint in a constraint satisfaction problem (CSP).
 * This constraint ensures that all variables involved in the assignment have distinct values.
 * <p>
 * The following rules are used for evaluation:
 * - If the assignment contains fewer than two values, the constraint is trivially satisfied.
 * - For two assigned values, the constraint is satisfied if the values are different.
 * - For three or more assigned values, the constraint ensures all values are unique.
 * <p>
 * This implementation is thread-safe as it uses immutable data structures
 * provided by the {@link io.github.rcrida.jcsp.assignments.Assignment} and ensures no internal state mutation.
 */
@SuperBuilder
public class AllDiffConstraint<T> extends UniformNaryConstraint<T> implements Propagatable, BinaryDecomposable {

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        val size = values.size();
        if (size < 2) return true;
        if (size == 2) {
            val iterator = values.iterator();
            return !Objects.equals(iterator.next(), iterator.next());
        }
        val deduped = new HashSet<T>();
        for (T value : values) {
            if (!deduped.add(value)) return false;
        }
        return true;
    }

    @Override
    public String getRelation() {
        return "AllDiff";
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryNotEqualsConstraint.of(
                        (Variable) variables.get(i),
                        (Variable) variables.get(j)));
            }
        }
        return binaryConstraints;
    }

    /**
     * Régin's GAC propagator: bipartite matching + Tarjan SCC.
     * Detects naked pairs/triples and any other Hall-set violation without backtracking.
     *
     * @see <a href="https://doi.org/10.1016/0004-3702(94)90060-4">Régin (1994)</a>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) getVariables());
        int n = vars.size();

        List<Object> valueList = new ArrayList<>();
        Map<Object, Integer> valueIndex = new LinkedHashMap<>();
        for (Variable<T> v : vars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            for (T val : dom.toList()) {
                if (!valueIndex.containsKey(val)) {
                    valueIndex.put(val, valueList.size());
                    valueList.add(val);
                }
            }
        }
        int m = valueList.size();

        List<List<Integer>> varAdj = new ArrayList<>(n);
        for (Variable<T> v : vars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            varAdj.add(dom.stream().map(val -> valueIndex.get(val)).toList());
        }

        int[] matchVar = new int[n];
        int[] matchVal = new int[m];
        Arrays.fill(matchVar, -1);
        Arrays.fill(matchVal, -1);

        int matchingSize = 0;
        for (int i = 0; i < n; i++) {
            if (augment(i, varAdj, matchVar, matchVal, new boolean[m])) matchingSize++;
        }
        if (matchingSize < n) return Optional.empty();

        int freeNode = n + m;
        int totalNodes = freeNode + 1;
        List<List<Integer>> graph = new ArrayList<>(totalNodes);
        for (int i = 0; i < totalNodes; i++) graph.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int mv = matchVar[i];
            graph.get(n + mv).add(i);
            for (int vj : varAdj.get(i)) {
                if (vj != mv) graph.get(i).add(n + vj);
            }
        }
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

        int[] scc = tarjanSCC(graph, totalNodes);

        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int mv = matchVar[i];
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(vars.get(i));
            DiscreteDomain.Builder<T> builder = null;
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

    @Override
    @SuppressWarnings("unchecked")
    public PropagationResult propagateWithReasons(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains)
                .map(updated -> PropagationResult.feasible(updated, Map.of()))
                .orElseGet(() -> PropagationResult.infeasible(
                    (Map<Variable<?>, Object>) (Map<?, ?>) getVariables().stream()
                        .filter(domains::containsKey)
                        .collect(java.util.stream.Collectors.toMap(v -> v, domains::get))
                ));
    }

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
}
