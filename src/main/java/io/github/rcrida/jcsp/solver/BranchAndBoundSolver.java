package io.github.rcrida.jcsp.solver;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.PhaseMemory;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.lp.LpBound;
import io.github.rcrida.jcsp.solver.lp.LpModelBuilder;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * An optimization solver that applies branch-and-bound pruning. Whenever the cost of the current
 * partial assignment meets or exceeds the best complete solution found so far (the
 * <em>incumbent</em>), the branch is cut immediately.
 *
 * <p>Returns a stream of improving complete assignments (each strictly better than the previous);
 * the last element is the global optimum found within the search.
 *
 * <p>The objective is supplied at construction time by {@link Solver.Factory#createSolver(ConstraintSatisfactionProblem, ToDoubleFunction)},
 * so it must return a lower bound on the cost of any completion of a partial assignment.
 *
 * <p>Like {@link DomWdegLubySearch}, folds a shared {@link #nogoodStore} into every node: each
 * candidate value is checked and propagated against {@code nogoodStore.apply(csp)} rather than the
 * bare {@code csp}, and {@link #inference}'s {@link Inference#applyWithReason} is used instead of
 * plain {@link Inference#apply} so a domain wipeout's reason (when one can be derived) is recorded
 * back into the store. This is orthogonal to the incumbent-bound pruning above: a nogood records a
 * genuine constraint violation (permanently true regardless of the incumbent), while the bound cut
 * records a cost dominance relative to the current incumbent -- the two prunings compose freely.
 * <p>
 * {@link #cancellation} is checked at the same site as {@link #limits}, and behaves the same way a
 * limit hit does: the affected candidate is filtered out, so the stream simply yields whatever
 * improving solutions were already found. Unlike {@link DomWdegLubySearch}, this class has no
 * distinct single-solution algorithm of its own -- {@code getSolution()} just consumes this
 * stream -- so it never throws {@link SolverCancelledException}; the caller gets back the best
 * incumbent found so far, exactly like today's {@link LimitExceededException} asymmetry.
 * <p>
 * When {@link #objective} is itself a {@link LinearObjective}, the plain {@code
 * objective.applyAsDouble(partial) >= incumbent} pruning check for non-complete assignments is
 * replaced with a single per-node {@link io.github.rcrida.jcsp.solver.lp.LpModelBuilder#solve} call
 * (see ADR-0009), reused for two purposes: (1) a strictly tighter (never looser) bound than the
 * plain check under the same non-negative-coefficients/non-negative-domain assumption it already
 * requires, with an immediate prune on LP infeasibility -- a relaxation's infeasibility already
 * proves the unrelaxed subtree is infeasible too; and (2), when not pruned, choosing which variable
 * to branch on next: the currently-unassigned variable whose LP-relaxed value is farthest from an
 * integer (standard MIP "most fractional" branching), falling back to {@link
 * #unassignedVariableSelector} when every LP-covered unassigned variable already has an integral
 * value, or none are LP-covered at all. This only changes <em>which</em> variable is decided next,
 * not how its domain is split -- unlike textbook MIP branching's binary {@code x<=floor(v)}/
 * {@code x>=ceil(v)} children, this class still enumerates {@link #domainValuesOrderer}'s full
 * ordering for whichever variable is chosen, so the benefit is search-order quality, not a
 * fundamentally tighter per-child bound. No separate opt-in is needed: {@link Solver.Factory}
 * passing a {@link LinearObjective} as {@code objective} (it implements {@link
 * ToDoubleFunction}{@code <Assignment>}) is what triggers this. Complete assignments are unaffected
 * -- their real cost is exact, so relaxing it would only add solve cost for no benefit.
 * <p>
 * This class now runs <em>before</em> {@link BisectionConditioningSolver} rather than after it (see
 * ADR-0009): {@link Solver.Factory} wires this class directly as the optimization chain's terminal
 * solver even when the problem has {@link BoundedDomain} variables, instead of nesting it inside
 * {@link BisectionConditioningSolver}. Variable selection above only ever considers non-{@link
 * BoundedDomain} ("discrete") variables -- {@link #isDiscreteComplete} recognises the point where
 * every discrete variable is decided but {@link BoundedDomain} variables remain open, and {@link
 * #resolveContinuousResidual} takes over from there: it fills them directly from the same node's LP
 * solution when {@link #objective} is a {@link LinearObjective} and that fill is actually consistent
 * against every constraint (exact, since with every discrete variable already pinned the LP is no
 * longer an approximation for the remaining purely-continuous sub-problem), falling back to
 * {@link BisectionConditioningSolver} -- now invoked internally, once per discrete-complete leaf
 * rather than once for the whole search -- when the fast path doesn't apply or isn't sound (e.g. a
 * {@link BoundedDomain} variable also participates in a constraint the LP can't see, like {@code
 * productConstraint}). This is the fix for the MIPLIB {@code flugpl} case that originally motivated
 * ADR-0009: continuous variables whose useful bounds depend on a still-open discrete decision are no
 * longer bisected blind before that decision is even made. Relies on {@link
 * #unassignedVariableSelector} preferring discrete variables while any remain undecided --
 * {@link io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector}
 * (what {@link Solver.Factory} always wires in) satisfies this by construction, since a
 * non-singleton {@link BoundedDomain}'s {@code size()} is {@link Integer#MAX_VALUE} -- larger than
 * any realistic discrete domain -- so it's never the smallest-remaining-domain choice while a
 * discrete variable is still open. A caller supplying a custom selector directly to this class's
 * builder must preserve that preference itself; {@link #requireDiscrete} fails fast with a clear
 * {@link IllegalStateException} if it doesn't, rather than letting {@link
 * io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer} (and other
 * {@link DomainValuesOrderer}s that assume a {@link io.github.rcrida.jcsp.domains.DiscreteDomain})
 * crash confusingly trying to enumerate a non-singleton {@link BoundedDomain}, which they cannot do.
 */
@Slf4j
@Value
@Builder
public class BranchAndBoundSolver implements Solver {
    /** Below this, an LP-relaxed value is treated as already integral rather than fractional. */
    private static final double FRACTIONAL_EPSILON = 1e-6;

    /**
     * Identity token under which this solver's reusable LP model is cached, per {@link
     * ConstraintSatisfactionProblem} -- see {@link LpModelBuilder#solve(ConstraintSatisfactionProblem,
     * LinearObjective, Object)} for why reuse is keyed on a per-solver token rather than shared. A
     * field with a direct initializer and no {@code @Builder.Default} is invisible to the generated
     * builder (Lombok's convention), so every instance gets its own; excluded from {@code equals}/
     * {@code hashCode}/{@code toString} so it cannot affect this value class's identity.
     */
    @EqualsAndHashCode.Exclude @ToString.Exclude @Getter(AccessLevel.NONE)
    Object lpModelCacheKey = new Object();

    /**
     * Single-slot cache for {@link #objectiveCut}'s constraint, keyed on the incumbent it was built
     * for. The incumbent changes only when a strictly better solution is found -- rare relative to
     * the per-node rate this is consulted at -- so rebuilding the constraint (which copies a
     * variable set) at every node would be pure waste. Same field conventions and rationale as
     * {@link #lpModelCacheKey}: direct initializer so Lombok's builder never sees it, and excluded
     * from this value class's identity.
     */
    @EqualsAndHashCode.Exclude @ToString.Exclude @Getter(AccessLevel.NONE)
    AtomicReference<ObjectiveCut> objectiveCutCache = new AtomicReference<>();

    /** The cut constraint built for one incumbent value; see {@link #objectiveCutCache}. */
    private record ObjectiveCut(double incumbent, @Nullable LinearBoundConstraint<Integer> constraint) {}

    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull ToDoubleFunction<Assignment> objective;
    @Builder.Default
    @NonNull SolverLimits limits = SolverLimits.unlimited();
    @Builder.Default
    @NonNull NogoodStore nogoodStore = new NogoodStore();
    /**
     * Shared token the root {@link Assignment} is seeded with (instead of a fresh {@code
     * Assignment.empty()}), so it's readable via {@code SolverConfig.getStatistics()} after the
     * call regardless of whether an improving solution was ever found.
     */
    @Builder.Default
    @NonNull Statistics statistics = new Statistics();
    @Builder.Default
    @NonNull SolverListener listener = SolverListener.NONE;
    @Builder.Default
    @NonNull Cancellation cancellation = Cancellation.NEVER;

    /**
     * Values from the current incumbent, tried first when branching -- solution-guided search.
     * <p>
     * Branch-and-bound keeps descending after an improvement rather than restarting, and rebuilds
     * each subsequent path from {@link #domainValuesOrderer} alone, which knows nothing about the
     * solution just found. Since an improving solution is by construction the best region seen, its
     * values are the best available guess for the next one, and replaying them steers the search
     * back towards it instead of re-deriving the neighbourhood value by value.
     * <p>
     * Reordering candidate values cannot affect soundness or completeness -- every value is still
     * tried, and the incumbent bound alone decides what gets pruned. It only changes the order
     * improving solutions are discovered in, which is not something {@link #getSolutions} promises
     * beyond their being improving.
     */
    @Builder.Default
    @NonNull PhaseMemory phaseMemory = new PhaseMemory();

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        long deadline = limits.deadlineNanos();
        return search(csp, Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build(), incumbent, deadline);
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       double[] incumbent,
                                       long deadline) {
        if (assignment.isComplete(csp) || isDiscreteComplete(csp, assignment)) {
            return resolveComplete(csp, assignment, incumbent);
        }
        ConstraintSatisfactionProblem cutCsp = applyObjectiveCut(csp, incumbent[0]);
        if (cutCsp == null) {
            return Stream.empty();
        }
        return searchCut(cutCsp, assignment, incumbent, deadline);
    }

    /** {@link #search}'s continuation once the objective cut has been folded into {@code csp}. */
    private Stream<Assignment> searchCut(ConstraintSatisfactionProblem csp,
                                         Assignment assignment,
                                         double[] incumbent,
                                         long deadline) {
        Variable<?> variable;
        if (objective instanceof LinearObjective linearObjective) {
            Optional<LpBound> bound = LpModelBuilder.solve(csp, linearObjective, lpModelCacheKey);
            if (bound.isEmpty() || bound.get().lowerBound() >= incumbent[0]) {
                return Stream.empty();
            }
            variable = selectFractionalVariable(csp, assignment, bound.get())
                    .orElseGet(() -> unassignedVariableSelector.select(csp, assignment));
        } else {
            if (objective.applyAsDouble(assignment) >= incumbent[0]) {
                return Stream.empty();
            }
            variable = unassignedVariableSelector.select(csp, assignment);
        }
        requireDiscrete(csp, variable);
        return searchValues(variable, csp, assignment, incumbent, deadline);
    }

    /**
     * Narrows {@code csp}'s domains by the <em>objective cut</em> {@code sum(c_v * v) <= incumbent -
     * constant - 1}, or {@code null} when that already wipes a domain out and the whole subtree can
     * be pruned.
     * <p>
     * This is the incumbent expressed as a <em>constraint</em> rather than only as the branch-cut
     * predicate {@link #search} already applies. The distinction is the point: a branch cut rejects
     * one node, whereas narrowing a domain is information every other propagator then compounds
     * with, via the ordinary fixpoint the child node's {@link #inference} runs. Without it the
     * incumbent is invisible to {@link io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint},
     * {@link io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint} and every other
     * propagator, no matter how good it gets.
     * <p>
     * Sound because it removes only assignments whose cost is at least the incumbent, and those are
     * by definition not improving -- the only thing {@link #getSolutions} ever promises to emit.
     * <p>
     * Restricted to a wholly integral {@link LinearObjective} (every coefficient and the constant),
     * which is what makes the strict {@code < incumbent} expressible exactly as {@code <= incumbent
     * - 1} with no epsilon. A non-integral objective is left alone rather than approximated: the
     * cut would have to be loosened by an epsilon to stay sound, and a wrong one here silently
     * discards the true optimum instead of failing.
     */
    private @Nullable ConstraintSatisfactionProblem applyObjectiveCut(ConstraintSatisfactionProblem csp, double incumbent) {
        if (!(objective instanceof LinearObjective linearObjective) || incumbent == Double.MAX_VALUE) {
            return csp;
        }
        LinearBoundConstraint<Integer> constraint = cutFor(linearObjective, incumbent, csp);
        if (constraint == null) {
            return csp;
        }
        Optional<Map<Variable<?>, Domain<?>>> narrowed = constraint.propagate(csp.getVariableDomains());
        if (narrowed.isEmpty()) {
            return null;
        }
        return narrowed.get().isEmpty() ? csp : csp.withDomains(narrowed.get());
    }

    /**
     * The cut constraint for {@code incumbent}, from {@link #objectiveCutCache} when it was already
     * built for that same incumbent. A {@code null} entry is cached too, so a non-integral objective
     * is diagnosed once rather than re-examined at every node.
     */
    private @Nullable LinearBoundConstraint<Integer> cutFor(LinearObjective objective, double incumbent,
                                                            ConstraintSatisfactionProblem csp) {
        ObjectiveCut cached = objectiveCutCache.get();
        if (cached != null && cached.incumbent() == incumbent) {
            return cached.constraint();
        }
        LinearBoundConstraint<Integer> built = buildCut(objective, incumbent, csp);
        objectiveCutCache.set(new ObjectiveCut(incumbent, built));
        return built;
    }

    /**
     * The cut as a {@link LinearBoundConstraint}, or {@code null} when this objective can't be
     * expressed as one exactly -- see {@link #applyObjectiveCut} for why an inexact cut is not worth
     * having. The bound is {@code incumbent - constant - 1}: one strictly better than the incumbent,
     * which for a wholly integral objective is exactly representable rather than an epsilon away.
     * <p>
     * A {@link BoundedDomain} anywhere in the objective disqualifies it outright, for two separate
     * reasons that happen to coincide: {@code -1} is not the next representable improvement over a
     * continuous cost, and an {@code Integer}-bounded {@link LinearBoundConstraint} dispatches to
     * integer propagation, which cannot read a continuous domain at all. The MIPLIB {@code flugpl}
     * instance (see {@code FlugplTest}) is exactly this mixed integer/continuous shape.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable LinearBoundConstraint<Integer> buildCut(LinearObjective objective, double incumbent,
                                                                     ConstraintSatisfactionProblem csp) {
        double bound = incumbent - objective.getConstant() - 1;
        if (!isExactInt(bound)) {
            return null;
        }
        Map<Variable<Integer>, Integer> coefficients = new HashMap<>();
        for (var entry : objective.getCoefficients().entrySet()) {
            if (!isExactInt(entry.getValue()) || csp.getDomain(entry.getKey()) instanceof BoundedDomain<?>) {
                return null;
            }
            coefficients.put((Variable<Integer>) entry.getKey(), entry.getValue().intValue());
        }
        return LinearBoundConstraint.of(coefficients, Operator.LEQ, (int) bound);
    }

    /**
     * Whether {@code value} is a whole number that survives a cast to {@code int} unchanged. The
     * magnitude test is not redundant with the first: it rejects an infinity (which {@link
     * Math#rint} reports as already whole) and a large finite value (which the cast would silently
     * wrap), either of which would otherwise produce a wrong cut rather than no cut.
     */
    private static boolean isExactInt(double value) {
        return value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE;
    }

    /**
     * Fails fast, with a clear diagnosis, instead of letting {@link #domainValuesOrderer} crash
     * confusingly deep inside itself (e.g. {@code LeastConstrainingValueOrderer} casting to {@code
     * DiscreteDomain}) when {@link #unassignedVariableSelector} violates the discrete-first contract
     * documented on this class: reaching this point already means a discrete variable is still open
     * (the {@link #isDiscreteComplete} check in {@link #search} didn't short-circuit), so {@code
     * variable} being a <em>non-singleton</em> {@link BoundedDomain} here can only mean the selector
     * picked an unresolved continuous variable while a discrete one remained open. A <em>singleton</em>
     * {@link BoundedDomain} is fine -- e.g. {@code flugpl}'s {@code STM1}, pinned by its own equality
     * constraint before search even starts -- since {@link #domainValuesOrderer} implementations
     * already special-case a singleton {@link BoundedDomain} the same way {@link
     * io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector} (what
     * {@link Solver.Factory} always wires in) naturally prefers it anyway, being the smallest
     * possible domain size. A caller-supplied custom selector passed directly to this class's
     * builder could still violate the non-singleton case.
     */
    private static void requireDiscrete(ConstraintSatisfactionProblem csp, Variable<?> variable) {
        if (csp.getDomain(variable) instanceof BoundedDomain<?> bd && !bd.isSingleton()) {
            throw new IllegalStateException(
                    "unassignedVariableSelector selected non-singleton continuous variable '" + variable
                            + "' while a discrete variable was still unassigned. BranchAndBoundSolver "
                            + "requires unassignedVariableSelector to prefer discrete variables while any "
                            + "remain open (see this class's own Javadoc); MinimumRemainingValuesSelector "
                            + "satisfies this by construction.");
        }
    }

    /**
     * Whether every non-{@link BoundedDomain} ("discrete") variable in {@code csp} has a value in
     * {@code assignment}, regardless of whether any {@link BoundedDomain} variable does. Vacuously
     * true for a purely continuous problem (no discrete variables to wait for), which is what makes
     * {@link #resolveComplete} degenerate correctly to "resolve the whole thing via {@link
     * #resolveContinuousResidual}" for a CSP like {@code ContinuousOptimizationTest}'s.
     */
    private static boolean isDiscreteComplete(ConstraintSatisfactionProblem csp, Assignment assignment) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> !(e.getValue() instanceof BoundedDomain<?>))
                .allMatch(e -> assignment.getValue(e.getKey()).isPresent());
    }

    /**
     * Resolves whatever remains -- nothing, if {@code assignment} is already fully complete, or the
     * open {@link BoundedDomain} variables via {@link #resolveContinuousResidual} otherwise -- into a
     * single candidate solution, then applies the same cost/incumbent check every complete assignment
     * gets.
     */
    private Stream<Assignment> resolveComplete(ConstraintSatisfactionProblem csp, Assignment assignment, double[] incumbent) {
        Optional<Assignment> complete = assignment.isComplete(csp)
                ? Optional.of(assignment)
                : resolveContinuousResidual(csp, assignment, incumbent[0]);
        if (complete.isEmpty()) {
            return Stream.empty();
        }
        Assignment solution = complete.get();
        double cost = objective.applyAsDouble(solution);
        if (cost >= incumbent[0]) {
            return Stream.empty();
        }
        incumbent[0] = cost;
        phaseMemory.recordSolution(solution.getValues());
        log.info("Found improving solution with cost {}: {}", cost, solution);
        listener.onIncumbentImproved(solution, cost);
        return Stream.of(solution);
    }

    /**
     * Fills the {@link BoundedDomain} variables {@code assignment} left open. Tries the exact fast
     * path first -- when {@link #objective} is a {@link LinearObjective}, the same node's LP solution
     * already gives the optimal value for every {@link BoundedDomain} variable the LP model covers;
     * with every discrete variable already pinned, that's no longer an approximation for the
     * remaining purely-continuous sub-problem, just its exact solution -- accepted only if it fills
     * every open variable and satisfies every constraint (a {@link BoundedDomain} variable can
     * participate in a constraint the LP can't see, e.g. {@code productConstraint}, which this
     * consistency check catches). Falls back to a fresh, single-use {@link BisectionConditioningSolver}
     * over just this residual otherwise, with {@link SolverDecorator#forcedSolution} as its own
     * {@code inner}: {@link BisectionConditioningSolver#getSolutions} delegates straight to {@code
     * inner} without bisecting at all whenever {@code csp} already has no non-singleton {@link
     * BoundedDomain} variable left -- a common case, not just a defensive fallback, since a {@link
     * BoundedDomain} residual variable can collapse to a singleton via propagation triggered by the
     * very inference step that completes the last discrete decision, before {@link
     * #unassignedVariableSelector} ever gets a chance to pick it up through the ordinary branching
     * path. {@link SolverDecorator#forcedSolution} is exactly the right tool for that: extract the
     * now-singleton values and validate them, the same way {@link BisectionConditioningSolver}'s own
     * fully-bisected leaves already do internally. {@code incumbent} seeds the fallback's own
     * bisection recursion (see {@link BisectionConditioningSolver#getSolutions(ConstraintSatisfactionProblem,
     * double)}), so a residual that can't possibly beat a bound already found elsewhere in the outer
     * search is pruned immediately rather than fully explored from scratch on every discrete-complete
     * leaf.
     */
    private Optional<Assignment> resolveContinuousResidual(ConstraintSatisfactionProblem csp, Assignment assignment, double incumbent) {
        if (objective instanceof LinearObjective linearObjective) {
            Optional<Assignment> viaLp = LpModelBuilder.solve(csp, linearObjective, lpModelCacheKey)
                    .map(bound -> mergeUnassigned(assignment, bound.solution()))
                    .filter(candidate -> candidate.isComplete(csp) && candidate.isConsistent(csp));
            if (viaLp.isPresent()) {
                return viaLp;
            }
        }
        BisectionConditioningSolver bisection = BisectionConditioningSolver.builder()
                .inner(candidate -> SolverDecorator.forcedSolution(candidate).stream())
                .epsilon(Solver.Factory.DEFAULT_BISECTION_EPSILON)
                .objective(objective)
                .build();
        // Not bisection.getSolution(csp): that's BisectionConditioningSolver's own
        // getSolutions(csp).findFirst() -- the first improving point its left-to-right recursive
        // descent happens to reach, not the best one. Exhausting the full improving stream and
        // taking the last element is what actually converges to this residual's optimum.
        return bisection.getSolutions(csp, incumbent).reduce((a, b) -> b).map(assignment::merge);
    }

    /**
     * Merges {@code values} into {@code assignment} for whichever of its keys {@code assignment}
     * hasn't already decided -- {@code values} may cover more than just the currently-open variables
     * (an LP model's {@link LpBound#solution()} spans every variable it built rows for, including
     * already-singleton ones), so already-decided keys are left untouched rather than overwritten.
     */
    private static Assignment mergeUnassigned(Assignment assignment, Map<Variable<?>, Double> values) {
        Map<Variable<?>, Object> unassigned = new HashMap<>();
        for (var entry : values.entrySet()) {
            if (assignment.getValue(entry.getKey()).isEmpty()) {
                unassigned.put(entry.getKey(), entry.getValue());
            }
        }
        return assignment.merge(Assignment.of(unassigned));
    }

    /**
     * Among {@code bound}'s LP-covered variables that {@code assignment} hasn't decided yet and whose
     * domain isn't a {@link BoundedDomain} (see this class's own Javadoc for why continuous variables
     * are never a branching candidate here), picks the one whose relaxed value is farthest from an
     * integer ({@code min(frac, 1-frac)}, maximal at a half-integer) -- standard MIP "most fractional"
     * branching. {@link Optional#empty()} when no unassigned discrete variable is LP-covered, or
     * every covered one is already within {@link #FRACTIONAL_EPSILON} of an integer, letting the
     * caller fall back to {@link #unassignedVariableSelector}.
     */
    private Optional<Variable<?>> selectFractionalVariable(ConstraintSatisfactionProblem csp, Assignment assignment, LpBound bound) {
        Variable<?> best = null;
        double bestFractionality = FRACTIONAL_EPSILON;
        for (var entry : bound.solution().entrySet()) {
            Variable<?> variable = entry.getKey();
            if (assignment.getValue(variable).isPresent() || csp.getDomain(variable) instanceof BoundedDomain<?>) {
                continue;
            }
            double value = entry.getValue();
            double fractionality = Math.min(value - Math.floor(value), Math.ceil(value) - value);
            if (fractionality > bestFractionality) {
                bestFractionality = fractionality;
                best = variable;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Wildcard-capture helper: binds {@code T} so that {@code getDomain(variable)} and
     * {@code withValue(variable, value)} share the same type argument, avoiding an unchecked cast.
     */
    private <T> Stream<Assignment> searchValues(Variable<T> variable,
                                                 ConstraintSatisfactionProblem csp,
                                                 Assignment assignment,
                                                 double[] incumbent,
                                                 long deadline) {
        ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
        @SuppressWarnings("unchecked")
        List<T> candidates = (List<T>) phaseMemory.prioritise(variable,
                (List<Object>) domainValuesOrderer.order(csp, variable, assignment).toList());
        return candidates.stream()
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> {
                    if (limits.checkStop(cancellation, next.getStatistics().getNodesExplored(), deadline)
                            != SolverLimits.StopReason.NONE) {
                        if (cancellation.isCancelled()) {
                            statistics.updateCurrentSearchSpace(csp.getSearchSpace());
                        }
                        return false;
                    }
                    if (!next.isConsistentAmong(cspWithNogoods.getConstraintsTouching(variable))) {
                        next.getStatistics().incrementBacktracks();
                        listener.onBacktrack(variable, next);
                        return false;
                    }
                    return true;
                })
                .flatMap(next -> {
                    try {
                        return inferOrExplain(cspWithNogoods, variable, next)
                                .map(inferred -> search(inferred, next, incumbent, deadline))
                                .orElseGet(Stream::empty);
                    } catch (SolverCancelledException e) {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Calls {@link #inference}'s {@link Inference#applyWithReason} unconditionally, mirroring
     * {@link DomWdegLubySearch#inferOrExplain}: whatever {@link Inference} is configured is
     * polymorphically responsible for both propagating and, on failure, explaining itself in one
     * pass. A {@code null} {@link ConsistencyResult#reason()} means the configured {@link
     * Inference} doesn't want a nogood recorded for this failure (see
     * {@link Inference#withoutReasonTracking}) -- this method has no fallback of its own for that
     * case, since choosing whether/how to explain is entirely {@link #inference}'s job.
     */
    private Optional<ConstraintSatisfactionProblem> inferOrExplain(ConstraintSatisfactionProblem cspWithNogoods,
                                                                     Variable<?> variable,
                                                                     Assignment next) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next);
        if (inferred.isInfeasible()) {
            if (inferred.reason() != null) {
                nogoodStore.record(inferred.reason());
                next.getStatistics().incrementNogoodsLearned();
                listener.onNogoodLearned(inferred.reason());
            }
            next.getStatistics().incrementBacktracks();
            listener.onBacktrack(variable, next);
            return Optional.empty();
        }
        return Optional.of(inferred.problem());
    }
}
