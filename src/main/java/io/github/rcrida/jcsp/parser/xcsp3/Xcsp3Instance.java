package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.Nullable;

import java.util.Set;
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
 * <p>
 * {@link #declaredVariableNames}, in original {@code <variables>} declaration order, is exactly
 * the set of variable names the source XCSP3 file itself declared -- excluding every synthetic
 * variable {@link Xcsp3CallbackHandler} creates internally (reification indicators, {@code
 * shiftVariable}/{@code booleanIndicatorFor} bridges, {@code addIff}'s fresh indicators, the
 * {@code $max} objective auxiliary, etc.). A tool re-emitting a solution in XCSP3's own {@code
 * <instantiation>} format (e.g. for {@code org.xcsp.parser.callbacks.SolutionChecker}) must
 * restrict itself to this set -- a synthetic variable's name has no counterpart in the original
 * file for the checker to resolve.
 * <p>
 * {@link #declaredConstraintCount} is {@link #declaredVariableNames}'s constraint-side sibling: how
 * many {@code <...>} constraint elements the source file itself specifies, counted post {@code
 * group}/{@code slide} expansion (one XCSP3 constraint per expanded instance, not one per
 * templating element -- see {@link Xcsp3CallbackHandler#loadCtr}), but before any of {@link
 * Xcsp3CallbackHandler}'s own synthetic constraints (reification bridges, {@code addIff}'s extra
 * equality, etc.). Unlike {@link #declaredVariableNames}, this is a plain count, not a name set --
 * one XCSP3 constraint doesn't correspond 1:1 with one built {@link
 * io.github.rcrida.jcsp.constraints.Constraint} object even before any synthetic ones are added
 * (e.g. {@code allDifferentMatrix} decomposes into several {@code AllDiffConstraint}s), so there's
 * no stable per-constraint identity to track the way there is for a variable's own name.
 */
public record Xcsp3Instance(ConstraintSatisfactionProblem csp, @Nullable ToDoubleFunction<Assignment> objective,
                             boolean maximize, Set<String> declaredVariableNames, int declaredConstraintCount) {
}
