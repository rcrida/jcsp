package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.consistency.arc.Arc;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.val;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable structural representation of a constraint graph. Captures the constraint set and all derived
 * graph properties (neighbours, binary constraints, cycle/connectivity analysis) computed once at construction.
 * Shared across all {@link ConstraintSatisfactionProblem} instances that result from domain-only modifications,
 * avoiding redundant recomputation during solving.
 */
@Value
@ToString(exclude = {"neighbours", "allBinaryConstraints", "allBinaryArcConstraints", "auxiliaryCache", "declaredDomains"})
class ConstraintGraph {
    Set<Constraint> constraints;
    boolean isCyclic;
    boolean isFullyConnected;
    /**
     * A map containing each variable in the problem that has at least one neighbour, along with its neighbours.
     */
    @EqualsAndHashCode.Exclude Map<Variable<?>, Set<Variable<?>>> neighbours;
    /**
     * A set of all binary constraints applicable to this problem. Where possible casts n-ary constrains
     * as additional binary constraints. Ignores n-ary constraints that aren't decomposable.
     */
    @EqualsAndHashCode.Exclude Set<BinaryConstraint<?, ?>> allBinaryConstraints;
    /**
     * A map containing all binary constraints associated with each arc.
     */
    @EqualsAndHashCode.Exclude Map<Arc, List<BinaryConstraint<?, ?>>> allBinaryArcConstraints;
    /**
     * Generic per-structure memoization slot for any propagation algorithm (e.g. {@link io.github.rcrida.jcsp.consistency.arc.AC3}'s own
     * arc/constraint index) that wants to cache a derived value keyed only by this graph's
     * structure, without the risk a single shared cache on the algorithm's own singleton instance
     * carries: two different {@link ConstraintSatisfactionProblem}s solved concurrently (e.g.
     * independent subproblems) would otherwise keep evicting each other's entry. Keying by this
     * graph instance directly — reused across every domain-only narrowing step of a search, since
     * {@link ConstraintSatisfactionProblem}'s constructor only rebuilds it when the structural
     * constraint set actually changes — gives each distinct constraint structure its own isolated
     * cache with no cross-contamination, discarded automatically once the graph itself becomes
     * unreachable, and no {@code hashCode}/{@code equals} cost on the (potentially large,
     * expensive-to-hash) constraint set itself: the key is whatever opaque token the caller
     * supplies (e.g. a {@link Class} literal identifying the cached value's shape), not the
     * constraint set.
     */
    @EqualsAndHashCode.Exclude
    Map<Object, Object> auxiliaryCache = new ConcurrentHashMap<>();
    /**
     * The variable domains this graph was originally built with -- captured once, only when a
     * fresh {@link ConstraintGraph} is actually constructed (never on reuse via {@link
     * ConstraintSatisfactionProblem#toBuilder()}/{@code withDomain}/{@code withDomains}, which pass
     * an existing graph straight through instead of building a new one). Since every domain-only
     * update from that point on narrows an existing domain and never re-adds a value (see {@link
     * ConstraintSatisfactionProblem}'s constructor Javadoc), this is a safe closed superset of every
     * value any of this graph's variables will ever hold for the rest of that graph's lineage --
     * unlike "whichever domains happen to be live the first time some propagator's own cache gets
     * built against this graph", which is not reliably the widest state (e.g. {@link
     * io.github.rcrida.jcsp.consistency.arc.MAC#apply} narrows its target variable to a singleton
     * <em>before</em> ever touching this graph, so a propagator whose own cache is first populated
     * from inside a bare {@code MAC}/{@code DomWdegLubySearch} call -- bypassing {@code
     * PropagationFixpointSolver}'s own whole-problem preprocessing pass -- would otherwise only ever
     * see whatever single value got assigned first). Added for {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3BitRm}'s own per-variable value-index cache, which
     * needs exactly this guarantee; exposed publicly via {@link
     * ConstraintSatisfactionProblem#getDeclaredDomains()} since this class itself is package-private.
     */
    @EqualsAndHashCode.Exclude
    Map<Variable<?>, Domain<?>> declaredDomains;

