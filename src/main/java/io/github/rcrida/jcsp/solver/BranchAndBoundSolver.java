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
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.PhaseMemory;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.solver.lp.LpBound;
import io.github.rcrida.jcsp.solver.lp.LpModelBuilder;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
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
 * integer (standard MIP "most fractional" branching), falling back to {@link #selectorFactory} when every LP-covered unassigned variable already has an integral
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
 * {@link BoundedDomain} variable also participates in a constraint the LP can't see, like {@link
 * ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder#productConstraint(java.util.Set,
 * io.github.rcrida.jcsp.constraints.Operator, Number)}). Continuous variables whose useful bounds
 * depend on a still-open discrete decision are therefore not bisected before that decision is made.
 * Which variable is branched on does not depend on {@link #selectorFactory} honouring that split:
 * {@link #branchVariable} substitutes a discrete variable whenever the selector picks a non-singleton
 * {@link BoundedDomain} one, since no {@link DomainValuesOrderer} can enumerate one.
 * <p>
 * Reaching the <em>first</em> solution is a pure feasibility search: there is no incumbent yet, so
 * neither the bound check in {@link #searchCut} nor {@link #applyObjectiveCut} can prune anything,
 * and on a heavy-tailed problem one descent can commit to a subtree it never escapes. An optional
 * {@link #incumbentSeeder} is given that job instead, and how it goes about it is entirely its own
 * business -- {@link Solver.Factory} supplies a {@link BoundedFirstSolution} over the satisfaction
 * chain's own search. The search that follows is the unchanged, unrestarted one, which is what keeps
 * draining this stream a proof of optimality. See
 * <a href="../../../../../../../docs/adr/0041-reuse-the-satisfaction-search-for-the-first-solution.md">ADR-0041</a>
 * and <a href="../../../../../../../docs/adr/0044-bounded-probes-for-the-starting-incumbent.md">ADR-0044</a>.
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
     * Applies the incumbent as a propagated constraint; see {@link #applyObjectiveCut}. Carries its
     * own single-slot cache, since the incumbent changes only when a strictly better solution is
     * found -- rare relative to the per-node rate it is consulted at -- so rebuilding the constraint
     * (which copies a variable set) at every node would be pure waste. Same field conventions and
     * rationale as {@link #lpModelCacheKey}: direct initializer so Lombok's builder never sees it,
     * and excluded from this value class's identity.
     */
    @EqualsAndHashCode.Exclude @ToString.Exclude @Getter(AccessLevel.NONE)
    ObjectiveCut objectiveCut = new ObjectiveCut();

    /**
     * Built once per {@link #getSolutions} call, as {@link DomWdegLubySearch} does and for the same
     * reason: an {@link AdaptiveVariableSelector} accumulates state about the search it is watching,
     * so two solves from one solver must not share one. Defaults to dom/wdeg via {@link
     * AdaptiveVariableSelector.Factory#INSTANCE}, and {@link SolverConfig#getVariableSelectorFactory}
     * reaches here -- until this field existed it reached only the satisfaction chain, so setting it
     * and then calling {@code createSolver(csp, objective)} silently had no effect (ADR-0038).
     */
    @Builder.Default
    AdaptiveVariableSelector.@NonNull Factory selectorFactory = AdaptiveVariableSelector.Factory.INSTANCE;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull ToDoubleFunction<Assignment> objective;
    @Builder.Default
    @NonNull SolverLimits limits = SolverLimits.unlimited();
    /**
     * Accumulates the nogoods this search learns, and is emptied at the start of each {@link
     * #getSolutions} call: unlike {@link DomWdegLubySearch}'s, these are not unconditional facts.
     * A reason derived here comes from domains {@link #applyObjectiveCut} has narrowed, so it means
     * "fails while costing less than the incumbent" -- sound for the rest of one search, where the
     * incumbent only tightens, and unsound for the next one, which starts unbounded again and would
     * otherwise inherit nogoods that refuse the prefix of its own optimum.
     */
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

    /**
     * Supplies a solution to start from, before this class's own search begins -- or {@code null} to
     * descend straight into that search, which is what a directly-constructed solver gets. The
     * optimization chain passes a {@link BoundedFirstSolution} over the satisfaction chain's search.
     * <p>
     * How it finds one is entirely its own business, and nothing about it can affect correctness:
     * only the incumbent crosses the boundary, this class validates what it is handed, and every
     * solution after the first still comes from {@link #search}. See {@link IncumbentSeeder}.
     */
    @Builder.Default
    @Nullable IncumbentSeeder incumbentSeeder = null;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        long deadline = limits.deadlineNanos();
        // Per-solve state, like the selector and the LP gate built below: a nogood here is relative
        // to the incumbent it was derived under, and this search starts unbounded -- see nogoodStore.
        nogoodStore.clear();
        // Fallback for a directly-constructed solver; in the full chain PropagationFixpointSolver
        // has already recorded the post-preprocessing figure and first-write-wins keeps it.
        statistics.updateRootSearchSpace(csp.getSearchSpace());
        Optional<Assignment> seed = seedIncumbent(csp, incumbent);
        Stream<Assignment> improvements = search(csp, rootAssignment(), incumbent, deadline, 1.0, new SearchProgress(),
                selectorFactory.createSelector(csp.getConstraints()), lpGateFor(csp));
        return seed.map(solution -> Stream.concat(Stream.of(solution), improvements)).orElse(improvements);
    }

    /**
     * The LP-bound policy for one solve: the ordinary backing-off {@link LpGate} when {@link
     * #applyObjectiveCut} will carry the incumbent, and {@link LpGate#alwaysSolving} when it cannot.
     * <p>
     * Backing off is sound either way -- a skipped bound forgoes pruning rather than admitting
     * anything -- but it is only affordable while something else enforces the incumbent. An objective
     * with no expressible cut (a fractional coefficient, say) has nothing else: {@link #searchCut}
     * deliberately does not fall back to {@code objective.applyAsDouble} for a {@link LinearObjective},
     * so a gated-off node would carry no bound at all and the search would enumerate.
     */
    private LpGate lpGateFor(ConstraintSatisfactionProblem csp) {
        return objective instanceof LinearObjective linearObjective
                && ObjectiveCut.isExpressible(linearObjective, csp)
                ? new LpGate()
                : LpGate.alwaysSolving();
    }

    /** The root of one search: a fresh {@link Assignment} carrying this solver's shared per-solve state. */
    private Assignment rootAssignment() {
        return Assignment.builder().statistics(statistics).listener(listener).cancellation(cancellation).build();
    }

    /**
     * Adopts {@link #incumbentSeeder}'s solution as the starting incumbent, so the search below
     * begins with a bound instead of having to find one. Empty when there is no seeder or it found
     * nothing, in which case the search below simply starts unbounded as it always did.
     * <p>
     * Validated before being trusted, the same way {@link #resolveContinuousResidual} validates its
     * LP fill: a solution to {@code csp} is feasible for the optimization problem too (an objective
     * is not a constraint), but that is a property of the seeder rather than of anything checked
     * here, and an incumbent that is not actually feasible would prune away real solutions.
     * <p>
     * Only the incumbent is taken from it. The solution's values are <em>not</em> written to
     * {@link #phaseMemory} (see {@link #resolveComplete}, which records its own): a seeder ranks by
     * feasibility first and cost second at best, so replaying its path steers branching toward a
     * region chosen with little regard for cost. Measured, not assumed -- recording it cost
     * {@code TravellingSalesman-20-30-00} an objective of 166-226 against 118 without, across three
     * seeds, and left {@code GraphColoring-3-fullins-4} proving optimality with one second to spare
     * instead of thirty (ADR-0041).
     */
    private Optional<Assignment> seedIncumbent(ConstraintSatisfactionProblem csp, double[] incumbent) {
        if (incumbentSeeder == null) {
            return Optional.empty();
        }
        return incumbentSeeder.seed(csp, objective)
                .filter(solution -> solution.isComplete(csp) && solution.isConsistent(csp))
                .flatMap(solution -> accept(solution, incumbent));
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       double[] incumbent,
                                       long deadline,
                                       double weight,
                                       SearchProgress progress,
                                       AdaptiveVariableSelector selector,
                                       LpGate lpGate) {
        if (assignment.isComplete(csp) || isDiscreteComplete(csp, assignment)) {
            return resolveComplete(csp, assignment, incumbent);
        }
        ObjectiveCut.Narrowed cut = applyObjectiveCut(csp, incumbent[0]);
        if (cut == null) {
            return Stream.empty();
        }
        return searchCut(cut.csp(), cut.variables(), assignment, incumbent, deadline, weight, progress, selector, lpGate);
    }

    /**
     * {@link #search}'s continuation once the objective cut has been folded into {@code csp}.
     * {@code cutNarrowed} is what the cut narrowed, carried down to {@link #inferOrExplain} so the
     * child node's propagation is seeded with it -- see {@link ObjectiveCut.Narrowed}.
     */
    private Stream<Assignment> searchCut(ConstraintSatisfactionProblem csp,
                                         Set<Variable<?>> cutNarrowed,
                                         Assignment assignment,
                                         double[] incumbent,
                                         long deadline,
                                         double weight,
                                         SearchProgress progress,
                                         AdaptiveVariableSelector selector,
                                         LpGate lpGate) {
        Optional<Variable<?>> hint = Optional.empty();
        if (objective instanceof LinearObjective linearObjective) {
            if (lpGate.solveThisNode()) {
                Optional<LpBound> bound = LpModelBuilder.solve(csp, linearObjective, lpModelCacheKey);
                boolean cut = bound.isEmpty() || bound.get().lowerBound() >= incumbent[0];
                lpGate.record(cut);
                if (cut) {
                    return Stream.empty();
                }
                hint = selectFractionalVariable(csp, assignment, bound.get());
            }
            // Gated off: no LP bound and no most-fractional hint this node, and no fallback to the
            // objective.applyAsDouble check below either -- that evaluates a LinearObjective over a
            // *partial* assignment, which is not a lower bound once any coefficient is negative.
            // Nothing is lost by it: the incumbent is enforced by applyObjectiveCut above, and a
            // search whose objective has no expressible cut is given a gate that never backs off
            // (see getSolutions), so this node is reached only when the cut is carrying the bound.
        } else if (objective.applyAsDouble(assignment) >= incumbent[0]) {
            return Stream.empty();
        }
        Variable<?> variable = branchVariable(csp, assignment, hint.orElseGet(() -> selector.select(csp, assignment)));
        return searchValues(variable, csp, cutNarrowed, assignment, incumbent, deadline, weight, progress, selector, lpGate);
    }

    /**
     * Narrows {@code csp}'s domains so nothing at or above the incumbent's cost remains, or {@code
     * null} when that already wipes a domain out and the whole subtree can be pruned.
     * <p>
     * The bound is expressed as a <em>constraint</em> rather than only as the branch-cut predicate
     * {@link #search} already applies, which is the point: a branch cut rejects one node, whereas
     * narrowing a domain is information every other propagator then compounds with, via the ordinary
     * fixpoint the child node's {@link #inference} runs. {@link ObjectiveCut} owns that, shared with
     * {@link BoundedFirstSolution}, which bounds its probes the same way.
     */
    private ObjectiveCut.@Nullable Narrowed applyObjectiveCut(ConstraintSatisfactionProblem csp, double incumbent) {
        return objective instanceof LinearObjective linearObjective
                ? objectiveCut.narrow(csp, linearObjective, incumbent)
                : new ObjectiveCut.Narrowed(csp, Set.of());
    }

    /**
     * The variable to branch on: {@code selected} as the selector chose it, or a discrete substitute
     * when it chose a <em>non-singleton</em> {@link BoundedDomain} one. Reaching this point already
     * means some discrete variable is still open (the {@link #isDiscreteComplete} check in {@link
     * #search} didn't short-circuit), so there is always a substitute to find, chosen by smallest
     * remaining domain.
     * <p>
     * A continuous variable is never branched on here: {@link #resolveContinuousResidual} resolves
     * them all at once, and {@link #domainValuesOrderer} cannot enumerate a non-singleton {@link
     * BoundedDomain} at all (e.g. {@link
     * io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer} casting to
     * {@link io.github.rcrida.jcsp.domains.DiscreteDomain}). A <em>singleton</em> {@link
     * BoundedDomain} is fine -- e.g. {@code flugpl}'s {@code STM1}, pinned by its own equality
     * constraint before search even starts -- since the orderers special-case it.
     * <p>
     * Substituting rather than requiring the selector to prefer discrete variables, because the
     * default selector does not: dom/wdeg ({@link AdaptiveVariableSelector.Factory#INSTANCE}, what
     * {@link Solver.Factory} wires in per ADR-0038) ranks on {@code domainSize / weight}, and a
     * discrete variable whose weight is still zero scores {@link Double#MAX_VALUE} -- worse than any
     * non-singleton {@link BoundedDomain}, whose {@code size()} caps the ratio at {@link
     * Integer#MAX_VALUE}. Late in a descent, where the remaining discrete variables often share no
     * constraint with another unassigned variable, that is an ordinary outcome rather than a
     * misconfiguration, and it used to throw out of the caller's stream.
     */
    private static Variable<?> branchVariable(ConstraintSatisfactionProblem csp, Assignment assignment,
                                               Variable<?> selected) {
        if (!(csp.getDomain(selected) instanceof BoundedDomain<?> bd) || bd.isSingleton()) {
            return selected;
        }
        log.debug("Selector chose continuous {} while a discrete variable was open; substituting", selected);
        // orElseThrow() without a message: unreachable, since isDiscreteComplete short-circuited.
        return csp.getVariableDomains().entrySet().stream()
                .filter(entry -> !(entry.getValue() instanceof BoundedDomain<?>)
                        && assignment.getValue(entry.getKey()).isEmpty())
                .min(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElseThrow()
                .getKey();
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
     * <p>
     * This is also where {@link #phaseMemory} is written, rather than inside {@link #accept}: these
     * are the solutions <em>this</em> search found, so their values are evidence about where cheap
     * solutions live. {@link #seedIncumbent}'s solution is not, and is deliberately not recorded.
     */
    private Stream<Assignment> resolveComplete(ConstraintSatisfactionProblem csp, Assignment assignment, double[] incumbent) {
        Optional<Assignment> complete = assignment.isComplete(csp)
                ? Optional.of(assignment)
                : resolveContinuousResidual(csp, assignment, incumbent[0]);
        Optional<Assignment> accepted = complete.flatMap(solution -> accept(solution, incumbent));
        accepted.ifPresent(solution -> phaseMemory.recordSolution(solution.getValues()));
        return accepted.stream();
    }

    /**
     * Adopts {@code solution} as the new incumbent and returns it, or {@link Optional#empty()} when
     * it does not strictly improve on the current one and so is not something {@link #getSolutions}
     * may emit. Shared by the two places a candidate solution arrives from -- {@link
     * #resolveComplete}'s own leaves and {@link #seedIncumbent}'s injected search -- so that the
     * cost test and the {@link #listener} notification cannot drift apart between them.
     * <p>
     * Writing {@link #phaseMemory} is deliberately <em>not</em> part of this, and is left to
     * {@link #resolveComplete}: a solution this class found is evidence about where cheap solutions
     * live, whereas {@link #seedIncumbent}'s came from a search that never looked at the objective.
     * Recording that one too costs real solution quality -- see ADR-0041 for the measurements.
     */
    private Optional<Assignment> accept(Assignment solution, double[] incumbent) {
        double cost = objective.applyAsDouble(solution);
        if (cost >= incumbent[0]) {
            return Optional.empty();
        }
        incumbent[0] = cost;
        log.info("Found improving solution with cost {}: {}", cost, solution);
        listener.onIncumbentImproved(solution, cost);
        return Optional.of(solution);
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
     * very inference step that completes the last discrete decision, before {@link #selectorFactory} ever gets a chance to pick it up through the ordinary branching
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
     * caller fall back to {@link #selectorFactory}.
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
                                                 Set<Variable<?>> cutNarrowed,
                                                 Assignment assignment,
                                                 double[] incumbent,
                                                 long deadline,
                                                 double weight,
                                                 SearchProgress progress,
                                                 AdaptiveVariableSelector selector,
                                                 LpGate lpGate) {
        // Merged once for the direct check below, which reads the store's live by-variable index and
        // so sees everything recorded since. The merged constraint *set* a fixpoint propagates is a
        // snapshot, though, so the one handed to the inference is re-taken per candidate inside the
        // flatMap -- as DomWdegLubySearch does, and for the same reason: a nogood learned while
        // backtracking one sibling value must be visible to every later sibling's propagation, that
        // being the branch most likely to re-derive the same failure.
        ConstraintSatisfactionProblem cspWithNogoods = nogoodStore.apply(csp);
        @SuppressWarnings("unchecked")
        List<T> candidates = (List<T>) phaseMemory.prioritise(variable,
                (List<Object>) domainValuesOrderer.order(csp, variable, assignment).toList());
        double childWeight = weight / candidates.size();
        return candidates.stream()
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> {
                    if (limits.checkStop(cancellation, next.getStatistics().getNodesExplored(), deadline)
                            != SolverLimits.StopReason.NONE) {
                        if (cancellation.isCancelled()) {
                            statistics.updateCurrentSearchSpace(csp.getSearchSpace());
                            statistics.getRootSearchSpace().ifPresent(
                                    root -> statistics.updateRemainingSearchSpace(progress.remainingOf(root)));
                        }
                        progress.complete(childWeight);
                        return false;
                    }
                    if (!next.isConsistentAmong(cspWithNogoods.getConstraintsTouching(variable))) {
                        next.getStatistics().incrementBacktracks();
                        selector.onValueRejected(variable);
                        listener.onBacktrack(variable, next);
                        progress.complete(childWeight);
                        return false;
                    }
                    return true;
                })
                .flatMap(next -> {
                    Stream<Assignment> child;
                    try {
                        child = inferOrExplain(nogoodStore.apply(csp), variable, next, cutNarrowed, selector)
                                .map(inferred -> search(inferred, next, incumbent, deadline, childWeight, progress, selector, lpGate))
                                .orElseGet(Stream::empty);
                    } catch (SolverCancelledException e) {
                        child = Stream.empty();
                    }
                    // flatMap consumes each mapped stream fully and then closes it (try-with-resources
                    // in ReferencePipeline), so this fires exactly when this child's subtree is done.
                    return child.onClose(() -> progress.complete(childWeight));
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
     * <p>
     * {@code cutNarrowed} is declared to the {@link Inference} as already changed: {@link
     * #applyObjectiveCut} narrowed those domains outside any propagation pass, so they appear in no
     * diff the inference can take against the problem it is handed, and leaving them undeclared
     * means the bound narrows a domain that then wakes no propagator -- which is the whole mechanism
     * the cut exists for rather than a branch-cut predicate (ADR-0029).
     */
    private Optional<ConstraintSatisfactionProblem> inferOrExplain(ConstraintSatisfactionProblem cspWithNogoods,
                                                                     Variable<?> variable,
                                                                     Assignment next,
                                                                     Set<Variable<?>> cutNarrowed,
                                                                     AdaptiveVariableSelector selector) {
        ConsistencyResult inferred = inference.applyWithReason(cspWithNogoods, variable, next, cutNarrowed);
        if (inferred.isInfeasible()) {
            selector.onConflict(variable, next);
            selector.onValueRejected(variable);
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
