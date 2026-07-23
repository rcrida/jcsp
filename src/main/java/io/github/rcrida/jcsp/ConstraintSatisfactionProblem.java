package io.github.rcrida.jcsp;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryLogicConstraint;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryElementConstraint;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.DivisionConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.SubsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DisjointConstraint;
import io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.Automaton;
import io.github.rcrida.jcsp.constraints.nary.BinPackingConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.DiffnConstraint;
import io.github.rcrida.jcsp.constraints.nary.RegularConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinConstraint;
import io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.NValueConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a constraint satisfaction problem (CSP), which consists of a set of variables,
 * each associated with a domain of values, and a set of constraints that must be satisfied.
 * This class provides methods for constructing and analyzing the problem.
 * It supports both binary and n-ary constraints and allows for the generation
 * of equivalent binary constraints from n-ary ones.
 */
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.NONE)
public class ConstraintSatisfactionProblem {
    /**
     * Constraint types that support {@link io.github.rcrida.jcsp.domains.BoundedDomain} (e.g.
     * {@link io.github.rcrida.jcsp.domains.IntervalDomain}) variables. Any other constraint type
     * referencing such a variable is rejected at build time.
     * <p>
     * Most entries here propagate via interval-arithmetic bounds narrowing. Several are the
     * exception, relying solely on the final {@code isSatisfiedBy} check every solver path already
     * runs before returning a solution (e.g. {@link io.github.rcrida.jcsp.solver.SolverDecorator#forcedSolution})
     * — the same fallback guarantee every other listed constraint relies on beyond its one upfront
     * propagation pass in {@link io.github.rcrida.jcsp.solver.BisectionConditioningSolver} (whose own
     * re-propagation is itself limited to {@code SumBoundConstraint}/{@code LinearBoundConstraint} — see its
     * {@code REPROPAGATORS}):
     * <ul>
     *   <li>{@link io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint} — {@code NodeConsistency}
     *       already gates on {@code instanceof DiscreteDomain} and no-ops otherwise.</li>
     *   <li>{@link io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint},
     *       {@link PredicateConstraint}, {@link ImplicationConstraint} — none of these implement
     *       {@link io.github.rcrida.jcsp.consistency.Propagatable} or {@link BinaryDecomposable}, so
     *       no propagator ever sees them.</li>
     *   <li>{@link ReifiedConstraint} — likewise not {@code Propagatable}; its {@code BinaryDecomposable}
     *       output only exists when the body is a {@code UnaryConstraint}, and AC3 gates that arc on
     *       {@code DiscreteDomain} the same way as above.</li>
     *   <li>{@link NaryElementConstraint} — the one exception that's already explicitly defensive:
     *       every propagation pass checks {@code instanceof DiscreteDomain} up front and returns
     *       {@code Optional.of(Map.of())} (a no-op) the moment {@code result} or any {@code vars[i]}
     *       is a {@code BoundedDomain}, so mixing a discrete {@code index} with continuous
     *       {@code result}/{@code vars} was always safe at the propagator level, just previously
     *       blocked by this list. Enabling it also surfaced (and required fixing) two latent bugs
     *       one layer down, in {@code TreeDecompositionSolver}'s machinery — reachable by any
     *       {@code BoundedDomain} CSP with 3+ mutually connected variables not fully resolved by
     *       propagation, not just this constraint: {@code TreeDecomposerImpl.getMaximalCliqueBags}'s
     *       min-degree elimination used to represent purely-structural "fill-in" edges as a real
     *       {@code BinaryTuplesConstraint} pushed through the fully validated CSP builder (fixed by
     *       maintaining the working adjacency graph as a plain map instead); and
     *       {@code AssignmentDomain.populateCombinations} unconditionally cast every clique
     *       variable's domain to {@code DiscreteDomain} to enumerate it (fixed by special-casing an
     *       already-singleton {@code BoundedDomain} via {@code Domain#singleValue()} — the only
     *       shape one can be by the time it reaches a clique, since the satisfaction chain's
     *       {@code PropagationFixpointSolver(snap=true)} always resolves bounded domains to
     *       singletons before tree decomposition ever runs).</li>
     * </ul>
     * <p>
     * Lists {@link GroundNogoodConstraint} and {@link RangeNogoodConstraint} specifically, not the
     * {@link NogoodConstraint} interface they implement — this check matches on
     * {@link Object#getClass()}, the concrete runtime type, so any future additional
     * {@code NogoodConstraint} implementation needs its own entry here too.
     */
    private static final Set<Class<? extends Constraint>> CONTINUOUS_COMPATIBLE_CONSTRAINTS =
            Set.of(SumBoundConstraint.class, SumVariableConstraint.class, LinearBoundConstraint.class, LinearVariableConstraint.class, UnaryComparatorConstraint.class, BinaryComparatorConstraint.class, BinaryOffsetConstraint.class, AbsoluteDifferenceConstraint.class, DivisionConstraint.class, LexConstraint.class, CumulativeConstraint.class, MaxConstraint.class, MinConstraint.class, ProductConstraint.class, DiffnConstraint.class, GroundNogoodConstraint.class, RangeNogoodConstraint.class, IncreasingConstraint.class, DecreasingConstraint.class, UnaryPredicateConstraint.class, BinaryPredicateConstraint.class, PredicateConstraint.class, ReifiedConstraint.class, ImplicationConstraint.class, NaryElementConstraint.class);

    /**
     * Constraint types that support {@link io.github.rcrida.jcsp.domains.SetBoundedDomain} (e.g.
     * {@link io.github.rcrida.jcsp.domains.SetIntervalDomain}) variables. Any other constraint
     * type referencing such a variable is rejected at build time — the set-CP analogue of {@link
     * #CONTINUOUS_COMPATIBLE_CONSTRAINTS}, checked via the same generalised {@link
     * #validateDomainKindCompatibility} this class uses for both.
     */
    private static final Set<Class<? extends Constraint>> SET_COMPATIBLE_CONSTRAINTS =
            Set.of(SubsetConstraint.class, DisjointConstraint.class, IntersectionCardinalityConstraint.class,
                    GroundNogoodConstraint.class, SetBoundsNogoodConstraint.class);

