package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A greedy cycle cutset (feedback vertex set) of a constraint graph: variables whose removal leaves
 * the rest acyclic. Every constraint joins each pair of variables in its scope; nogoods are ignored.
 * <p>
 * Variables with at most one remaining neighbour are stripped repeatedly, since none can lie on a
 * cycle; the remaining variable of highest degree then joins the cutset, ties going to the lower
 * index, and the two steps alternate until nothing remains. A final pass returns to the forest any
 * cutset variable, latest first, whose forest neighbours all lie in different trees.
 */
final class FeedbackVertexSet {

    /**
     * Adjacency entries {@link #within} builds at most, counting each scope of {@code k} variables
     * as {@code k * (k - 1)}.
     */
    static final long MAX_ADJACENCY = 5_000_000;

    private FeedbackVertexSet() {}

    /**
     * The greedy cutset of {@code constraints}' graph, if it has at most {@code maxSize} variables.
     * Empty without building the graph when a scope is too wide for any such cutset (a scope of
     * {@code k} variables leaves a cycle until {@code k - 2} of them are cut) or when the graph would
     * exceed {@link #MAX_ADJACENCY}.
     */
    static Optional<Set<Variable<?>>> within(@NonNull Set<Constraint> constraints, int maxSize) {
        long adjacency = 0;
        for (Constraint constraint : constraints) {
            if (constraint instanceof NogoodConstraint) continue;
            long k = constraint.getVariables().size();
            if (k - 2 > maxSize) return Optional.empty();
            adjacency += k * (k - 1);
        }
        if (adjacency > MAX_ADJACENCY) return Optional.empty();
        Set<Variable<?>> cutset = greedy(neighbours(constraints));
        return cutset.size() <= maxSize ? Optional.of(cutset) : Optional.empty();
    }

    private static Map<Variable<?>, Set<Variable<?>>> neighbours(Set<Constraint> constraints) {
        Map<Variable<?>, Set<Variable<?>>> neighbours = new HashMap<>();
        for (Constraint constraint : constraints) {
            if (constraint instanceof NogoodConstraint) continue;
            for (Variable<?> a : constraint.getVariables()) {
                Set<Variable<?>> adjacent = neighbours.computeIfAbsent(a, k -> new HashSet<>());
                for (Variable<?> b : constraint.getVariables()) {
                    if (a != b) adjacent.add(b);
                }
            }
        }
        return neighbours;
    }

    private static Set<Variable<?>> greedy(Map<Variable<?>, Set<Variable<?>>> neighbours) {
        Map<Variable<?>, Integer> degree = new HashMap<>();
        neighbours.forEach((v, adjacent) -> degree.put(v, adjacent.size()));
        Set<Variable<?>> alive = new HashSet<>(neighbours.keySet());
        Deque<Variable<?>> stripping = new ArrayDeque<>();
        for (Variable<?> v : alive) {
            if (degree.get(v) <= 1) stripping.add(v);
        }
        PriorityQueue<Ranked> hubs = new PriorityQueue<>(HUB_ORDER);
        for (Variable<?> v : alive) hubs.add(new Ranked(v, degree.get(v)));
        Set<Variable<?>> forest = new HashSet<>();
        List<Variable<?>> cut = new ArrayList<>();
        while (true) {
            while (!stripping.isEmpty()) {
                Variable<?> v = stripping.poll();
                if (alive.remove(v)) {
                    forest.add(v);
                    detach(v, neighbours, alive, degree, stripping, hubs);
                }
            }
            if (alive.isEmpty()) return returnRedundant(cut, forest, neighbours);
            // Every variable still alive has degree 2 or more and a current entry, so one is found.
            Ranked hub = hubs.poll();
            while (!alive.contains(hub.variable()) || degree.get(hub.variable()) != hub.degree()) {
                hub = hubs.poll();
            }
            alive.remove(hub.variable());
            cut.add(hub.variable());
            detach(hub.variable(), neighbours, alive, degree, stripping, hubs);
        }
    }

    /** A variable at the degree it had when queued; stale once its degree has dropped since. */
    private record Ranked(Variable<?> variable, int degree) {}

    /** Highest degree first, then lowest index. */
    private static final Comparator<Ranked> HUB_ORDER = Comparator.comparingInt(Ranked::degree).reversed()
            .thenComparingInt(r -> r.variable().getIndex());

    private static void detach(Variable<?> v, Map<Variable<?>, Set<Variable<?>>> neighbours, Set<Variable<?>> alive,
                               Map<Variable<?>, Integer> degree, Deque<Variable<?>> stripping, PriorityQueue<Ranked> hubs) {
        for (Variable<?> w : neighbours.get(v)) {
            if (alive.contains(w)) {
                int reduced = degree.merge(w, -1, Integer::sum);
                if (reduced <= 1) stripping.add(w);
                else hubs.add(new Ranked(w, reduced));
            }
        }
    }

    /** Moves back to {@code forest}, latest first, each cut variable whose forest neighbours lie in distinct trees. */
    private static Set<Variable<?>> returnRedundant(List<Variable<?>> cut, Set<Variable<?>> forest,
                                                    Map<Variable<?>, Set<Variable<?>>> neighbours) {
        Map<Variable<?>, Variable<?>> parent = new HashMap<>();
        for (Variable<?> v : forest) parent.put(v, v);
        for (Variable<?> v : forest) {
            for (Variable<?> w : neighbours.get(v)) {
                if (forest.contains(w)) union(parent, v, w);
            }
        }
        Set<Variable<?>> cutset = new LinkedHashSet<>(cut);
        for (int i = cut.size() - 1; i >= 0; i--) {
            Variable<?> v = cut.get(i);
            if (joinsDistinctTrees(v, forest, neighbours, parent)) {
                cutset.remove(v);
                forest.add(v);
                parent.put(v, v);
                for (Variable<?> w : neighbours.get(v)) {
                    if (forest.contains(w)) union(parent, v, w);
                }
            }
        }
        return cutset;
    }

    private static boolean joinsDistinctTrees(Variable<?> v, Set<Variable<?>> forest,
                                              Map<Variable<?>, Set<Variable<?>>> neighbours,
                                              Map<Variable<?>, Variable<?>> parent) {
        Set<Variable<?>> roots = new HashSet<>();
        for (Variable<?> w : neighbours.get(v)) {
            if (forest.contains(w) && !roots.add(find(parent, w))) return false;
        }
        return true;
    }

    private static Variable<?> find(Map<Variable<?>, Variable<?>> parent, Variable<?> v) {
        Variable<?> root = v;
        while (parent.get(root) != root) {
            Variable<?> grandparent = parent.get(parent.get(root));
            parent.put(root, grandparent);
            root = grandparent;
        }
        return root;
    }

    private static void union(Map<Variable<?>, Variable<?>> parent, Variable<?> a, Variable<?> b) {
        parent.put(find(parent, a), find(parent, b));
    }
}
