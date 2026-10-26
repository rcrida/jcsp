package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.AssignmentDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.VariableSelectionHeuristic;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Value
public class TreeDecomposerImpl implements TreeDecomposer {
    VariableSelectionHeuristic.Factory variableHeuristicFactory;
    /**
     * Checked once per eliminated vertex, and while enumerating each clique's joint domain -- see
     * {@link AssignmentDomain#of} for why a bounded enumeration still needs interrupting. Construction-time
     * rather than a {@link #decompose} parameter, the same way every solver in the chain takes its
     * {@link Cancellation}.
     */
    @NonNull Cancellation cancellation;

    // Edge for clique graph with weight = intersection size
    record CliqueEdge(int a, int b, int w) implements Comparable<CliqueEdge> {
        public int compareTo(CliqueEdge o) { return Integer.compare(o.w, this.w); } // descending
    }

    // Union-Find for Kruskal
    static class UnionFind {
        int[] p;

        public UnionFind(int n) {
            p = new int[n];
            for (int i = 0; i < n; i++) {
                p[i] = -1;
            }
        }

        int find(int x) {
            return p[x] < 0 ? x : (p[x] = find(p[x]));
        }

        boolean union(int a, int b) {
            a = find(a);
            b = find(b);
            if (a == b) return false;
            if (p[a] > p[b]) {
                int t = a;
                a = b;
                b = t;
            }
            p[a] += p[b];
            p[b] = a;
            return true;
        }
    }

    @Override
    public Optional<ConstraintSatisfactionProblem> decompose(@NonNull ConstraintSatisfactionProblem csp, int maxDomainSize) {
        if (csp.isEmpty() || csp.isTree()) {
            return Optional.empty();
        }
        val maximalBags = getMaximalCliqueBags(csp, maxDomainSize);
        if (maximalBags.isEmpty()) {
            return Optional.empty();
        }
        List<Set<Variable<?>>> maximal = maximalBags.get();

        List<CliqueEdge> edges = getCliqueEdges(maximal);

        Map<Integer, Set<Integer>> tree = getMaximumSpanningTree(maximal, edges);

        // Every clique's domains first, and only then the enumeration. Interleaving the two --
        // size-check clique i, enumerate clique i, size-check clique i+1 -- means a clique that busts
        // the cap discards every enumeration already done for the cliques before it.
        // QuadraticAssignment-bur26a spent 74s enumerating and then returned empty on a later clique.
        // The size check itself now happens while the bags are built, so no clique reaching here can
        // exceed maxDomainSize.
        val cliqueDomainsByClique = new ArrayList<Map<Variable<?>, Domain<?>>>(maximal.size());
        for (val clique : maximal) {
            val cliqueVariableDomains = new java.util.HashMap<Variable<?>, Domain<?>>();
            clique.forEach(v -> cliqueVariableDomains.put(v, csp.getDomain(v)));
            cliqueDomainsByClique.add(cliqueVariableDomains);
        }

        val treeBuilder = ConstraintSatisfactionProblem.builder();
        val cliqueVariables = new ArrayList<Variable<Assignment>>();
        for (val cliqueVariableDomains : cliqueDomainsByClique) {
            val cliqueDomain = AssignmentDomain.of(cliqueVariableDomains, csp, cancellation);
            if (cliqueDomain.isEmpty()) {
                log.debug("Abandoning tree decomposition: cancelled while enumerating a clique domain");
                return Optional.empty();
            }
            val cliqueVariable = treeBuilder.<Assignment>createVariable(cliqueVariableDomains.keySet().toString(), cliqueDomain.get());
            cliqueVariables.add(cliqueVariable);
        }
        val consistencyConstraints = new HashSet<AssignmentVariableConsistencyConstraint>();
        for (val treeEntry : tree.entrySet()) {
            val clique = maximal.get(treeEntry.getKey());
            for (val neighbourIndex : treeEntry.getValue()) {
                val shared = new HashSet<>(maximal.get(neighbourIndex));
                shared.retainAll(clique);
                // One constraint for the whole separator -- see its Javadoc for why one per shared
                // variable is not equivalent under arc consistency.
                consistencyConstraints.add(AssignmentVariableConsistencyConstraint.builder()
                        .left(cliqueVariables.get(treeEntry.getKey()))
                        .right(cliqueVariables.get(neighbourIndex))
                        .cliqueVariables(shared)
                        .build());
            }
        }
        treeBuilder.constraints(consistencyConstraints);
        log.debug("tree {}", treeBuilder.build());
        return Optional.of(treeBuilder.build());
    }

