package io.github.rcrida.jcsp.solver;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * The one-time fixpoint-propagation preprocessing step in the solver decorator chain: runs {@link
 * #fixpointPropagation}'s propagator list to convergence before search starts, then (in
 * satisfaction mode) resolves any {@link BoundedDomain} variable that's still non-singleton by
 * snapping it to its interval midpoint. {@link #fixpointPropagation} defaults to {@link
 * FixpointPropagation#FULL} but {@link Solver.Factory} overrides it per solve with the filtered
 * instance from {@link FixpointPropagation.Factory#forProblem}.
 *
 * <p>When {@link #snap} is true (satisfaction mode with {@link BoundedDomain} variables), any
 * non-singleton bounded domain remaining after propagation is snapped to its interval midpoint,
 * giving one concrete solution for underdetermined continuous systems. When {@link #snap} is false
 * (optimization mode), intervals are left open so that a downstream {@link BisectionConditioningSolver}
 * can explore the feasible region.
 *
 * <p>The propagation algorithm itself -- the propagator list and the fixpoint loop that runs it to
 * convergence -- lives in {@link FixpointPropagation}, not here: that logic is also called from
 * the per-solve {@link io.github.rcrida.jcsp.consistency.Inference} built by {@link
 * Solver.Factory#propagationInference} (per search node) and
 * {@link SetBranchingSolver#repropagate} (per branch step), neither of which has an instance of
 * this class to call it on. This class only owns the parts specific to being a chain preprocessing
 * step: {@link #snap} and the convergence loop that layers bounded-domain snapping on top of a
 * plain {@link FixpointPropagation#applyFixpoint} call.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PropagationFixpointSolver extends SolverDecorator {

    /** When true, snaps non-singleton bounded domains to midpoints after propagation converges. */
    boolean snap;
    @Builder.Default @NonNull SolverListener listener = SolverListener.NONE;
    @Builder.Default @NonNull Statistics statistics = new Statistics();
    @Builder.Default @NonNull Cancellation cancellation = Cancellation.NEVER;
    @Builder.Default @NonNull FixpointPropagation fixpointPropagation = FixpointPropagation.FULL;

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        log.debug("preprocess");
        return runFixpoint(csp);
    }

    /**
     * Lets {@link SolverCancelledException} out of {@link FixpointPropagation#applyFixpoint} rather
     * than converting it to {@link Optional#empty()}, which is what it used to do.
     * <p>
     * Empty from here means this pass <em>proved</em> the problem infeasible, and a cancelled
     * preprocess has proved nothing, so the two cannot share a return value: a caller inferring
     * UNSAT from an empty result would report a refutation that never happened, and {@code
     * Xcsp3ProblemRunner} did exactly that. The silence {@link BoundSolver#getSolutions()} promises
     * is applied by {@link SolverDecorator#getSolutions}, the one place that owns that contract, so
     * it no longer has to be baked in here where the single-solution path shares the code. See
     * <a href="../../../../../../../docs/adr/0043-inconclusive-is-not-unsatisfiable.md">ADR-0043</a>.
     */
    private @NonNull Optional<ConstraintSatisfactionProblem> runFixpoint(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            Optional<ConstraintSatisfactionProblem> result =
                    fixpointPropagation.applyFixpoint(current, null, listener, statistics, cancellation);
            if (result.isEmpty()) return Optional.empty();
            changed = FixpointPropagation.domainSum(result.get()) < FixpointPropagation.domainSum(current);
            current = result.get();
            if (!changed && snap) {
                var snapTarget = BisectionConditioningSolver.findWidestBounded(current);
                if (snapTarget != null) {
                    BoundedDomain<?> bd = (BoundedDomain<?>) current.getDomain(snapTarget);
                    double mid = (bd.getMin().doubleValue() + bd.getMax().doubleValue()) / 2.0;
                    current = BisectionConditioningSolver.withSnapped(current, snapTarget, mid);
                    changed = true;
                }
            }
        }
        log.debug("PropagationFixpoint converged; domain-sum={}", FixpointPropagation.domainSum(current));
        statistics.updateRootSearchSpace(current.getSearchSpace());
        return Optional.of(current);
    }
}
