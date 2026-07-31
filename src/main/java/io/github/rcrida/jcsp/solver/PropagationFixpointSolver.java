package io.github.rcrida.jcsp.solver;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * The one-time fixpoint-propagation preprocessing step in the solver decorator chain: runs
 * {@link FixpointPropagation}'s full propagator list to convergence before search starts, then (in
 * satisfaction mode) resolves any {@link BoundedDomain} variable that's still non-singleton by
 * snapping it to its interval midpoint.
 *
 * <p>When {@link #snap} is true (satisfaction mode with {@link BoundedDomain} variables), any
 * non-singleton bounded domain remaining after propagation is snapped to its interval midpoint,
 * giving one concrete solution for underdetermined continuous systems. When {@link #snap} is false
 * (optimization mode), intervals are left open so that a downstream {@link BisectionConditioningSolver}
 * can explore the feasible region.
 *
 * <p>The propagation algorithm itself -- the propagator list and the fixpoint loop that runs it to
 * convergence -- lives in {@link FixpointPropagation}, not here: that logic is also called from
 * {@link Solver.Factory#FULL_PROPAGATION_INFERENCE} (per search node) and
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

    @Override
    protected @NonNull Optional<ConstraintSatisfactionProblem> preprocess(
            @NonNull ConstraintSatisfactionProblem csp) {
        log.debug("preprocess");
        return runFixpoint(csp);
    }

    private @NonNull Optional<ConstraintSatisfactionProblem> runFixpoint(
            @NonNull ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            var result = FixpointPropagation.applyFixpoint(current, null, listener);
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
        return Optional.of(current);
    }
}