    /**
     * Computes tree-decomposition bags via min-degree elimination, maintaining the working
     * adjacency graph as a plain {@code Map<Variable<?>, Set<Variable<?>>>} rather than a
     * {@link ConstraintSatisfactionProblem}. This loop only ever needs adjacency (which variables
     * are structurally connected) — it never inspects constraint semantics or domain values — so
     * building a fully validated CSP on every elimination step was both wasted work and, worse,
     * unsound: each step's "fill-in" edges (recording that a pivot's remaining neighbours must now
     * be treated as mutually adjacent) used to be represented as a real {@link io.github.rcrida.jcsp.constraints.binary.BinaryTuplesConstraint}
     * pushed through {@link ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder#build()},
     * which re-runs the {@link io.github.rcrida.jcsp.domains.BoundedDomain} compatibility check ({@code validateConstraints}) on
     * every new constraint added. That check is meant for constraints that are actually evaluated
     * during solving; a fill-in edge never is; it exists purely to make the next iteration's
     * {@code getNeighbours} report the right adjacency. Since {@link io.github.rcrida.jcsp.constraints.binary.BinaryTuplesConstraint} was
     * never (and shouldn't be) whitelisted for {@link io.github.rcrida.jcsp.domains.BoundedDomain} — extensional tables over
     * continuous values don't make sense — any tree decomposition attempt over 3+ mutually
     * connected {@link io.github.rcrida.jcsp.domains.BoundedDomain} variables not already resolved by propagation would throw
     * {@link IllegalArgumentException}, regardless of which real constraint originally connected
     * them (2026-07-15).
     */
    private @NonNull Optional<List<Set<Variable<?>>>> getMaximalCliqueBags(
            @NonNull ConstraintSatisfactionProblem csp, int maxDomainSize) {
        val neighbours = new HashMap<Variable<?>, Set<Variable<?>>>();
        csp.getNeighbours().forEach((v, ns) -> neighbours.put(v, new HashSet<>(ns)));
        val maxJointSize = BigInteger.valueOf(maxDomainSize);

        val bags = new ArrayList<Set<Variable<?>>>();
        while (!neighbours.isEmpty()) {
            // Elimination is where a problem too wide to decompose spends its time: each step's
            // fill-in is quadratic in the bag, and the maximal-clique filter and clique graph after
            // it are quadratic in the bag count. Checked per vertex, it is the one place that can
            // stop that work early.
            if (cancellation.isCancelled()) {
                log.debug("Abandoning tree decomposition: cancelled during elimination");
                return Optional.empty();
            }
            // build bag = pick U neighbours (uneliminated)
            val pick = selectVertex(neighbours);
            val N = neighbours.get(pick);
            val bag = new HashSet<>(N);
            bag.add(pick);
            // Every maximal clique is one of these bags, and a bag only ever grows into the clique
            // containing it, so a bag over the cap means the decomposition is going to be declined.
            // Saying so now, before the fill-in below and everything after it, is what keeps a wide
            // problem from building a decomposition only to discard it.
            val bagJointSize = bag.stream()
                    .map(v -> BigInteger.valueOf(csp.getDomain(v).size()))
                    .reduce(BigInteger.ONE, BigInteger::multiply);
            if (bagJointSize.compareTo(maxJointSize) > 0) {
                log.debug("Maximum clique domain size {} exceeded by {}", maxDomainSize, bagJointSize);
                return Optional.empty();
            }
            bags.add(bag);

            // add fill edges: make neighbors a clique
            val listN = new ArrayList<>(N);
            for (int i = 0; i < listN.size(); i++) {
                for (int j = i + 1; j < listN.size(); j++) {
                    neighbours.get(listN.get(i)).add(listN.get(j));
                    neighbours.get(listN.get(j)).add(listN.get(i));
                }
            }

            // remove vertex pick
            neighbours.remove(pick);
            for (Variable<?> n : N) {
                neighbours.get(n).remove(pick);
            }
        }
        log.debug("Bags = {}", bags);

        // Extract unique inclusion-maximal cliques from recorded bags
        Set<Set<Variable<?>>> cliques = new HashSet<>(bags);
        log.debug("cliques = {}", cliques);

        // remove those that are subset of another
        List<Set<Variable<?>>> maximal = new ArrayList<>(cliques);
        maximal.removeIf(c -> {
            for (Set<Variable<?>> d : cliques) {
                if (d != c && d.containsAll(c)) {
                    return true;
                }
            }
            return false;
        });
        log.debug("Maximal = {}", maximal);
        return Optional.of(maximal);
    }

    private Variable<?> selectVertex(@NonNull Map<Variable<?>, Set<Variable<?>>> neighbours) {
        val variableHeuristic = variableHeuristicFactory.create(neighbours);
        return neighbours.keySet().stream()
                .min(variableHeuristic)
                .orElseThrow();
    }

    private static @NonNull List<CliqueEdge> getCliqueEdges(List<Set<Variable<?>>> maximal) {
        // Build clique graph edges weighted by intersection size
        int m = maximal.size();
        List<CliqueEdge> edges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                Set<Variable<?>> a = maximal.get(i);
                Set<Variable<?>> b = maximal.get(j);
                int inter = 0;
                for (Variable<?> x : a) {
                    if (b.contains(x)) {
                        inter++;
                    }
                }
                if (inter > 0) {
                    edges.add(new CliqueEdge(i, j, inter));
                }
            }
        }
        Collections.sort(edges);
        return edges;
    }

    private static @NonNull Map<Integer, Set<Integer>> getMaximumSpanningTree(List<Set<Variable<?>>> maximal, List<CliqueEdge> edges) {
        // Kruskal maximum spanning tree — edges sorted descending, maximises separator sizes
        int m = maximal.size();
        UnionFind uf = new UnionFind(m);
        Map<Integer, Set<Integer>> tree = new HashMap<>();
        for (int i = 0; i < m; i++) {
            tree.put(i, new LinkedHashSet<>());
        }
        for (CliqueEdge e : edges) {
            if (uf.union(e.a, e.b)) {
                tree.get(e.a).add(e.b);
                tree.get(e.b).add(e.a);
            }
        }
        log.debug("Clique tree = {}", tree);
        return tree;
    }
}
