package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.Nullable;

import java.util.function.ToDoubleFunction;

/**
 * The result of parsing one XCSP3 file: the {@link ConstraintSatisfactionProblem} itself, plus an
 * optional {@link #objective} for COP (optimization) instances. A plain CSP instance has a
 * {@code null} objective; callers should branch on that to choose between {@link
 * io.github.rcrida.jcsp.solver.Solver.Factory#createSolver(ConstraintSatisfactionProblem)} and
 * {@link io.github.rcrida.jcsp.solver.Solver.Factory#createSolver(ConstraintSatisfactionProblem,
 * ToDoubleFunction)}.
 * <p>
 * {@link #objective}, when present, is already oriented for minimization -- an XCSP3 {@code
 * <maximize>} objective is negated by the parser so every {@link Xcsp3Instance} can be handed
 * directly to {@code createSolver(csp, objective)}, which always minimizes. {@link #maximize}
 * records the original sense purely so a caller can report the solved objective value back in
 * XCSP3's own terms (negate it again if {@link #maximize} is {@code true}).
 */
public record Xcsp3Instance(ConstraintSatisfactionProblem csp, @Nullable ToDoubleFunction<Assignment> objective,
                             boolean maximize) {
}
