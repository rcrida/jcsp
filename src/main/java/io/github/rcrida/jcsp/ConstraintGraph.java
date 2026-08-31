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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    ConstraintGraph(@NonNull Set<Constraint> constraints, @NonNull Map<Variable<?>, Domain<?>> variableDomains) {
        this.constraints = constraints;
        this.declaredDomains = variableDomains;
        Set<Variable<?>> variables = variableDomains.keySet();
        this.neighbours = computeNeighbours(constraints, variables);
        this.allBinaryConstraints = computeAllBinaryConstraints(constraints);
        this.allBinaryArcConstraints = computeAllBinaryArcConstraints(this.allBinaryConstraints);
        val visited = new HashSet<Variable<?>>();
        if (this.neighbours.isEmpty()) {
            this.isCyclic = false;
            this.isFullyConnected = false;
        } else {
            val startingVariable = this.neighbours.keySet().iterator().next();
            this.isCyclic = isCyclicFrom(startingVariable, null, this.neighbours, visited);
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

    private static boolean isCyclicFrom(Variable<?> src, Variable<?> parent, Map<Variable<?>, Set<Variable<?>>> neighbours, Set<Variable<?>> visited) {
        visited.add(src);
        for (Variable<?> neighbour : neighbours.get(src)) {
            if (!visited.contains(neighbour)) {
                if (isCyclicFrom(neighbour, src, neighbours, visited)) {
                    return true;
                }
            } else if (neighbour != parent) {
                return true;
            }
        }
        return false;
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
     */
    private static Map<Variable<?>, Set<Variable<?>>> computeNeighbours(Set<Constraint> constraints, Set<Variable<?>> variables) {
        val neighbours = new HashMap<Variable<?>, Set<Variable<?>>>();
        for (Variable<?> variable : variables) {
            neighbours.put(variable, new HashSet<>());
        }
        for (Constraint constraint : constraints) {
            val constraintVariables = constraint.getVariables();
            for (Variable<?> variable : constraintVariables) {
                neighbours.get(variable).addAll(constraintVariables);
            }
        }
        val result = new HashMap<Variable<?>, Set<Variable<?>>>();
        for (Map.Entry<Variable<?>, Set<Variable<?>>> entry : neighbours.entrySet()) {
            entry.getValue().remove(entry.getKey());
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
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
