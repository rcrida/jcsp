package io.github.rcrida.jcsp.solver;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.solver.listener.PropagationListener;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DivisionConstraint;
import io.github.rcrida.jcsp.constraints.binary.SubsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.DisjointConstraint;
import io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AllEqualConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.BinPackingConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.DiffnConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.AndConstraint;
import io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint;
import io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBooleanVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.MinConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.NValueConstraint;
import io.github.rcrida.jcsp.constraints.nary.PartitionConstraint;
import io.github.rcrida.jcsp.constraints.nary.ProductConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.RegularConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.constraints.unary.SetMembershipConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a list of propagators -- AC3, AllDiff GAC, SumBoundConstraint bounds propagation,
 * LinearBoundConstraint bounds propagation, CountConstraint value propagation, InverseConstraint
 * arc consistency, AmongConstraint value-set propagation, and so on -- in a combined fixpoint loop.
 *
 * <p>The propagators are not independent: AllDiff GAC can expose naked pairs that AC3 then
 * propagates to neighbouring constraints; sum, linear, count, inverse, and among propagation
 * tightens domains that AC3 and AllDiff GAC can then exploit further. Running each once misses
 * this feedback. This iterates until none of the propagators makes further progress, or exits
 * immediately with {@link Optional#empty()} as soon as any propagator detects infeasibility.
 *
 * <p>An instance carries a fixed {@link #propagators} list, since which propagators can possibly
 * do anything depends on which constraint types a given {@link ConstraintSatisfactionProblem}
 * actually has -- {@link Factory#forProblem} computes that filtered list once per solve, called
 * from {@link Solver.Factory}. {@link #FULL} is the always-everything instance, backed by the
 * complete {@link #PROPAGATORS} catalog, used as the unfiltered fallback for direct/manual use and
 * by {@link Solver.Factory#FULL_PROPAGATION_INFERENCE}.
 *
 * <p>To add a new propagator for a {@link io.github.rcrida.jcsp.consistency.Propagatable} constraint
 * type, append {@code FixpointConsistency.of(MyConstraint.class)} to {@link #PROPAGATORS}: it is
 * automatically picked up both by {@link #FULL} and by {@link Factory#forProblem}'s filtering, with
 * no second place to update.
 *
 * <p>Not a free-standing static utility, despite being called from three structurally different
 * places -- {@link PropagationFixpointSolver}'s own one-time preprocessing pass (before search
 * starts), the per-solve {@link io.github.rcrida.jcsp.consistency.Inference} built in {@link
 * Solver.Factory} (applied at every search node), and {@link SetBranchingSolver}'s branch
 * re-propagation for set-domain variables (a different class entirely) -- because each of those
 * three needs the same solve's filtered {@link #propagators} list, not the full catalog; each holds
 * (or is handed) a {@link FixpointPropagation} instance to call.
 *
 * <p>Both {@link #applyFixpoint}/{@link #applyFixpointWithReason} check a caller-supplied {@link
 * Cancellation} token once per propagator within the round -- "between propagators" -- since a
 * single round runs every entry in {@link #propagators} in sequence and this loop is itself the hot
 * path invoked at every search node, every {@link SetBranchingSolver} branch step, and once for
 * whole-CSP preprocessing. See each method's own Javadoc for the exact caller obligation this
 * creates.
 */
@Slf4j
@Value
@Builder
public class FixpointPropagation {

    /**
     * Defaults to {@link #PROPAGATORS} (the full catalog) rather than an empty list, so an
     * accidental {@code FixpointPropagation.builder().build()} with no explicit {@code propagators}
     * still runs every propagator instead of silently becoming a total no-op that looks like
     * successful propagation. {@link #FULL} and {@link Factory#forProblem} both always set this
     * explicitly, so the default only matters for a caller that builds one directly without either.
     */
    @Builder.Default @NonNull List<ConstraintConsistency> propagators = PROPAGATORS;

    public static final List<ConstraintConsistency> PROPAGATORS = List.of(
            FixpointConsistency.of(UnaryComparatorConstraint.class),
            FixpointConsistency.of(BinaryComparatorConstraint.class),
            FixpointConsistency.of(BinaryOffsetConstraint.class),
            FixpointConsistency.of(AbsoluteDifferenceConstraint.class),
            AC3.INSTANCE,
            NogoodFixpointConsistency.INSTANCE,
            FixpointConsistency.of(AllDiffConstraint.class),
            FixpointConsistency.of(AllEqualConstraint.class),
            FixpointConsistency.of(SumBoundConstraint.class),
            FixpointConsistency.of(SumVariableConstraint.class),
            FixpointConsistency.of(LinearBoundConstraint.class),
            FixpointConsistency.of(LinearVariableConstraint.class),
            FixpointConsistency.of(LinearBooleanBoundConstraint.class),
            FixpointConsistency.of(LinearBooleanVariableConstraint.class),
            FixpointConsistency.of(CountConstraint.class),
            FixpointConsistency.of(CountVariableConstraint.class),
            FixpointConsistency.of(InverseConstraint.class),
            FixpointConsistency.of(AmongConstraint.class),
            FixpointConsistency.of(AmongVariableConstraint.class),
            FixpointConsistency.of(AtLeastNConstraint.class),
            FixpointConsistency.of(AtMostNConstraint.class),
            FixpointConsistency.of(AtMostOneConstraint.class),
            FixpointConsistency.of(BinPackingConstraint.class),
            FixpointConsistency.of(CumulativeConstraint.class),
            FixpointConsistency.of(GlobalCardinalityConstraint.class),
            FixpointConsistency.of(NValueConstraint.class),
            FixpointConsistency.of(LexConstraint.class),
            FixpointConsistency.of(MaxConstraint.class),
            FixpointConsistency.of(MaxVariableConstraint.class),
            FixpointConsistency.of(MinConstraint.class),
            FixpointConsistency.of(NaryElementConstraint.class),
            FixpointConsistency.of(NaryTuplesConstraint.class),
            FixpointConsistency.of(ProductConstraint.class),
            FixpointConsistency.of(DivisionConstraint.class),
            FixpointConsistency.of(CircuitConstraint.class),
            FixpointConsistency.of(DiffnConstraint.class),
            FixpointConsistency.of(RegularConstraint.class),
            FixpointConsistency.of(IncreasingConstraint.class),
            FixpointConsistency.of(DecreasingConstraint.class),
            FixpointConsistency.of(ReifiedConstraint.class),
            FixpointConsistency.of(ImplicationConstraint.class),
            FixpointConsistency.of(AndConstraint.class),
            FixpointConsistency.of(SubsetConstraint.class),
            FixpointConsistency.of(DisjointConstraint.class),
            FixpointConsistency.of(IntersectionCardinalityConstraint.class),
            FixpointConsistency.of(PartitionConstraint.class),
            FixpointConsistency.of(SetMembershipConstraint.class)
    );

    /** The always-everything instance, backed by the complete {@link #PROPAGATORS} catalog. */
    public static final FixpointPropagation FULL = FixpointPropagation.builder().propagators(PROPAGATORS).build();

    /**
     * Builds a {@link FixpointPropagation} containing only the {@link #PROPAGATORS} entries that
     * can possibly do anything for a given {@link ConstraintSatisfactionProblem} -- so the fixpoint
     * loop never even calls into a propagator whose constraint type isn't present, rather than
     * relying on that propagator's own internal empty-check to discover it has nothing to do.
     * {@link Solver.Factory} calls {@link #forProblem} once per solve, since which propagators
     * apply is a structural property of the CSP's constraint types that holds for the whole solve
     * (constraints are never added or removed during search, only domains and nogoods).
     */
    public interface Factory {
        Factory INSTANCE = new Factory() {
            @Override
            public FixpointPropagation forProblem(@NonNull ConstraintSatisfactionProblem csp,
                                                   boolean nogoodLearningEnabled) {
                return FixpointPropagation.builder()
                        .propagators(PROPAGATORS.stream()
                                .filter(propagator -> isApplicable(propagator, csp, nogoodLearningEnabled))
                                .toList())
                        .build();
            }
        };

        /**
         * @param nogoodLearningEnabled whether {@link NogoodFixpointConsistency#INSTANCE} should be
         *                              included when {@code csp} itself has no nogoods yet -- a
         *                              config-level decision ({@code
         *                              SolverConfig#isNogoodLearningEnabled()}), not re-derived from
         *                              current emptiness, since nogoods <em>learned</em> during the
         *                              solve accumulate over the course of a solve under this same
         *                              filtered list. This is independent of nogoods already present
         *                              on {@code csp} when this is called (via {@code
         *                              ConstraintSatisfactionProblem.Builder#nogood} or {@code
         *                              #withNogoods}): those are propagated regardless of this flag,
         *                              matching {@code nogoodLearningEnabled}'s documented meaning
         *                              ("disables CDCL", not "ignore nogoods already on the
         *                              problem") -- see {@link FixpointPropagation#isApplicable}.
         */
        FixpointPropagation forProblem(@NonNull ConstraintSatisfactionProblem csp, boolean nogoodLearningEnabled);
    }

    /**
     * {@link NogoodFixpointConsistency#INSTANCE} and {@link AC3#INSTANCE} are singletons checked by
     * identity; every other {@link #PROPAGATORS} entry is a {@link FixpointConsistency} targeting
     * one constraint type. {@code nogoodLearningEnabled} alone would incorrectly drop nogoods a
     * caller pre-seeded on {@code csp} before ever starting a solve with learning disabled, so
     * {@link NogoodFixpointConsistency}'s own applicability additionally checks {@code
     * csp.getNogoods()} directly -- included whenever learning is on <em>or</em> the problem already
     * carries nogoods to propagate. The final branch falls back to {@code true} ("assume
     * applicable") for anything that isn't one of the two singletons or a {@link
     * FixpointConsistency}, rather than casting unconditionally: {@link #PROPAGATORS} is a public
     * list documented as safe to append to, so a future non-{@link FixpointConsistency} entry must
     * fail open (included, harmless if actually irrelevant) instead of throwing. That fallback
     * branch is unreachable through {@link #PROPAGATORS} as it exists today -- every current entry
     * is provably one of the three kinds above -- so it's covered directly (with a fabricated
     * {@link ConstraintConsistency}) rather than chased through an integration scenario; package-private,
     * not {@code private}, so a same-package test can call it directly. Not on {@link Factory}
     * itself since interface members can only be {@code public} or {@code private}, neither of which
     * a same-package test could reach without either widening this to public API or losing direct
     * testability.
     */
    static boolean isApplicable(ConstraintConsistency propagator, ConstraintSatisfactionProblem csp,
                                 boolean nogoodLearningEnabled) {
        if (propagator == NogoodFixpointConsistency.INSTANCE) {
            return nogoodLearningEnabled || !csp.getNogoods().isEmpty();
        }
        if (propagator == AC3.INSTANCE) return !csp.getAllBinaryConstraints().isEmpty();
        return !(propagator instanceof FixpointConsistency fc) || fc.appliesTo(csp);
    }

    /**
     * Runs {@link #propagators} to a combined fixpoint without snapping bounded domains. Called by
     * the per-solve {@link io.github.rcrida.jcsp.consistency.Inference} {@link
     * io.github.rcrida.jcsp.solver.Solver.Factory#propagationInference} builds to apply {@link
     * #propagators} during backtracking search, not just as a preprocessing pass.
     * <p>
     * Tracks which variables' domains changed during the previous round and passes that set to
     * each propagator via {@link ConstraintConsistency#apply(ConstraintSatisfactionProblem, Set)},
     * so {@link NogoodFixpointConsistency} can skip re-checking nogoods that reference none of
     * them (see its javadoc). {@code initialSeed} is round 1's dirty-variable hint: {@link
     * io.github.rcrida.jcsp.solver.Solver.Factory#propagationInference} passes the diff between the pre- and post-MAC domains (plus any
     * variable of a newly-learned nogood, which must always be checked once) rather than {@code
     * null}, since at a search node this call's input is exactly the parent's already-converged
     * CSP -- nothing else could have changed. {@code null} (a top-level preprocessing call, or any
     * other caller with no such parent to diff against) falls back to a full first-round scan
     * exactly as before. {@code listener} receives {@link SolverListener#onPropagatorProgress}
     * events; every current caller already has a real listener in scope (a field or, at a search
     * node, {@link io.github.rcrida.jcsp.assignments.Assignment#listener()}), so pass
     * {@link SolverListener#NONE} explicitly rather than adding a no-listener overload just to
     * avoid doing so. {@code cancellation} is checked once per propagator (not just once per
     * round), throwing {@link SolverCancelledException} (built from {@code statistics}) the moment
     * it's detected -- every caller except {@link DomWdegLubySearch#searchOne} must catch this and
     * convert it to their own existing "stopped early" behavior, since only that one call path is
     * meant to surface {@link SolverCancelledException} to an external caller (see
     * {@code SolverConfig.getCancellation()}). Checked once up front too, before the round loop, so
     * a cancellation request is still observed even when {@link #propagators} is empty (a filtered
     * instance for a CSP with nothing to propagate) -- otherwise the per-propagator check inside the
     * loop would never run at all and an already-cancelled caller would silently get a full result.
     */
    public Optional<ConstraintSatisfactionProblem> applyFixpoint(
            @NonNull ConstraintSatisfactionProblem csp, @Nullable Set<Variable<?>> initialSeed,
            @NonNull SolverListener listener, @NonNull Statistics statistics, @NonNull Cancellation cancellation) {
        log.debug("applyFixpoint");
        if (cancellation.isCancelled()) throw new SolverCancelledException(statistics);
        var current = csp;
        Set<Variable<?>> changedVariables = initialSeed;
        boolean changed = true;
        while (changed) {
            Map<Variable<?>, Domain<?>> before = current.getVariableDomains();
            double domainSumBefore = domainSum(current);
            for (var propagator : propagators) {
                if (cancellation.isCancelled()) throw new SolverCancelledException(statistics);
                var beforePropagator = current;
                var after = propagator.apply(current, changedVariables);
                if (after.isEmpty()) return Optional.empty();
                current = after.get();
                logIfDomainSumReduced(propagator, beforePropagator, current, log.isDebugEnabled(), listener);
            }
            changed = domainSum(current) < domainSumBefore;
            changedVariables = changed ? changedVariables(before, current.getVariableDomains()) : null;
        }
        return Optional.of(current);
    }

    /**
     * Returns every variable whose domain in {@code after} differs from its domain in {@code
     * before} (added/removed variables cannot occur -- every propagator narrows an existing
     * domain, never changes the variable set). Public so {@link
     * io.github.rcrida.jcsp.solver.Solver.Factory#propagationInference} can reuse it to
     * compute {@code applyFixpoint}'s round-1 seed from the pre-/post-MAC domains.
     */
    public static Set<Variable<?>> changedVariables(Map<Variable<?>, Domain<?>> before,
                                                      Map<Variable<?>, Domain<?>> after) {
        Set<Variable<?>> result = new HashSet<>();
        for (var entry : after.entrySet()) {
            if (!Objects.equals(entry.getValue(), before.get(entry.getKey()))) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Single-pass combination of {@link #applyFixpoint} and the old separate {@code explainConflict}
     * traversal: identical loop and seeding as {@link #applyFixpoint} — each propagator's {@link
     * ConstraintConsistency#applyWithReason} costs the same as {@link ConstraintConsistency#apply}
     * on the feasible path (see each propagator's own {@code applyWithReason} override) — and only
     * the propagator that actually signals infeasibility contributes a reason, computed as part of
     * this same pass rather than a second, from-scratch, unseeded replay. {@code cancellation} is
     * checked the same way -- and with the same caller obligations, including the up-front check
     * before the round loop -- as in {@link #applyFixpoint}.
     */
    public ConsistencyResult applyFixpointWithReason(
            @NonNull ConstraintSatisfactionProblem csp, @Nullable Set<Variable<?>> initialSeed,
            @NonNull SolverListener listener, @NonNull Statistics statistics, @NonNull Cancellation cancellation) {
        log.debug("applyFixpointWithReason");
        if (cancellation.isCancelled()) throw new SolverCancelledException(statistics);
        var current = csp;
        Set<Variable<?>> changedVariables = initialSeed;
        boolean changed = true;
        while (changed) {
            Map<Variable<?>, Domain<?>> before = current.getVariableDomains();
            double domainSumBefore = domainSum(current);
            for (var propagator : propagators) {
                if (cancellation.isCancelled()) throw new SolverCancelledException(statistics);
                var beforePropagator = current;
                ConsistencyResult after = propagator.applyWithReason(current, changedVariables);
                if (after.isInfeasible()) return after;
                current = after.problem();
                logIfDomainSumReduced(propagator, beforePropagator, current, log.isDebugEnabled(), listener);
            }
            changed = domainSum(current) < domainSumBefore;
            changedVariables = changed ? changedVariables(before, current.getVariableDomains()) : null;
        }
        return ConsistencyResult.feasible(current);
    }

    /**
     * Logs, at debug level, which individual propagator narrowed domains within a fixpoint round —
     * {@link #applyFixpoint}/{@link #applyFixpointWithReason} only check {@link #domainSum} once per
     * whole round (all {@link #PROPAGATORS} applied in sequence), so without this, a round that made
     * progress gives no visibility into which specific propagator (of up to several dozen) was
     * responsible -- and, since a {@link SolverListener} was added, notifies it via
     * {@link SolverListener#onPropagatorProgress} too. {@code debugEnabled} is passed in (as
     * {@link #log}'s own {@code isDebugEnabled()} at the one real call site) rather than read
     * directly, so the extra {@link #domainSum} computation (two calls per propagator per round
     * instead of two per round total) is both skipped entirely at the default log level with no
     * listener registered, and independently testable without depending on the test process's
     * actual SLF4J level -- {@code slf4j-simple} resolves and caches a logger's level once, at
     * first use, so it can't be toggled per-test the way a mock could. The gate now also checks
     * {@code listener != SolverListener.NONE} (reference equality, not {@code instanceof}), so a
     * registered listener still receives progress events even when debug logging is off, at the
     * same computation cost. Package-private, not {@code private}, so a same-package test can
     * exercise every combination of the two gate conditions directly.
     */
    static void logIfDomainSumReduced(@NonNull ConstraintConsistency propagator,
                                       @NonNull ConstraintSatisfactionProblem before,
                                       @NonNull ConstraintSatisfactionProblem after,
                                       boolean debugEnabled,
                                       @NonNull SolverListener listener) {
        if (!debugEnabled && listener == SolverListener.NONE) return;
        SumComparison sums = notifyIfDomainSumReduced(propagator, before, after, listener);
        if (sums.reduced()) {
            log.debug("{} reduced domain-sum from {} to {}", propagator, sums.before(), sums.after());
        }
    }

    /** {@code before}/{@code after} {@link #domainSum} results, and whether it actually decreased. */
    record SumComparison(double before, double after) {
        boolean reduced() {
            return after < before;
        }
    }

    /**
     * Shared "compute {@link #domainSum} before/after, notify on decrease" idiom, factored out so
     * {@link #logIfDomainSumReduced} (this class's own fixpoint loop) and {@code
     * LocalSolver.Factory#applyPreprocessors} (its single-pass, non-fixpoint preprocessing) don't
     * each maintain their own copy of it. Takes {@link PropagationListener} -- the common supertype
     * of {@link SolverListener} and {@code LocalSolverListener} -- rather than either concrete type,
     * since both callers' own listener types differ but both only ever need {@link
     * PropagationListener#onPropagatorProgress} here. Returns the computed sums so a caller that
     * also wants to act on them (like {@link #logIfDomainSumReduced}'s debug log) doesn't need to
     * recompute {@link #domainSum} a second time. Deliberately doesn't include either caller's own
     * cheap-when-unregistered gate (their sentinel {@code NONE} constants aren't comparable through
     * the shared {@link PropagationListener} type) -- callers keep that check themselves.
     */
    static SumComparison notifyIfDomainSumReduced(@NonNull ConstraintConsistency propagator,
                                                   @NonNull ConstraintSatisfactionProblem before,
                                                   @NonNull ConstraintSatisfactionProblem after,
                                                   @NonNull PropagationListener listener) {
        double beforeSum = domainSum(before);
        double afterSum = domainSum(after);
        if (afterSum < beforeSum) {
            listener.onPropagatorProgress(propagator, before.getVariableDomains(), after.getVariableDomains(), beforeSum, afterSum);
        }
        return new SumComparison(beforeSum, afterSum);
    }

    /**
     * Sums each domain's "size" as a progress metric for fixpoint convergence: discrete domains
     * contribute their element count, {@link BoundedDomain} (e.g. {@link IntervalDomain})
     * contributes its width, and {@link SetBoundedDomain} contributes {@code |upperBound| -
     * |lowerBound|} (its own remaining-slack metric, mirroring {@link SetBranchingSolver}'s
     * {@code undeterminedCount}) — not {@link Domain#size()}, which for a {@link SetBoundedDomain}
     * is only ever {@code 1} (singleton) or {@code Integer.MAX_VALUE} (anything else), and so can't
     * distinguish a domain one bound-narrowing step away from resolved from one untouched by
     * propagation. That insensitivity went unnoticed while only {@link PropagationFixpointSolver}'s
     * own {@code runFixpoint} called {@link #applyFixpoint} (whose few propagation rounds before
     * handing off to {@link SetBranchingSolver} rarely narrow a set domain part-way without
     * resolving it outright), but became a real bottleneck once {@link SetBranchingSolver} started
     * calling {@link #applyFixpoint} directly at every branch step: a round that only narrowed set
     * bounds without reaching singleton looked identical to one that changed nothing, so the loop
     * exited a full fixpoint early, leaving weaker propagation per branch and a larger search tree.
     * <p>
     * Deliberately a sum, not the product that would actually measure the combined search space's
     * size ({@code domain1.size() * domain2.size() * ...}): every caller only ever compares two
     * successive rounds' totals ({@code changed = domainSum(after) < domainSum(before)}), so any
     * monotonic-in-each-domain progress signal works equally well, and a product over dozens to
     * hundreds of variables overflows {@code double} far sooner than a sum does — at that point
     * every comparison degrades to {@code Infinity < Infinity} ({@code false}), permanently hiding
     * real progress from the very check this method exists to drive. Package-private (not {@code
     * private}) so {@link PropagationFixpointSolver}'s own convergence loop can reuse the same
     * metric rather than duplicating it.
     */
    static double domainSum(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream()
                .mapToDouble(d -> {
                    if (d instanceof BoundedDomain<?> bd) return bd.getMax().doubleValue() - bd.getMin().doubleValue();
                    if (d instanceof SetBoundedDomain<?> sd) return sd.getUpperBound().size() - sd.getLowerBound().size();
                    return d.size();
                })
                .sum();
    }
}