    Map<Variable<?>, Domain<?>> variableDomains;
    // Included in equals/hashCode (via ConstraintGraph's own, which compares constraints/isCyclic/
    // isFullyConnected and excludes only its derived neighbours/allBinaryConstraints caches) so that
    // two CSPs with the same variables but different constraints are correctly distinct.
    @Getter(AccessLevel.NONE) ConstraintGraph constraintGraph;
    // Excluded: nogoods are learned facts accumulated during search, not part of the problem's
    // identity -- two CSPs that differ only in what a search has learned so far are the same problem.
    @EqualsAndHashCode.Exclude Set<NogoodConstraint> nogoods;
    // Excluded: purely a derived cache of constraintGraph's constraints + nogoods (see
    // mergedWithNogoods), already covered transitively by constraintGraph and nogoods above.
    @Getter(AccessLevel.NONE) @EqualsAndHashCode.Exclude @ToString.Exclude Set<Constraint> allConstraints;
    // Mutable-state-inside-@Value cache (same pattern as NogoodStore/SolverLimits), threaded forward
    // via toBuilder() exactly like constraintGraph is: remembers the single most recent (nogoods
    // reference, merged result) pair so that repeated calls with the same nogoods Set reference --
    // which NogoodStore guarantees between nogood-learning events, via its own snapshot cache --
    // skip rebuilding allConstraints entirely instead of paying for it on every search node.
    @Getter(AccessLevel.NONE) @EqualsAndHashCode.Exclude @ToString.Exclude AtomicReference<NogoodMergeCache> nogoodMergeCache;

    private record NogoodMergeCache(Set<NogoodConstraint> nogoods, Set<Constraint> merged) {
    }

    /**
     * Constructor ensures constraints reference known variables and determines whether graph is cyclic and/or
     * fully connected. When a {@code constraintGraph} is supplied whose constraint set matches {@code constraints}
     * (e.g. via {@link #toBuilder()} during domain-only updates) it is reused directly, avoiding redundant
     * recomputation of neighbours and binary constraints.
     * <p>
     * {@code nogoods} is layered on top of {@code constraints} without ever influencing
     * {@code constraintGraph}: a {@link NogoodConstraint} is neither a {@link BinaryConstraint} nor
     * {@link BinaryDecomposable}, so it never contributes to neighbours, binary decomposition, or
     * cycle/connectivity analysis. This is what lets {@link #withNogoods} add learned nogoods without
     * ever triggering graph recomputation.
     *
     * @param variableDomains the variables and their corresponding domains for the problem
     * @param constraints the structural constraints that will apply to the solution
     * @param constraintGraph pre-computed constraint graph to reuse, or {@code null} to compute fresh
     * @param nogoods learned nogoods folded into {@link #getConstraints()} but excluded from the constraint graph
     * @param nogoodMergeCache pre-existing merge cache to reuse (see the field javadoc), or {@code null} for a fresh one
     */
    @Builder
    ConstraintSatisfactionProblem(@Singular("variableDomainEntry") Map<Variable<?>, Domain<?>> variableDomains, @Singular Set<Constraint> constraints, @Nullable ConstraintGraph constraintGraph, @Nullable Set<NogoodConstraint> nogoods, @Nullable AtomicReference<NogoodMergeCache> nogoodMergeCache) {
        this.variableDomains = variableDomains;
        if (constraintGraph != null && constraintGraph.getConstraints().equals(constraints)) {
            this.constraintGraph = constraintGraph;
        } else {
            validateConstraints(variableDomains, constraints);
            this.constraintGraph = new ConstraintGraph(constraints, variableDomains.keySet());
        }
        this.nogoods = nogoods == null ? Set.of() : nogoods;
        assert this.nogoods.stream().flatMap(n -> n.getVariables().stream()).allMatch(variableDomains::containsKey)
                : "Nogoods reference unknown variables";
        this.nogoodMergeCache = nogoodMergeCache != null ? nogoodMergeCache : new AtomicReference<>();
        this.allConstraints = this.nogoods.isEmpty() ? this.constraintGraph.getConstraints() : mergedWithNogoods(this.nogoods);
    }

    /**
     * Returns {@code constraintGraph.getConstraints()} unioned with {@code nogoods}, reusing the
     * last computed merge from {@link #nogoodMergeCache} when {@code nogoods} is the exact same
     * {@link Set} reference as last time (a cheap identity check) instead of rebuilding a fresh
     * {@code HashSet} of every structural constraint plus every nogood on every call. Correct
     * regardless of hit rate: a reference match can only ever return a result actually computed for
     * that exact object, so a miss (different nogoods reference) always falls back to a fresh,
     * correct merge. Relies on {@link NogoodStore#apply} handing back the same cached snapshot
     * reference across calls where nothing has changed for this cache to have a good hit rate.
     */
    private Set<Constraint> mergedWithNogoods(Set<NogoodConstraint> nogoods) {
        NogoodMergeCache cached = nogoodMergeCache.get();
        if (cached != null && cached.nogoods() == nogoods) {
            return cached.merged();
        }
        val merged = new HashSet<Constraint>(constraintGraph.getConstraints());
        merged.addAll(nogoods);
        Set<Constraint> result = Set.copyOf(merged);
        nogoodMergeCache.set(new NogoodMergeCache(nogoods, result));
        return result;
    }

    /**
     * Returns a builder pre-populated from this instance, sharing the existing {@link ConstraintGraph}
     * reference, current nogoods, and nogood-merge cache. Domain-only modifications via the builder
     * will reuse the constraint graph without recomputation; modifications to the constraint set
     * will trigger a fresh computation.
     */
    public ConstraintSatisfactionProblemBuilder toBuilder() {
        return builder()
                .variableDomains(variableDomains)
                .constraints(constraintGraph.getConstraints())
                .constraintGraph(constraintGraph)
                .nogoods(nogoods)
                .nogoodMergeCache(nogoodMergeCache);
    }

    /**
     * Returns this problem with its learned nogoods replaced by {@code newNogoods}. Reuses the existing
     * {@link ConstraintGraph} untouched — see the constructor javadoc for why nogoods never need to
     * affect it — so this never recomputes neighbours, binary decomposition, or cycle/connectivity
     * analysis; only the flat set returned by {@link #getConstraints()} changes, and only when it
     * actually needs to: if {@code newNogoods} is the exact same {@link Set} reference already held
     * by this instance (nothing learned since this CSP was built — the common case for most of a
     * search tree, since {@link NogoodStore#apply} only ever produces a new reference when something
     * was actually recorded or evicted), this returns {@code this} directly, skipping the builder,
     * constructor, and merge entirely.
     */
    public ConstraintSatisfactionProblem withNogoods(@NonNull Set<NogoodConstraint> newNogoods) {
        if (newNogoods == this.nogoods) return this;
        return toBuilder().nogoods(newNogoods).build();
    }

    private static void validateConstraints(Map<Variable<?>, Domain<?>> variableDomains, Set<Constraint> constraints) {
        val unknownVariables = constraints.stream()
                .flatMap(c -> c.getVariables().stream())
                .filter(Predicate.not(variableDomains::containsKey))
                .collect(Collectors.toSet());
        if (!unknownVariables.isEmpty()) {
            throw new IllegalArgumentException(String.format("Constraints reference unknown variables %s", unknownVariables));
        }

        validateDomainKindCompatibility(variableDomains, constraints, BoundedDomain.class,
                CONTINUOUS_COMPATIBLE_CONSTRAINTS, "BoundedDomain (e.g. IntervalDomain)");
        validateDomainKindCompatibility(variableDomains, constraints, SetBoundedDomain.class,
                SET_COMPATIBLE_CONSTRAINTS, "SetBoundedDomain (e.g. SetIntervalDomain)");
    }

