package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.lp.LpBound;
import io.github.rcrida.jcsp.solver.lp.LpModelBuilder;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * An {@link IncumbentSeeder} that finds a feasible solution and then tries to beat it, by asking the
 * same search a <em>bounded</em> question: "is there a solution costing at most {@code target}?".
 *
 * <p>The bound is an {@link ObjectiveCut}, so it is propagated rather than merely checked, which is
 * what makes a tighter question often an easier one: on {@code Taillard-js-015-15-0}, bounding the
 * makespan at 1281 finds a solution in 3.1s where the unbounded search takes 4.4s to find its 1330,
 * and bounds at or below 1146 are refuted by propagation alone without visiting a single node.
 *
 * <p>The schedule is a descent, deliberately not a bisection. Between the costs a bounded search can
 * satisfy and those it can refute lies a band where it can do neither within any sane budget --
 * measured at roughly 1220 to 1274 on that instance, straddling the optimum -- and a bisection aims
 * its first probe squarely into the middle of it, learning nothing and spending everything. Stepping
 * down by {@link #stepDivisor}ths of the remaining gap keeps the early probes in the region that
 * answers cheaply, and every answer shrinks the gap for the next one.
 *
 * <p>Which answer came back is what drives the descent, and being able to tell two of them apart is
 * what <a href="../../../../../../../docs/adr/0043-inconclusive-is-not-unsatisfiable.md">ADR-0043</a>
 * exists to provide:
 * <ul>
 *   <li><b>Satisfiable</b> -- adopt it. Its real cost may be well below the target, so the next step is
 *       measured from the cost rather than from the target.</li>
 *   <li><b>Proven unsatisfiable</b> -- raise the lower bound, shrinking every later step. Used only to
 *       aim probes, never to prune, so a wrong proof here could waste work but could not change the
 *       answer.</li>
 *   <li><b>Neither</b> -- stop. The band has no bottom worth hunting for and the budget is better spent
 *       on the real search.</li>
 * </ul>
 *
 * See <a href="../../../../../../../docs/adr/0044-bounded-probes-for-the-starting-incumbent.md">ADR-0044</a>.
 */
@Slf4j
@Value
@Builder
public class BoundedFirstSolution implements IncumbentSeeder {
    /**
     * Builds a search for one feasible solution, given the problem it will be asked about, a restart
     * budget, and this search's index within the solve. The budget because the two uses want very
     * different ones -- see {@link #initialRestartBudget} and {@link #probeRestartBudget}. The index
     * because each search must draw its tie-breaking from its own stream rather than from wherever the
     * previous one left a shared driver, which is what {@link RestartRandomization#forSearch}
     * supplies; the first solution is search 0 and each probe is the next.
     */
    @NonNull SearchFactory search;

    /**
     * {@link #search}'s shape: the problem to be asked about, a restart budget and a search index in,
     * one search out.
     *
     * <p>{@code csp} is the same problem the returned {@link Solver} is then asked about, and is
     * passed here as well because a search's <em>configuration</em> can depend on which constraint
     * types the problem has -- {@link FixpointPropagation.Factory#forProblem} filters the propagator
     * list on exactly that. A probe's problem carries an {@link ObjectiveCut} the original does not,
     * so a factory that configured itself from the original problem would build a search that cannot
     * propagate the very bound the probe exists to ask about.
     */
    @FunctionalInterface
    public interface SearchFactory {
        @NonNull Solver create(@NonNull ConstraintSatisfactionProblem csp, int restartBudget, int searchIndex);
    }

    // No @Builder.Default — defaults are set in BoundedFirstSolutionBuilder below, which validates
    // them, following DomWdegLubySearch's own knobs.

    /**
     * Restart budget for the unbounded search that finds the first solution, where the job is to find
     * one at all and giving up early buys nothing.
     */
    int initialRestartBudget;

    /**
     * Restart budget for each bounded probe, far smaller than {@link #initialRestartBudget} because a
     * probe that cannot be answered must hand the budget back rather than consume the solve. It bounds
     * a probe's cost in <em>restarts</em>, which is only loosely a bound on its time: the default of
     * 32 cut an unanswerable probe off after 9.5s on {@code Taillard-js-015-15-0}.
     * <p>
     * The default is known to be slightly ungenerous. That same instance's bounded question is
     * answerable at 8,764 nodes, and 32 restarts reaches 8,522 before giving up -- three percent
     * short. Raising it would collect answers like that one while lingering longer on the probes that
     * have none, a trade ADR-0044 records as unmeasured.
     */
    int probeRestartBudget;

    /** Bounded probes to run before handing the rest of the budget to branch-and-bound. */
    int maxProbes;

    /**
     * Each probe targets a cost this fraction of the way from the incumbent down towards the best
     * known lower bound. Small on purpose: the first probe is the one most likely to land in the band
     * where nothing can be answered, and a near miss there ends the descent having learned nothing.
     */
    int stepDivisor;

    /**
     * Reused across every probe of one {@link #seed} call, so the cut constraint is rebuilt only as
     * the bound actually moves. Same field conventions as {@link BranchAndBoundSolver}'s own caches:
     * a direct initializer makes it invisible to the generated builder (Lombok skips initialized final
     * fields), so every instance gets its own, and it is excluded from this value class's identity so
     * per-instance scratch state cannot affect equality.
     */
    @EqualsAndHashCode.Exclude @ToString.Exclude @Getter(AccessLevel.NONE)
    ObjectiveCut cut = new ObjectiveCut();

    /**
     * Identity token for {@link LpModelBuilder}'s per-owner model reuse; see {@link
     * LpModelBuilder#solve(ConstraintSatisfactionProblem, LinearObjective, Object)}. Same field
     * conventions as {@link #cut}.
     */
    @EqualsAndHashCode.Exclude @ToString.Exclude @Getter(AccessLevel.NONE)
    Object lpModelCacheKey = new Object();

    /** Partial builder: sets defaults and validates preconditions in {@link #build}. */
    public static class BoundedFirstSolutionBuilder {
        private int initialRestartBudget = DomWdegLubySearch.DEFAULT_MAX_RESTARTS;
        private int probeRestartBudget = 32;
        private int maxProbes = 8;
        private int stepDivisor = 8;

        public BoundedFirstSolution build() {
            if (initialRestartBudget <= 0) {
                throw new IllegalArgumentException("initialRestartBudget must be positive, got: " + initialRestartBudget);
            }
            if (probeRestartBudget <= 0) {
                throw new IllegalArgumentException("probeRestartBudget must be positive, got: " + probeRestartBudget);
            }
            if (maxProbes < 0) {
                throw new IllegalArgumentException("maxProbes must not be negative, got: " + maxProbes);
            }
            if (stepDivisor <= 0) {
                throw new IllegalArgumentException("stepDivisor must be positive, got: " + stepDivisor);
            }
            return new BoundedFirstSolution(search, initialRestartBudget, probeRestartBudget, maxProbes, stepDivisor);
        }
    }

    @Override
    public @NonNull Optional<Assignment> seed(@NonNull ConstraintSatisfactionProblem csp,
                                              @NonNull ToDoubleFunction<Assignment> objective) {
        Assignment first = ask(csp, search.create(csp, initialRestartBudget, 0)).solution();
        if (first == null) {
            return Optional.empty();
        }
        return Optional.of(objective instanceof LinearObjective linear ? descend(csp, linear, first) : first);
    }

    /**
     * Probes downwards from {@code first}, returning the cheapest solution found. Declines to probe at
     * all unless the bound is expressible as an exact cut -- without one a "bounded" probe would
     * re-ask the question just answered -- and unless the LP relaxation offers a lower bound for the
     * steps to be measured against.
     */
    private Assignment descend(ConstraintSatisfactionProblem csp, LinearObjective objective, Assignment first) {
        Assignment best = first;
        double bestCost = objective.applyAsDouble(best);
        if (cut.constraintFor(objective, bestCost, csp) == null) {
            return best;
        }
        // No relaxation means no lower bound to measure steps against, which is the same situation as
        // a bound already equal to the incumbent: nothing to descend into. An LP relaxation of a
        // problem that has a solution is feasible, so this is a formality rather than a real case.
        double lower = LpModelBuilder.solve(csp, objective, lpModelCacheKey)
                .map(LpBound::lowerBound)
                .orElse(bestCost);
        log.debug("Descending from a first solution costing {}, towards a lower bound of {}", bestCost, lower);
        for (int i = 0; i < maxProbes && bestCost > lower; i++) {
            double target = bestCost - Math.max(1.0, Math.ceil((bestCost - lower) / stepDivisor));
            if (target < lower) {
                break;
            }
            log.debug("Probe {}: is there a solution costing at most {}?", i, target);
            // Cost at most target is cost strictly better than target + 1. Added as a constraint
            // rather than narrowed into the domains: see ObjectiveCut#enforce for why a bound spread
            // across many objective variables is invisible to a search that only sees what one
            // root-level pass of it narrowed.
            ConstraintSatisfactionProblem bounded = cut.enforce(csp, objective, target + 1);
            // Built inside the loop, not once outside it: each probe is its own search over its own
            // bounded problem and takes its own index, so that probe 3 asks its question the same way
            // wherever the descent reached it. Rebuilding the chain costs a constraint-graph build
            // (enforce changed the constraint set) and one propagator-list filter, both once per
            // probe against a search measured in seconds.
            Answer answer = ask(bounded, search.create(bounded, probeRestartBudget, i + 1));
            if (answer.solution() != null) {
                double cost = objective.applyAsDouble(answer.solution());
                // Necessarily an improvement, so there is no "no better than what we hold" case to
                // check for: the cut is a constraint of `bounded` and `ask` returns only a solution
                // consistent with it, which puts the cost at or below target and so strictly below
                // bestCost. An answer that ignores its bound is rejected by that validation and
                // arrives here as neither a solution nor a refutation. This was a real case while the
                // bound was only narrowed into the domains, where a thin one narrowed nothing and the
                // probe answered the unbounded question instead -- see ObjectiveCut#enforce.
                log.debug("Bounded probe at {} improved the incumbent from {} to {}", target, bestCost, cost);
                best = answer.solution();
                bestCost = cost;
            } else if (answer.refuted()) {
                lower = target + 1;
            } else {
                break;
            }
        }
        return best;
    }

    /**
     * One feasibility question's three possible answers: a {@code solution}, or {@code refuted} when
     * the search completed and proved there is none, or neither when it stopped early.
     */
    private record Answer(@Nullable Assignment solution, boolean refuted) {}

    /**
     * Asks {@code solver} whether {@code csp} has any solution.
     *
     * <p>{@link Solver#getSolution} rather than {@link Solver#getSolutions}, because for the
     * satisfaction chain only the former reaches {@link DomWdegLubySearch}'s restarts at all, which is
     * most of the reason for delegating to it. Its {@link Optional#empty()} is a proof that there is
     * no solution and every early stop throws instead (ADR-0043) -- exactly the distinction the
     * descent needs, and why this returns three states rather than an {@link Optional}.
     *
     * <p>A returned solution is validated: a solution of {@code csp} is feasible for the optimization
     * problem too, since an objective is not a constraint, but that is a property of the injected
     * search rather than of anything checked here. One that fails validation is reported as neither a
     * solution nor a refutation, because an unusable answer proves nothing either.
     */
    private static Answer ask(ConstraintSatisfactionProblem csp, Solver solver) {
        Optional<Assignment> found;
        try {
            found = solver.getSolution(csp);
        } catch (InconclusiveSearchException e) {
            log.debug("Feasibility search stopped early: {}", e.getClass().getSimpleName());
            return new Answer(null, false);
        }
        if (found.isEmpty()) {
            return new Answer(null, true);
        }
        Assignment candidate = found.get();
        return candidate.isComplete(csp) && candidate.isConsistent(csp)
                ? new Answer(candidate, false)
                : new Answer(null, false);
    }
}