    @SuppressWarnings("unchecked")
    <T> T computeAuxiliaryCacheIfAbsent(Object key, Function<ConstraintGraph, T> compute) {
        return (T) auxiliaryCache.computeIfAbsent(key, k -> compute.apply(this));
    }

    /**
     * Neighbours totalling no more than this many entries across the whole graph are all built
     * eagerly. A constraint of arity {@code k} contributes {@code k²} entries, so the budget is what
     * a very wide constraint runs past; see {@link #computeNeighbours}.
     */
    static final long EAGER_NEIGHBOUR_BUDGET = 4_000_000;
    /** Once over {@link #EAGER_NEIGHBOUR_BUDGET}, a variable with at most this many neighbours still gets a built set. */
    static final int LAZY_NEIGHBOUR_THRESHOLD = 1_024;

    ConstraintGraph(@NonNull Set<Constraint> constraints, @NonNull Map<Variable<?>, Domain<?>> variableDomains) {
        this(constraints, variableDomains, EAGER_NEIGHBOUR_BUDGET, LAZY_NEIGHBOUR_THRESHOLD);
    }

    ConstraintGraph(@NonNull Set<Constraint> constraints, @NonNull Map<Variable<?>, Domain<?>> variableDomains,
                    long eagerNeighbourBudget, int lazyNeighbourThreshold) {
        this.constraints = constraints;
        this.declaredDomains = variableDomains;
        Set<Variable<?>> variables = variableDomains.keySet();
        this.neighbours = computeNeighbours(constraints, variables, eagerNeighbourBudget, lazyNeighbourThreshold);
        this.allBinaryConstraints = computeAllBinaryConstraints(constraints);
        this.allBinaryArcConstraints = computeAllBinaryArcConstraints(this.allBinaryConstraints);
        val visited = new HashSet<Variable<?>>();
        if (this.neighbours.isEmpty()) {
            this.isCyclic = false;
            this.isFullyConnected = false;
        } else {
            val startingVariable = this.neighbours.keySet().iterator().next();
            this.isCyclic = isCyclicFrom(startingVariable, this.neighbours, visited);
            this.isFullyConnected = visited.size() == this.neighbours.size();
        }
    }

    /**
     * @return true if the problem graph is a tree
     */
    boolean isTree() {
        return !isCyclic && isFullyConnected;
    }

    Set<Arc> getAllBinaryArcs() {
        return allBinaryArcConstraints.keySet();
    }

