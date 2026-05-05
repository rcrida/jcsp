package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.binary.BinaryTuplesConstraint;
import io.github.rcrida.jcsp.domains.AssignmentDomain;
import io.github.rcrida.jcsp.domains.Domain;
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
        List<Set<Variable>> maximal = getMaximalCliqueBags(csp);

        List<CliqueEdge> edges = getCliqueEdges(maximal);

        Map<Integer, Set<Integer>> tree = getMinimumSpanningTree(maximal, edges);

        val treeBuilder = ConstraintSatisfactionProblem.builder();
        val cliqueVariables = new ArrayList<Variable>();
        for (val clique : maximal) {
            val cliqueVariableDomains = clique.stream()
                    .collect(Collectors.toMap(
                            v -> v,
                            v -> csp.getDomain(v).get()));
            val cliqueDomainMaxSize = cliqueVariableDomains.values().stream()
                    .map(Domain::size)
                    .map(BigInteger::valueOf)
                    .reduce(BigInteger.ONE, BigInteger::multiply);
            if (cliqueDomainMaxSize.compareTo(BigInteger.valueOf(maxDomainSize)) > 0) {
                log.info("Maximum clique domain size {} exceeded by {}", maxDomainSize, cliqueDomainMaxSize);
                return Optional.empty();
            }
            val cliqueDomain = new AssignmentDomain(cliqueVariableDomains, csp);
            val cliqueVariable = treeBuilder.createVariable(cliqueVariableDomains.keySet().toString(), cliqueDomain);
            treeBuilder.variableDomain(cliqueVariable, cliqueDomain);
            cliqueVariables.add(cliqueVariable);
        }
        val consistencyConstraints = new HashSet<AssignmentVariableConsistencyConstraint>();
        for (val treeEntry : tree.entrySet()) {
            val clique = maximal.get(treeEntry.getKey());
            for (val neighbourIndex : treeEntry.getValue()) {
                val neighbour = new HashSet<>(maximal.get(neighbourIndex));
                neighbour.retainAll(clique);
                neighbour.forEach(variable ->
                    consistencyConstraints.add(AssignmentVariableConsistencyConstraint.builder()
                            .left(cliqueVariables.get(treeEntry.getKey()))
                            .right(cliqueVariables.get(neighbourIndex))
                            .cliqueVariable(variable)
                            .build())
                );
            }
        }
        treeBuilder.constraints(consistencyConstraints);
        log.info("tree {}", treeBuilder.build());
        return Optional.of(treeBuilder.build());
    }

    private @NonNull List<Set<Variable>> getMaximalCliqueBags(@NonNull ConstraintSatisfactionProblem csp) {
        var workGraph = csp.toBuilder().build();
        val bags = new ArrayList<Set<Variable>>();
        while (workGraph.getNumVariables() > 0) {
            // build bag = pick U neighbours (uneliminated)
            val pick = selectVertex(workGraph);
            val N = workGraph.getNeighbours().get(pick); // TODO add csp.getNeightbours(v)
            val bag = new HashSet<>(N);
            bag.add(pick);
            bags.add(bag);

            // add fill edges: make neighbors a clique
            val workGraphBuilder = workGraph.toBuilder();
            val listN = new ArrayList<>(N);
            for (int i = 0; i < listN.size(); i++) {
                for (int j = i + 1; j < listN.size(); j++) {
                    workGraphBuilder.constraint(BinaryTuplesConstraint.builder()
                            .left(listN.get(i))
                            .right(listN.get(j))
                            .build());
                }
            }

            // remove vertex pick
            workGraphBuilder.deleteVariable(pick);

            workGraph = workGraphBuilder.build();
        }
        log.info("Bags = {}", bags);

        // Extract unique inclusion-maximal cliques from recorded bags
        Set<Set<Variable>> cliques = new HashSet<>(bags);
        log.info("cliques = {}", cliques);

        // remove those that are subset of another
        List<Set<Variable>> maximal = new ArrayList<>(cliques);
        maximal.removeIf(c -> {
            for (Set<Variable> d : cliques) {
                if (d != c && d.containsAll(c)) {
                    return true;
                }
            }
            return false;
        });
        log.info("Maximal = {}", maximal);
        return maximal;
    }

    private Variable selectVertex(@NonNull ConstraintSatisfactionProblem workGraph) {
        val variableHeuristic = variableHeuristicFactory.create(workGraph);
        return workGraph.getVariableDomains().keySet().stream()
                .min(variableHeuristic)
                .orElseThrow();
    }

    private static @NonNull List<CliqueEdge> getCliqueEdges(List<Set<Variable>> maximal) {
        // Build clique graph edges weighted by intersection size
        int m = maximal.size();
        List<CliqueEdge> edges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                Set<Variable> a = maximal.get(i);
                Set<Variable> b = maximal.get(j);
                int inter = 0;
                for (Variable x : a) {
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

    private static @NonNull Map<Integer, Set<Integer>> getMinimumSpanningTree(List<Set<Variable>> maximal, List<CliqueEdge> edges) {
        // Kruskal minimum spanning tree
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
        log.info("Clique tree = {}", tree);
        return tree;
    }
}