    /**
     * Shared by both {@link #CONTINUOUS_COMPATIBLE_CONSTRAINTS} and {@link
     * #SET_COMPATIBLE_CONSTRAINTS}: any variable whose domain is an instance of {@code domainKind}
     * may only be referenced by a constraint in {@code compatibleConstraints}, or building the CSP
     * fails fast with {@link IllegalArgumentException}.
     */
    private static void validateDomainKindCompatibility(Map<Variable<?>, Domain<?>> variableDomains, Set<Constraint> constraints,
                                                          Class<?> domainKind, Set<Class<? extends Constraint>> compatibleConstraints,
                                                          String domainKindDescription) {
        val restrictedVariables = variableDomains.entrySet().stream()
                .filter(e -> domainKind.isInstance(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (restrictedVariables.isEmpty()) {
            return;
        }
        for (Constraint constraint : constraints) {
            validateCompatibility(constraint, restrictedVariables, compatibleConstraints, domainKindDescription);
        }
    }

    /**
     * {@link ReifiedConstraint}/{@link ImplicationConstraint} wrap an arbitrary {@code body}
     * constraint that is never registered as a top-level member of {@code constraints} (see
     * {@link ReifiedConstraint#of}) — only the wrapper itself is. Both wrapper types are
     * themselves whitelisted in {@link #CONTINUOUS_COMPATIBLE_CONSTRAINTS} unconditionally (they
     * do no propagation of their own over the body), so without this recursion an incompatible
     * body constraint (e.g. {@code allDiffConstraint}/{@code circuitConstraint} over a
     * {@code BoundedDomain} variable, both explicitly rejected when used directly) would silently
     * bypass the whitelist check entirely by being wrapped in a reification.
     */
    private static void validateCompatibility(Constraint constraint, Set<Variable<?>> restrictedVariables,
                                                Set<Class<? extends Constraint>> compatibleConstraints, String domainKindDescription) {
        if (constraint instanceof ReifiedConstraint reified) {
            validateCompatibility(reified.getBody(), restrictedVariables, compatibleConstraints, domainKindDescription);
            return;
        }
        if (constraint instanceof ImplicationConstraint implication) {
            validateCompatibility(implication.getBody(), restrictedVariables, compatibleConstraints, domainKindDescription);
            return;
        }
        if (compatibleConstraints.contains(constraint.getClass())) {
            return;
        }
        val incompatible = constraint.getVariables().stream()
                .filter(restrictedVariables::contains)
                .collect(Collectors.toSet());
        if (!incompatible.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Variables %s use a %s but are referenced by unsupported constraint %s; only %s support it",
                    incompatible, domainKindDescription, constraint.getClass().getSimpleName(), compatibleConstraints));
        }
    }

    /**
     * Check if the problem is empty.
     *
     * @return true if the problem does not contain any variables.
     */
    public boolean isEmpty() {
        return variableDomains.isEmpty();
    }

    /** Returns true when every variable's domain is a singleton — the problem is fully determined. */
    public boolean isFullyDetermined() {
        return variableDomains.values().stream().allMatch(Domain::isSingleton);
    }

    /**
     * @return true if the problem graph is a tree
     */
    public boolean isTree() {
        return constraintGraph.isTree();
    }

    /**
     * @return true if the constraint graph contains a cycle
     */
    public boolean isCyclic() {
        return constraintGraph.isCyclic();
    }

    /**
     * @return true if all variables with constraints are reachable from each other
     */
    public boolean isFullyConnected() {
        return constraintGraph.isFullyConnected();
    }

    /**
     * A map containing each variable in the problem that has at least one neighbour, along with its neighbours.
     */
    public Map<Variable<?>, Set<Variable<?>>> getNeighbours() {
        return constraintGraph.getNeighbours();
    }

    /** Returns the neighbours of {@code variable}, or an empty set if it has none. */
    public Set<Variable<?>> getNeighbours(Variable<?> variable) {
        return getNeighbours().getOrDefault(variable, Set.of());
    }

    /**
     * A set of all binary constraints applicable to this problem. Where possible casts n-ary constrains
     * as additional binary constraints. Ignores n-ary constraints that aren't decomposable.
     */
    public Set<BinaryConstraint<?, ?>> getAllBinaryConstraints() {
        return constraintGraph.getAllBinaryConstraints();
    }

    public Set<Constraint> getConstraints() {
        return allConstraints;
    }

    /**
     * @return the number of variables in the problem
     */
    public int getNumVariables() {
        return variableDomains.size();
    }

    /**
     * @param variable whose domain we are interested in
     * @return the domain of the specified variable, or empty, if the problem does not contain the variable
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Domain<T>> findDomain(@NonNull Variable<T> variable) {
        return Optional.ofNullable((Domain<T>) variableDomains.get(variable));
    }

    /**
     * @param variable whose domain we are interested in
     * @return the domain of the specified variable
     * @throws java.util.NoSuchElementException if the problem does not contain the variable
     */
    @SuppressWarnings("unchecked")
    public <T> Domain<T> getDomain(@NonNull Variable<T> variable) {
        return (Domain<T>) Optional.ofNullable(variableDomains.get(variable)).orElseThrow();
    }

    /**
     * Check whether the specified variable is allowed to take the specified value
     *
     * @param variable the variable of interest
     * @param value is this value allowed for the variable?
     * @return true if the problem contains the variable and the domain of the variable contains the value
     */
    public boolean isAllowedValue(@NonNull Variable<?> variable, @NonNull Object value) {
        return findDomain(variable)
                .map(domain -> domain.contains(value))
                .orElse(false);
    }