    /**
     * Whether the component containing {@code start} has a cycle, found by a depth-first search that
     * records every variable it reaches in {@code visited}. Iterative, with the path on an explicit
     * stack, because a path-shaped graph is as deep as it is long and a recursive search overflowed
     * the thread stack between 30,000 and 100,000 variables. It visits neighbours in the same order
     * and stops at the same point as the recursion did, so {@code visited} -- which {@link
     * #isFullyConnected} is read from -- comes out identical, including when a cycle ends the search
     * early.
     */
    private static boolean isCyclicFrom(Variable<?> start, Map<Variable<?>, Set<Variable<?>>> neighbours, Set<Variable<?>> visited) {
        Deque<PathStep> path = new ArrayDeque<>();
        visited.add(start);
        path.push(new PathStep(start, null, neighbours.get(start).iterator()));
        while (!path.isEmpty()) {
            PathStep step = path.peek();
            if (!step.untried().hasNext()) {
                path.pop();
                continue;
            }
            Variable<?> neighbour = step.untried().next();
            if (!visited.contains(neighbour)) {
                visited.add(neighbour);
                path.push(new PathStep(neighbour, step.node(), neighbours.get(neighbour).iterator()));
            } else if (neighbour != step.parent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * One variable on the path of {@link #isCyclicFrom}'s search: the variable, the one it was
     * reached from, and its neighbours not yet tried.
     */
    private record PathStep(Variable<?> node, Variable<?> parent, Iterator<Variable<?>> untried) {
    }

    /**
     * Both the per-variable neighbour sets and the outer map are wrapped via {@link
     * Collections#unmodifiableSet}/{@link Collections#unmodifiableMap} rather than {@code
     * Set.copyOf}/{@code Map.copyOf} for the same reason {@link #computeAllBinaryArcConstraints}'s
     * own Javadoc documents: those {@code copyOf} factories build one of the JDK's small immutable
     * collections, salted with a per-JVM-process random value that reshuffles hash-bucket-derived
     * iteration order launch-to-launch for no reason relevant here ({@link Variable}'s own {@code
     * hashCode()} is already fully deterministic). {@code startingVariable = neighbours.keySet()
     * .iterator().next()} (used by cycle detection) and any other neighbour-set iteration depend
     * on this map's/these sets' iteration order, so removing the salt here closes the same class of
     * launch-to-launch search noise as that fix, for the {@code neighbours} structure specifically.
     * <p>
     * A constraint of arity {@code k} makes each of its variables the neighbour of the other
     * {@code k - 1}, so building every set costs {@code k²} entries -- over a gigabyte at arity 4,000.
     * While the whole graph needs no more than {@code eagerBudget} entries every set is built here,
     * exactly as always. Past it, a variable with more than {@code lazyThreshold} neighbours gets a
     * {@link LazyNeighbours} instead, which builds the identical set when it is read and keeps nothing.
     */
    private static Map<Variable<?>, Set<Variable<?>>> computeNeighbours(Set<Constraint> constraints, Set<Variable<?>> variables,
                                                                       long eagerBudget, int lazyThreshold) {
        val scopesByVariable = new HashMap<Variable<?>, List<Set<Variable<?>>>>();
        for (Variable<?> variable : variables) {
            scopesByVariable.put(variable, new ArrayList<>());
        }
        for (Constraint constraint : constraints) {
            val scope = constraint.getVariables();
            for (Variable<?> variable : scope) {
                scopesByVariable.get(variable).add(scope);
            }
        }
        long totalNeighbours = 0;
        for (List<Set<Variable<?>>> scopes : scopesByVariable.values()) {
            totalNeighbours += LazyNeighbours.upperBound(scopes);
        }
        boolean allEager = totalNeighbours <= eagerBudget;
        val result = new HashMap<Variable<?>, Set<Variable<?>>>();
        for (Map.Entry<Variable<?>, List<Set<Variable<?>>>> entry : scopesByVariable.entrySet()) {
            Variable<?> variable = entry.getKey();
            List<Set<Variable<?>>> scopes = entry.getValue();
            result.put(variable, allEager || LazyNeighbours.upperBound(scopes) <= lazyThreshold
                    ? Collections.unmodifiableSet(LazyNeighbours.build(variable, scopes))
                    : new LazyNeighbours(variable, scopes));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * The neighbours of one variable -- every variable that shares a constraint with it -- built
     * from the scopes of the constraints it is in rather than held. Read-only.
     * <p>
     * Every read builds the set afresh with exactly the operations {@link #computeNeighbours} uses
     * for an eager one (add each scope in constraint order, then remove the variable itself), so
     * iteration order is the same as the {@link HashSet} it stands in for, and nothing downstream
     * that depends on that order can tell the two apart. {@link #size} is remembered; the rest are
     * answered from the scopes where that is cheaper than building.
     */
    private static final class LazyNeighbours extends AbstractSet<Variable<?>> {
        private final Variable<?> self;
        private final List<Set<Variable<?>>> scopes;
        private int size = -1;

        LazyNeighbours(Variable<?> self, List<Set<Variable<?>>> scopes) {
            this.self = self;
            this.scopes = scopes;
        }

        /** An upper bound on the neighbours: the scopes' sizes, counting a repeat and the variable itself. */
        static long upperBound(List<Set<Variable<?>>> scopes) {
            long bound = 0;
            for (Set<Variable<?>> scope : scopes) {
                bound += scope.size();
            }
            return bound;
        }

        static Set<Variable<?>> build(Variable<?> self, List<Set<Variable<?>>> scopes) {
            Set<Variable<?>> neighbours = new HashSet<>();
            for (Set<Variable<?>> scope : scopes) {
                neighbours.addAll(scope);
            }
            neighbours.remove(self);
            return neighbours;
        }

        @Override
        public Iterator<Variable<?>> iterator() {
            return Collections.unmodifiableSet(build(self, scopes)).iterator();
        }

        @Override
        public int size() {
            if (size < 0) {
                size = build(self, scopes).size();
            }
            return size;
        }

        @Override
        public boolean contains(Object candidate) {
            if (self.equals(candidate)) {
                return false;
            }
            for (Set<Variable<?>> scope : scopes) {
                if (scope.contains(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Collected via {@code Collectors.toSet()} (a plain, unsalted {@link HashSet}) and wrapped in
     * {@link Collections#unmodifiableSet} rather than {@code Collectors.toUnmodifiableSet()} for the
     * same reason {@link #computeAllBinaryArcConstraints}'s own Javadoc documents: the latter builds
     * one of the JDK's small immutable collections, salted per JVM process. This set's own iteration
     * order feeds {@link #computeAllBinaryArcConstraints}'s encounter order, which in turn determines
     * the relative order of multiple {@link BinaryConstraint}s sharing the same {@link Arc} in that
     * method's own per-arc constraint lists.
     */
    private static Set<BinaryConstraint<?, ?>> computeAllBinaryConstraints(Set<Constraint> constraints) {
        val binaryConstraints = constraints.stream()
                .filter(c -> c instanceof BinaryConstraint)
                .map(c -> (BinaryConstraint<?, ?>) c)
                .toList();
        val inferredBinaryConstraints = constraints.stream()
                .filter(c -> c instanceof BinaryDecomposable)
                .map(c -> (BinaryDecomposable) c)
                .flatMap(c -> c.getAsBinaryConstraints().stream())
                .toList();
        return Collections.unmodifiableSet(Stream.concat(binaryConstraints.stream(), inferredBinaryConstraints.stream())
                .collect(Collectors.toSet()));
    }

    /**
     * {@code Collectors.groupingBy}'s own map (a plain {@link HashMap}) is wrapped via {@link
     * Collections#unmodifiableMap} rather than {@code Map.copyOf} deliberately: {@code Map.copyOf}
     * builds one of the JDK's small immutable collections ({@code java.util.ImmutableCollections}),
     * which since JDK 9 randomizes its internal hash-bucket layout with a salt derived from {@code
     * System.nanoTime()} at JVM startup -- fixed for one running process, different every launch.
     * {@link Arc}'s own {@code hashCode()} is fully deterministic (it composes {@link
     * Variable}'s, which for {@code Variable.Impl} is just {@code String.hashCode()}, spec-guaranteed
     * stable), so this map's own key ordering would otherwise be deterministic too -- {@code
     * Map.copyOf}'s salt was the only reason {@link
     * io.github.rcrida.jcsp.consistency.arc.AC3#apply}'s initial arc-processing queue (built directly
     * from this map's {@code keySet()}) varied launch-to-launch despite producing the same
     * arc-consistent fixpoint either way (AC3 is confluent, so this was never a correctness issue,
     * only a source of node-count/timing noise across separate {@code java} processes -- the same
     * noise {@code RestartRandomization} was added to reduce for {@code DomWdegLubySearch}'s own,
     * unrelated tie-break randomness, and doesn't touch this JDK-internal source at all).
     * {@code Collections.unmodifiableMap} is a plain non-copying wrapper with no such salting.
     */
    private static Map<Arc, List<BinaryConstraint<?, ?>>> computeAllBinaryArcConstraints(Set<BinaryConstraint<?, ?>> allBinaryConstraints) {
        Map<Arc, List<BinaryConstraint<?, ?>>> grouped = allBinaryConstraints.stream()
                .flatMap(binaryConstraint -> getArcStream(binaryConstraint)
                        .map(arc -> new AbstractMap.SimpleEntry<>(arc, binaryConstraint)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toUnmodifiableList())));
        return Collections.unmodifiableMap(grouped);
    }

    private static @NonNull Stream<Arc> getArcStream(BinaryConstraint<?, ?> bc) {
        return Stream.of(Arc.of(bc.getLeft(), bc.getRight()), Arc.of(bc.getRight(), bc.getLeft()));
    }
}
