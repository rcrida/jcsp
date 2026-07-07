package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.IntStream;

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
     * The bipartite variable/value graph and maximum matching, shared by {@link #propagate} and
     * {@link #explainInfeasible} so the matching computation lives in exactly one place.
     */
    private record MatchingResult(List<Variable<?>> vars, List<Object> valueList,
                                   List<List<Integer>> varAdj, int[] matchVar, int[] matchVal,
                                   int matchingSize) {}

    @SuppressWarnings("unchecked")
    private MatchingResult computeMatching(Map<Variable<?>, Domain<?>> domains) {
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

        return new MatchingResult((List<Variable<?>>) (List<?>) vars, valueList, varAdj, matchVar, matchVal, matchingSize);
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
        MatchingResult matching = computeMatching(domains);
        List<Variable<?>> vars = matching.vars();
        List<Object> valueList = matching.valueList();
        List<List<Integer>> varAdj = matching.varAdj();
        int[] matchVar = matching.matchVar();
        int[] matchVal = matching.matchVal();
        int n = vars.size();
        int m = valueList.size();

        if (matching.matchingSize() < n) return Optional.empty();

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
                    builder.delete((T) valueList.get(vj));
                }
            }
            if (builder != null) {
                updates.put(vars.get(i), builder.build());
            }
        }
        return Optional.of(updates);
    }

    /**
     * Finds the Hall-violating variable subset via the standard alternating-reachability
     * construction (the constructive proof of König/Hall's theorem): starting from a variable left
     * unmatched by the maximum matching, {@code z} is every variable reachable by alternating
     * paths. This set is guaranteed to have {@code |N(z)| < |z|} — a genuine Hall violation — since
     * `exposed` has no augmenting path in a maximum matching.
     * <p>
     * Deliberately no {@code matchedVar != -1} or {@code z.add(...)}-truthiness guard: both are
     * mathematically impossible to fail here. A free value reachable from {@code exposed} would
     * itself complete an augmenting path from {@code exposed}, contradicting the matching's
     * maximality; and since the matching is injective, each variable is matched to exactly one
     * value, so it can only ever be proposed for {@code z}-membership once. Adding checks for
     * cases that provably can't happen would only create untestable dead branches under this
     * project's 100%-branch-coverage requirement.
     */
    private Set<Integer> hallViolatingVars(int exposed, List<List<Integer>> varAdj, int[] matchVal) {
        Set<Integer> z = new HashSet<>();
        Set<Integer> visitedValues = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        z.add(exposed);
        queue.add(exposed);
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (int vj : varAdj.get(i)) {
                if (visitedValues.add(vj)) {
                    int matchedVar = matchVal[vj];
                    z.add(matchedVar);
                    queue.add(matchedVar);
                }
            }
        }
        return z;
    }

    /**
     * Attributes a Hall-set violation to the variables of the Hall-violating subset {@code z}
     * found by {@link #hallViolatingVars}, via {@link Propagatable#allSingletonReason} — sound
     * only when every variable in {@code z} is currently singleton.
     * <p>
     * This is a narrower proof than {@code propagate}'s general Régin GAC: by pigeonhole, if every
     * variable in a Hall-violating set of size {@code k} is singleton, at least two of them
     * necessarily share the same value (they occupy at most {@code k-1} distinct values), so a
     * non-empty reason from this method always reduces to a simple pairwise collision. The
     * general, non-singleton Hall violation — e.g. three variables each with domain {@code {1,2}},
     * the actual reason Régin's algorithm exists instead of plain pairwise decomposition — isn't
     * reducible to a small set of variable-value pairs, and still falls back to {@code Map.of()}
     * here, same as the default.
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        MatchingResult matching = computeMatching(domains);
        int n = matching.vars().size();
        if (matching.matchingSize() >= n) return Map.of();

        // matchingSize < n guarantees some variable is unmatched.
        int exposed = IntStream.range(0, n).filter(i -> matching.matchVar()[i] == -1).findFirst().orElseThrow();
        Set<Integer> z = hallViolatingVars(exposed, matching.varAdj(), matching.matchVal());
        List<Variable<?>> zVars = z.stream().map(matching.vars()::get).toList();
        return Propagatable.allSingletonReason(zVars, domains);
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