    /**
     * Calculates the size of the search space as the product of the sizes of all of the variable domains.
     *
     * @return the size of the search space
     */
    public BigInteger getSearchSpace() {
        return getVariableDomains().values().stream().map(Domain::size).map(BigInteger::valueOf).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    /**
     * When there are N-ary constraints that can't be recreated as binary constraints
     * then we don't want to split up the constraint during cycle cutset conditioning.
     * The unsplittable constraints should belong entirely in the cycle cutset.
     *
     * @return the set of variables which should not be included in a tree during cycle
     * cutset conditioning.
     */
    @NonNull
    public Set<Variable<?>> getUnsplittableVariables() {
        return getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint)
                .filter(c -> !(c instanceof BinaryDecomposable bd) || bd.getAsBinaryConstraints().isEmpty())
                .map(c -> (NaryConstraint) c)
                .flatMap(c -> c.getVariables().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Decomposes the current problem into independent sub-problems if it has more than one
     * connected component. Returns {@link Optional#empty()} when the problem is fully connected,
     * avoiding the cost of reconstructing a CSP that is equivalent to this one.
     *
     * @return the set of independent sub-problems, or empty if the problem cannot be decomposed
     */
    @NonNull
    public Optional<Set<ConstraintSatisfactionProblem>> decomposeSubproblems() {
        val neighbours = getNeighbours();
        val allVariables = neighbours.keySet();
        if (allVariables.isEmpty()) return Optional.empty();

        // First pass: cheap BFS to detect whether more than one component exists.
        // Avoids the expensive constraint-filtering reconstruction for single-component problems.
        val visited = new HashSet<Variable<?>>();
        val checkQueue = new ArrayDeque<Variable<?>>();
        val first = allVariables.iterator().next();
        checkQueue.add(first);
        visited.add(first);
        while (!checkQueue.isEmpty()) {
            val v = checkQueue.poll();
            for (val neighbour : neighbours.get(v)) {
                if (visited.add(neighbour)) checkQueue.add(neighbour);
            }
        }
        if (visited.size() == allVariables.size()) return Optional.empty();

        // Second pass: build sub-CSPs for each component.
        val unassignedVariables = new HashSet<>(allVariables);
        val subproblems = new HashSet<ConstraintSatisfactionProblem>();
        while (!unassignedVariables.isEmpty()) {
            val subCsp = ConstraintSatisfactionProblem.builder();
            val queue = new ArrayDeque<Variable<?>>();
            addUnassignedVariable(queue, unassignedVariables.iterator().next(), unassignedVariables);
            while (!queue.isEmpty()) {
                val variable = queue.poll();
                subCsp.variableDomainEntry(variable, getDomain(variable));
                subCsp.constraints(getConstraints().stream()
                        .filter(c -> c.getVariables().contains(variable))
                        .collect(Collectors.toSet()));
                neighbours.get(variable).stream()
                        .filter(unassignedVariables::contains)
                        .forEach(neighbour -> addUnassignedVariable(queue, neighbour, unassignedVariables));
            }
            subproblems.add(subCsp.build());
        }
        return Optional.of(subproblems);
    }

    private void addUnassignedVariable(@NonNull Queue<Variable<?>> queue, @NonNull Variable<?> variable, @NonNull Set<Variable<?>> unassignedVariables) {
        queue.add(variable);
        unassignedVariables.remove(variable);
    }

    /**
     * Extract a subset of the current problem that contains only the variables accepted by the
     * specified predicate. This is used by cycle-cutset conditioning to separate the cycle-cutset
     * from the remaining tree.
     *
     * @param variablePredicate determines which variables to include in the sub-problem
     * @return a sub-problem with a reduced set of variables
     */
    public ConstraintSatisfactionProblem withVariableSubset(@NonNull Predicate<Variable<?>> variablePredicate) {
        val variableDomainSubset = getVariableDomains().entrySet().stream()
                .filter(e -> variablePredicate.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        val allConstraints = new HashSet<>(getConstraints());
        // include inferred binary constraints for when multi-variable constraints are split between subsets to capture the constraints
        // within the subset
        allConstraints.addAll(getAllBinaryConstraints());
        val constraintSubset = allConstraints.stream()
                .filter(constraint -> constraint.getVariables().stream()
                        .allMatch(variableDomainSubset::containsKey))
                .toList();
        return toBuilder()
                .clearVariableDomains()
                .variableDomains(variableDomainSubset)
                .clearConstraints()
                .constraints(constraintSubset)
                .build();
    }

    public static class ConstraintSatisfactionProblemBuilder {
        private final Variable.Factory variableFactory = Variable.Factory.INSTANCE;
        private int atLeastNChainCount = 0;

        /**
         * Adds a single nogood, replacing {@code @Singular}'s generated equivalent (removed so that
         * {@link #withNogoods} can hand a {@link NogoodStore}-cached snapshot straight through
         * without the builder silently copying it again — see that method's javadoc). Only used for
         * small-scale/test construction, never the per-node search hot path, so accumulating via a
         * fresh {@link HashSet} on each call is fine.
         */
        public ConstraintSatisfactionProblemBuilder nogood(@NonNull NogoodConstraint nogood) {
            if (this.nogoods == null) {
                this.nogoods = Set.of(nogood);
            } else {
                val combined = new HashSet<>(this.nogoods);
                combined.add(nogood);
                this.nogoods = Set.copyOf(combined);
            }
            return this;
        }

        public <T> Variable<T> createVariable(String name, Domain<T> domain) {
            final var variable = variableFactory.<T>create(name);
            variableDomain(variable, domain);
            return variable;
        }

        @SuppressWarnings("unchecked")
        public <T> Variable<T>[] create1dVariableArray(@NonNull String[] labels, @NonNull String namePrefix, @NonNull Domain<T> domain) {
            final var variables = new Variable[labels.length];
            for (int i = 0; i < labels.length; i++) {
                variables[i] = createVariable(String.format("%s%s", namePrefix, labels[i]), domain);
            }
            return variables;
        }

        @SuppressWarnings("unchecked")
        public <T> Variable<T>[][] create2dVariableArray(@NonNull String[] rows, @NonNull String[] columns, @NonNull String namePrefix, @NonNull Domain<T> domain) {
            final var variables = new Variable[rows.length][columns.length];
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < columns.length; j++) {
                    variables[i][j] = createVariable(String.format("%s%s%s", namePrefix, rows[i], columns[j]), domain);
                }
            }
            return variables;
        }

        /**
         * Register a variable with its domain, enforcing that the domain value type matches the variable type.
         *
         * @param variable the variable to register
         * @param domain the domain of allowed values for the variable
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder variableDomain(@NonNull Variable<T> variable, @NonNull Domain<T> domain) {
            return this.variableDomainEntry(variable, domain);
        }

        /**
         * Create a unary constraint that constrains the specified variable to the specified value.
         *
         * @param variable to be constrained
         * @param value the value the variable must take
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable<T> variable, @NonNull T value) {
            return this.constraint(UnaryValueConstraint.of(variable, value));
        }

        /**
         * Create a binary constraint that constraints two specified variables to have the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable<T> left, @NonNull Variable<T> right) {
            return this.constraint(BinaryEqualsConstraint.of(left, right));
        }

        /**
         * Create a unary constraint that constrains the specified variable to not take a specified value.
         *
         * @param variable to be constrained
         * @param value the value the variable must not take
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable<T> variable, @NonNull T value) {
            return this.constraint(UnaryNotEqualsConstraint.of(variable, value));
        }

        /**
         * Create a unary constraint that evaluates a typed predicate against the value of a single variable.
         *
         * @param variable the variable to be constrained
         * @param predicate determines whether the variable's value satisfies the constraint
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Variable<T> variable, @NonNull Predicate<T> predicate) {
            return this.constraint(UnaryPredicateConstraint.of(variable, predicate));
        }

        /**
         * Create a unary comparator constraint: {@code variable <op> value}.
         * Works with any {@link Number} type that implements {@link Comparable}.
         *
         * @param variable the number variable to constrain
         * @param operator the comparison operator (e.g. {@link Operator#GEQ}, {@link Operator#LT})
         * @param value    the fixed value to compare against
         * @return the builder
         */
        public <N extends Number & Comparable<N>> ConstraintSatisfactionProblemBuilder comparatorConstraint(
                @NonNull Variable<N> variable, @NonNull Operator operator, @NonNull N value) {
            return this.constraint(UnaryComparatorConstraint.of(variable, operator, value));
        }

        /**
         * Create a binary constraint that compares two variables of the same type: {@code left <op> right}.
         * Works with any {@link Comparable} type.
         *
         * @param left     the left variable
         * @param operator the comparison operator (e.g. {@link Operator#LEQ}, {@link Operator#GT})
         * @param right    the right variable
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder comparatorConstraint(
                @NonNull Variable<T> left, @NonNull Operator operator, @NonNull Variable<T> right) {
            return this.constraint(BinaryComparatorConstraint.of(left, operator, right));
        }

        /**
         * Create an AllDiff constraint on the specified set of variables, all sharing the same value type.
         *
         * @param variables to be constrained
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder allDiffConstraint(@NonNull Set<Variable<T>> variables) {
            return this.constraint(AllDiffConstraint.<T>builder().variables(variables).build());
        }

        /**
         * Constrain a set of boolean variables so that at most one is {@code true}.
         * Implemented as pairwise binary constraints — each pair cannot both be {@code true}.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atMostOneConstraint(@NonNull Set<Variable<Boolean>> variables) {
            return this.constraint(AtMostOneConstraint.builder().variables(variables).build());
        }

        /**
         * Create a cumulative scheduling constraint: at every point in time, the sum of resources
         * consumed by concurrently executing tasks must not exceed {@code limit}.
         * Task {@code i} executes during {@code [starts[i], starts[i] + durations[i])}.
         * Equivalent to MiniZinc's {@code cumulative(start, duration, resource, limit)}.
         *
         * @param starts    start-time variables (one per task)
         * @param durations fixed task durations
         * @param resources fixed resource requirements per task
         * @param limit     maximum total resource usage at any instant
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder cumulativeConstraint(
                @NonNull List<Variable<Integer>> starts,
                @NonNull List<Integer> durations,
                @NonNull List<Integer> resources,
                int limit) {
            return this.constraint(CumulativeConstraint.of(starts, durations, resources, limit));
        }

        /**
         * Create a cumulative scheduling constraint with continuous ({@link io.github.rcrida.jcsp.domains.IntervalDomain})
         * start-time variables: at every point in time, the sum of resources consumed by concurrently
         * executing tasks must not exceed {@code limit}.
         * Task {@code i} executes during {@code [starts[i], starts[i] + durations[i])}.
         *
         * @param starts    continuous start-time variables (one per task)
         * @param durations fixed task durations
         * @param resources fixed resource requirements per task
         * @param limit     maximum total resource usage at any instant
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder cumulativeConstraint(
                @NonNull List<Variable<Double>> starts,
                @NonNull List<Double> durations,
                @NonNull List<Double> resources,
                double limit) {
            return this.constraint(CumulativeConstraint.of(starts, durations, resources, limit));
        }

        /**
         * Enforce that the integer successor variables form a single Hamiltonian circuit through
         * all {@code n} nodes. {@code successors.get(i)} represents node {@code i+1}; its value
         * {@code j ∈ {1..n}} means "the successor of node {@code i+1} is node {@code j}".
         * Equivalent to MiniZinc's {@code circuit(successors)}.
         *
         * @param successors ordered list of successor variables (one per node, 1-indexed values)
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder circuitConstraint(
                @NonNull List<Variable<Integer>> successors) {
            return this.constraint(CircuitConstraint.of(successors));
        }

        /**
         * Enforce that {@code n} axis-aligned rectangles do not overlap. Rectangle {@code i}
         * occupies {@code [xs[i], xs[i]+widths[i]) × [ys[i], ys[i]+heights[i])}. Origin
         * variables may be integer ({@link io.github.rcrida.jcsp.domains.IntRangeDomain}) or
         * continuous ({@link io.github.rcrida.jcsp.domains.IntervalDomain}).
         * Equivalent to MiniZinc's {@code diffn(x, y, dx, dy)}.
         *
         * @param xs      x-origin variables (one per rectangle)
         * @param ys      y-origin variables (one per rectangle)
         * @param widths  fixed rectangle widths
         * @param heights fixed rectangle heights
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder diffnConstraint(
                @NonNull List<Variable<?>> xs,
                @NonNull List<Variable<?>> ys,
                @NonNull List<Double> widths,
                @NonNull List<Double> heights) {
            return this.constraint(DiffnConstraint.of(xs, ys, widths, heights));
        }

        /**
         * Enforce that the sequence of values assigned to {@code sequence} is a word accepted
         * by {@code automaton}. Uses forward-backward DP propagation to prune values not on
         * any accepting path. Equivalent to MiniZinc's {@code regular(sequence, automaton)}.
         *
         * @param sequence  ordered list of variables forming the word
         * @param automaton the finite automaton defining the accepted language
         * @param <T>       the value type shared by the sequence variables and automaton alphabet
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder regularConstraint(
                @NonNull List<Variable<T>> sequence,
                @NonNull Automaton<T> automaton) {
            return this.constraint(RegularConstraint.of(sequence, automaton));
        }

        /**
         * Create a binary boolean connective constraint: {@code left <op> right}, where
         * {@code op} is one of {@link LogicOperator}: AND, OR, XOR, NAND, NOR, XNOR.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param left     the left boolean variable
         * @param operator the boolean connective to apply
         * @param right    the right boolean variable
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder logicConstraint(@NonNull Variable<Boolean> left,
                                                                    @NonNull LogicOperator operator,
                                                                    @NonNull Variable<Boolean> right) {
            return this.constraint(BinaryLogicConstraint.of(left, operator, right));
        }

        /**
         * Constrain a set of boolean variables so that at most {@code n} are {@code true}.
         * For N=1, prefer {@link #atMostOneConstraint(Set)}, which provides an AC3-compatible
         * binary decomposition. Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @param n         the maximum number of variables that may be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atMostNConstraint(@NonNull Set<Variable<Boolean>> variables, int n) {
            return this.constraint(AtMostNConstraint.builder().variables(variables).n(n).build());
        }

        /**
         * Constrain a set of boolean variables so that at least {@code n} are {@code true}.
         * For partial assignments the constraint is satisfied as long as reaching {@code n} true
         * values is still possible; it only fails when all variables are assigned and fewer than
         * {@code n} are {@code true}. Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * <p><b>Solver recommendation:</b> preferred for local search via {@link io.github.rcrida.jcsp.solver.LocalSolver}, where
         * it participates directly in conflict detection and value weighting without the overhead of
         * auxiliary variables. For backtracking search via {@link io.github.rcrida.jcsp.solver.Solver.Factory},
         * consider {@link #atLeastNConstraintWithCounting(Set, int)} to enable AC3 and node consistency
         * propagation through a carry-chain.
         *
         * @param variables the boolean variables to constrain
         * @param n         the minimum number of variables that must be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atLeastNConstraint(@NonNull Set<Variable<Boolean>> variables, int n) {
            return this.constraint(AtLeastNConstraint.builder().variables(variables).n(n).build());
        }

        /**
         * Constrain a set of boolean variables so that exactly one is {@code true}.
         * Implemented as pairwise binary constraints — each pair cannot both be {@code true},
         * and an overall constraint when all variables are assigned that exactly one is {@code true}.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder exactlyOneConstraint(@NonNull Set<Variable<Boolean>> variables) {
            if (variables.size() == 1) {
                return this.constraint(UnaryValueConstraint.of(variables.iterator().next(), true));
            }
            return this.constraint(ExactlyOneConstraint.builder().variables(variables).build());
        }

        /**
         * Create a binary constraint that a pair of variables cannot take the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable<T> left, @NonNull Variable<T> right) {
            return this.constraint(BinaryNotEqualsConstraint.of(left, right));
        }

        /**
         * Create a binary constraint over set variables: {@code left ⊆ right}.
         *
         * @param left the variable required to be a subset of {@code right}
         * @param right the variable required to be a superset of {@code left}
         * @return the builder
         */
        public <E> ConstraintSatisfactionProblemBuilder subsetConstraint(@NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right) {
            return this.constraint(SubsetConstraint.of(left, right));
        }

        /**
         * Create a binary constraint over set variables: {@code left ∩ right = ∅}.
         *
         * @param left first set variable of the pair
         * @param right second set variable of the pair
         * @return the builder
         */
        public <E> ConstraintSatisfactionProblemBuilder disjointConstraint(@NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right) {
            return this.constraint(DisjointConstraint.of(left, right));
        }

        /**
         * Create a binary constraint over set variables: {@code |left ∩ right| op bound}. E.g. CSPLib's
         * Social Golfers "no two groups share more than one player across weeks" is {@code
         * intersectionCardinalityConstraint(groupA, groupB, Operator.LEQ, 1)} between every pair of
         * groups drawn from different weeks.
         *
         * @param left first set variable of the pair
         * @param right second set variable of the pair
         * @param operator comparison applied to the intersection's size
         * @param bound the value the intersection's size is compared against
         * @return the builder
         */
        public <E> ConstraintSatisfactionProblemBuilder intersectionCardinalityConstraint(
                @NonNull Variable<Set<E>> left, @NonNull Variable<Set<E>> right, @NonNull Operator operator, int bound) {
            return this.constraint(IntersectionCardinalityConstraint.of(left, right, operator, bound));
        }

        /**
         * Constrain a sequence of variables to be non-decreasing: {@code vars[0] <= vars[1] <= ... <= vars[n-1]}.
         * Equivalent to MiniZinc's {@code increasing(vars)}.
         *
         * @param variables ordered list of variables to constrain
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder increasingConstraint(@NonNull List<Variable<T>> variables) {
            return this.constraint(IncreasingConstraint.of(variables));
        }

        /**
         * Constrain a sequence of variables to be non-increasing: {@code vars[0] >= vars[1] >= ... >= vars[n-1]}.
         * Equivalent to MiniZinc's {@code decreasing(vars)}.
         *
         * @param variables ordered list of variables to constrain
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder decreasingConstraint(@NonNull List<Variable<T>> variables) {
            return this.constraint(DecreasingConstraint.of(variables));
        }

        /**
         * Constrain two equal-length sequences of variables to be lexicographically ordered:
         * {@code left <op> right}. Use {@link Operator#LT} for strict lex-less,
         * {@link Operator#LEQ} for lex-less-or-equal. Equivalent to MiniZinc's
         * {@code lex_less(left, right)} and {@code lex_lesseq(left, right)}.
         *
         * @param left     the left sequence
         * @param operator the comparison operator (typically {@link Operator#LT} or {@link Operator#LEQ})
         * @param right    the right sequence; must be the same length as left
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder lexConstraint(
                @NonNull List<Variable<T>> left, @NonNull Operator operator, @NonNull List<Variable<T>> right) {
            return this.constraint(LexConstraint.of(left, operator, right));
        }

        /**
         * Create a sequence of binary not-equals constraints to ensure that each variable in the specified list
         * cannot take the same value as its neighbours in the list.
         *
         * @param variables a list of variables, neighbours in the list cannot have the same value
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsChainConstraint(@NonNull List<Variable<T>> variables) {
            assert variables.size() > 1;
            val firstIter = variables.iterator();
            val secondIter = variables.iterator();
            secondIter.next();
            while (secondIter.hasNext()) {
                this.constraint(BinaryNotEqualsConstraint.of(firstIter.next(), secondIter.next()));
            }
            return this;
        }

        /**
         * Create a binary constraint on numerical variables. A specified offset is applied to the first variable and the result
         * is compared using the specified operator to the value of the second variable.
         *
         * @param left the first variable
         * @param offset numerical offset, same type as variable domain, can be positive or negative, will be added
         * @param operator the type of comparison to perform, eg ==, !=, &lt;, &lt;=, &gt;=, &gt;
         * @param right the second variable
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder offsetConstraint(@NonNull Variable<N> left, @NonNull N offset, @NonNull Operator operator, @NonNull Variable<N> right) {
            return this.constraint(BinaryOffsetConstraint.of(left, offset, operator, right));
        }

        /**
         * Create a binary constraint on numerical variables enforcing {@code |left - right| op bound}.
         * <p>
         * Supports {@link io.github.rcrida.jcsp.domains.IntervalDomain} variables via interval-arithmetic
         * bounds propagation. {@link Operator#LEQ}/{@link Operator#LT} clips both domains symmetrically;
         * {@link Operator#EQ} adds infeasibility detection; {@link Operator#GEQ}/{@link Operator#GT}
         * detects infeasibility only.
         *
         * @param left     the first variable
         * @param right    the second variable
         * @param operator the comparison operator applied to the absolute difference
         * @param bound    the right-hand side of the comparison, same numeric type as the variables
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder absoluteDifferenceConstraint(@NonNull Variable<N> left, @NonNull Variable<N> right, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(AbsoluteDifferenceConstraint.of(left, right, operator, bound));
        }

        /**
         * Create a binary array-element constraint: {@code result = array[index]}.
         * The index variable is 1-based. Out-of-bounds indices violate the constraint.
         * Equivalent to MiniZinc's {@code element(index, array, result)} constraint.
         *
         * @param index  variable holding the 1-based array index
         * @param result variable constrained to equal {@code array[index]}
         * @param array  fixed array of values
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder elementConstraint(@NonNull Variable<Integer> index, @NonNull Variable<T> result, @NonNull List<T> array) {
            return this.constraint(BinaryElementConstraint.of(index, result, array));
        }

        /**
         * Create an element constraint over a list of variables: {@code result = vars[index]}.
         * The index variable is 1-based. Out-of-bounds indices violate the constraint.
         * Equivalent to MiniZinc's {@code element(index, vars, result)} constraint when {@code vars}
         * is an array of decision variables rather than fixed values.
         *
         * @param index  variable holding the 1-based array index
         * @param result variable constrained to equal {@code vars[index-1]}
         * @param vars   list of variables to select from
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder elementVariableConstraint(@NonNull Variable<Integer> index, @NonNull Variable<T> result, @NonNull List<Variable<T>> vars) {
            return this.constraint(NaryElementConstraint.of(index, result, vars));
        }

        /**
         * Create a constraint that compares the sum of a set of numeric variables to a fixed bound:
         * {@code v1 + v2 + ... + vn <op> bound}.
         *
         * @param variables the numeric variables to sum
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound     the value to compare the sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder sumConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(SumBoundConstraint.of(variables, operator, bound));
        }

        /**
         * Create a constraint that compares the sum of a set of numeric variables to a variable
         * target, rather than a fixed bound: {@code v1 + v2 + ... + vn <op> target}.
         *
         * @param variables the numeric variables to sum
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param target    the variable to compare the sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder sumConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull Variable<N> target) {
            return this.constraint(SumVariableConstraint.of(variables, operator, target));
        }

        /**
         * Create a constraint that compares the maximum of a set of numeric variables to a fixed bound:
         * {@code max(v1, v2, ..., vn) op bound}.
         *
         * @param variables the numeric variables to take the maximum over
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound     the value to compare the maximum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder maxConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(MaxConstraint.of(variables, operator, bound));
        }

        /**
         * Create a constraint that compares the minimum of a set of numeric variables to a fixed bound:
         * {@code min(v1, v2, ..., vn) op bound}.
         *
         * @param variables the numeric variables to take the minimum over
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#GEQ})
         * @param bound     the value to compare the minimum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder minConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(MinConstraint.of(variables, operator, bound));
        }

        /**
         * Create a constraint that compares the product of a set of numeric variables to a fixed bound:
         * {@code v1 * v2 * ... * vn op bound}.
         * <p>
         * Propagation narrows domains for EQ, LEQ, and GEQ operators when all domains have strictly
         * positive minimums. Non-positive domains (including zero) receive no narrowing.
         *
         * @param variables the numeric variables to multiply
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound     the value to compare the product against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder productConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(ProductConstraint.of(variables, operator, bound));
        }

        /**
         * Create a binary constraint enforcing {@code dividend / divisor op bound} (real-valued division).
         * <p>
         * Propagation narrows domains for EQ, LEQ, and GEQ operators when both domains have strictly
         * positive minimums. Non-positive domains (including zero) receive no narrowing.
         *
         * @param dividend the variable in the numerator
         * @param divisor  the variable in the denominator (must not include zero)
         * @param operator the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound    the value to compare the quotient against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder divisionConstraint(@NonNull Variable<N> dividend, @NonNull Variable<N> divisor, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(DivisionConstraint.of(dividend, divisor, operator, bound));
        }

        /**
         * Create a weighted-sum (linear) constraint: {@code a1*v1 + a2*v2 + ... <op> bound}.
         * Coefficients and variables are supplied as a map. Equivalent to MiniZinc's
         * {@code linear(coefficients, variables, bound)} constraint.
         *
         * @param coefficients map from variable to its numeric coefficient
         * @param operator     the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound        the value to compare the weighted sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder linearConstraint(@NonNull Map<Variable<N>, N> coefficients, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(LinearBoundConstraint.of(coefficients, operator, bound));
        }

        /**
         * Create a weighted-sum (linear) constraint compared to a variable target, rather than a
         * fixed bound: {@code a1*v1 + a2*v2 + ... <op> target}.
         *
         * @param coefficients map from variable to its numeric coefficient
         * @param operator     the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param target       the variable to compare the weighted sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder linearConstraint(@NonNull Map<Variable<N>, N> coefficients, @NonNull Operator operator, @NonNull Variable<N> target) {
            return this.constraint(LinearVariableConstraint.of(coefficients, operator, target));
        }

        /**
         * Create a constraint that counts how many variables in a set take a specific value,
         * and compares that count to a bound: {@code count(vars, value) <op> n}.
         *
         * @param variables the variables to count over
         * @param value     the value whose occurrences are counted
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param n         the bound to compare the count against
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder countConstraint(@NonNull Set<Variable<T>> variables, @NonNull T value, @NonNull Operator operator, int n) {
            return this.constraint(CountConstraint.of(variables, value, operator, n));
        }

        /**
         * Create an among constraint: count how many variables take a value from the set {@code S},
         * and compare that count to a bound: {@code among(vars, S) <op> n}.
         * Equivalent to MiniZinc's {@code among(n, vars, S)}.
         *
         * @param variables the variables to count over
         * @param values    the set of target values
         * @param operator  the comparison operator
         * @param n         the bound to compare the count against
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder amongConstraint(@NonNull Set<Variable<T>> variables, @NonNull Set<T> values, @NonNull Operator operator, int n) {
            return this.constraint(AmongConstraint.of(variables, values, operator, n));
        }

        /**
         * Create an inverse constraint: {@code f[i] == j ↔ invf[j-1] == i+1} for all {@code i}.
         * Both arrays must have the same length and use 1-based integer values.
         * Equivalent to MiniZinc's {@code inverse(f, invf)}.
         *
         * @param f    the forward permutation variables
         * @param invf the inverse permutation variables
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder inverseConstraint(@NonNull List<Variable<Integer>> f, @NonNull List<Variable<Integer>> invf) {
            return this.constraint(InverseConstraint.of(f, invf));
        }

        /**
         * Create a global cardinality constraint: each value in the map must appear exactly
         * the specified number of times across the variables. Values not in the map are
         * unconstrained (open GCC). Equivalent to MiniZinc's
         * {@code global_cardinality(vars, values, counts)} constraint.
         *
         * @param variables   the variables to constrain
         * @param cardinality map from value to required occurrence count
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder globalCardinalityConstraint(@NonNull Set<Variable<T>> variables, @NonNull Map<T, Integer> cardinality) {
            return this.constraint(GlobalCardinalityConstraint.of(variables, cardinality));
        }

        /**
         * Create an nvalue constraint: {@code count} equals the number of distinct values taken
         * by {@code variables}. Unlike other counting constraints, {@code count} is a genuine
         * decision variable rather than a fixed constant, so it can be handed directly to an
         * optimization objective to minimise the number of distinct values used. Equivalent to
         * MiniZinc's {@code nvalue(count, variables)} constraint.
         *
         * @param variables the variables to constrain
         * @param count     the variable bound to the number of distinct values taken by {@code variables}
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder nValueConstraint(@NonNull Set<Variable<T>> variables, @NonNull Variable<Integer> count) {
            return this.constraint(NValueConstraint.of(variables, count));
        }

        /**
         * Create a bin-packing constraint: each item is assigned to a bin ({@code bin[i]})
         * without any bin's total item weight exceeding its capacity. {@code weights} and
         * {@code capacities} are fixed data, not variables. Equivalent to MiniZinc's
         * {@code bin_packing_capa(capacities, bin, weights)} constraint. Pair with {@link
         * #nValueConstraint} over the same {@code bin} variables to additionally minimise the
         * number of bins used.
         *
         * @param bin        the bin-assignment variable for each item (0-indexed bin number)
         * @param weights    the fixed weight of each item
         * @param capacities the fixed capacity of each bin
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder binPackingConstraint(@NonNull List<Variable<Integer>> bin,
                                                                          @NonNull List<Integer> weights,
                                                                          @NonNull List<Integer> capacities) {
            return this.constraint(BinPackingConstraint.of(bin, weights, capacities));
        }

        /**
         * Create a constraint defined by a set of permitted assignments: the combined values
         * of the constrained variables must match one of the allowed tuples.
         * All tuples must contain exactly the same variable set.
         * Equivalent to MiniZinc's {@code table(x, t)} constraint.
         *
         * @param tuples the allowed assignments; all must share the same variable set
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder tuplesConstraint(@NonNull Set<Assignment> tuples) {
            return this.constraint(NaryTuplesConstraint.of(tuples));
        }

        /**
         * Create an arbitrary binary constraint using a {@link BiPredicate}.
         *
         * @param left the first variable
         * @param right the second variable
         * @param biPredicate determines whether the specified values of the first and second variables are consistent
         * @return the builder
         */
        public <L, R> ConstraintSatisfactionProblemBuilder biPredicateConstraint(@NonNull Variable<L> left, @NonNull Variable<R> right, @NonNull BiPredicate<L, R> biPredicate) {
            return this.constraint(BinaryPredicateConstraint.of(left, right, biPredicate));
        }

        /**
         * Create an arbitrary constraint using a {@link Predicate<Assignment>}. The predicate will need to dereference
         * the values of the variables it requires for the calculation of the predicate value.
         *
         * @param variables the variables that the predicate will reference
         * @param predicate determines whether the specified assignment is consistent
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Set<? extends Variable<?>> variables, @NonNull Predicate<Assignment> predicate) {
            return this.constraint(PredicateConstraint.builder().variables(variables).predicate(predicate).build());
        }

        /**
         * Create a fully reified constraint: {@code indicator <-> body}.
         * The indicator is {@code true} exactly when the body constraint is satisfied.
         *
         * @param indicator boolean variable that captures the body's satisfaction state
         * @param body      the constraint being reified
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder reifyConstraint(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
            return this.constraint(ReifiedConstraint.of(indicator, body));
        }

        /**
         * Create a half-reified (implication) constraint: {@code indicator -> body}.
         * When the indicator is {@code true} the body must be satisfied; {@code false} is unconstrained.
         *
         * @param indicator boolean variable that activates the body constraint
         * @param body      the constraint activated by the indicator
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder impliesConstraint(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
            return this.constraint(ImplicationConstraint.of(indicator, body));
        }

        /**
         * Encodes {@code atLeastN(vars, n)} as a carry-chain over auxiliary integer counting
         * variables, enabling richer propagation than the plain {@link AtLeastNConstraint}.
         *
         * <p>Introduces {@code k+1} integer variables {@code c[0]..c[k]} with domain {@code {0..k}}
         * and {@code k} boolean negation indicators. Constraints:
         * <ul>
         *   <li>{@code c[0] = 0}</li>
         *   <li>for each {@code v_i}: {@code v_i -> c[i] = c[i-1] + 1} and {@code !v_i -> c[i] = c[i-1]}</li>
         *   <li>{@code c[k] >= n}</li>
         * </ul>
         *
         * <p><b>Solver recommendation:</b> use with {@link io.github.rcrida.jcsp.solver.Solver.Factory}
         * (backtracking search), where node consistency and AC3 propagate through the chain and prune
         * the search space. For local search via {@link io.github.rcrida.jcsp.solver.LocalSolver}, prefer the plain
         * {@link #atLeastNConstraint(Set, int)} — the extra chain variables increase repair cost
         * without providing propagation benefit.
         *
         * @param vars the boolean variables to count
         * @param n    minimum number that must be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atLeastNConstraintWithCounting(@NonNull Set<Variable<Boolean>> vars, int n) {
            val varList = List.copyOf(vars);
            int k = varList.size();
            int id = atLeastNChainCount++;

            val counterLabels = IntStream.rangeClosed(0, k).mapToObj(i -> "[" + i + "]").toArray(String[]::new);
            val counters = create1dVariableArray(counterLabels, "$c_" + id + "_", IntRangeDomain.of(0, k));
            equalsConstraint(counters[0], 0);

            val negLabels = IntStream.range(0, k).mapToObj(i -> "[" + i + "]").toArray(String[]::new);
            val negations = create1dVariableArray(negLabels, "$neg_" + id + "_", BooleanDomain.INSTANCE);

            for (int i = 0; i < k; i++) {
                val v    = varList.get(i);
                val prev = counters[i];
                val curr = counters[i + 1];
                val neg  = negations[i];

                reifyConstraint(neg, UnaryNotEqualsConstraint.of(v, true));

                impliesConstraint(v, BinaryOffsetConstraint.of(prev, 1, Operator.EQ, curr));
                impliesConstraint(neg, BinaryEqualsConstraint.of(curr, prev));
            }

            return comparatorConstraint(counters[k], Operator.GEQ, n);
        }
    }
}
