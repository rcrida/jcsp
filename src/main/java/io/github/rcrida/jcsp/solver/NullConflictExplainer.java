package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;

/**
 * A {@link ConflictExplainer} that never explains anything, disabling nogood learning (CDCL)
 * entirely: {@code DomWdegLubySearch} only records a nogood when {@link #explain} returns a
 * present value, so this keeps {@code NogoodStore} permanently empty -- with it, {@code
 * NogoodStore#apply} always short-circuits on an empty set and {@code NogoodFixpointConsistency}
 * never has anything to scan. Unlike capping {@code NogoodStore} to a small size (which still
 * pays the cost of re-running MAC and the propagation fixpoint with reason tracking on every
 * domain wipeout via {@link MacAndFixpointConflictExplainer}, only to immediately evict the
 * result), this skips that computation altogether -- the only way to eliminate CDCL's per-wipeout
 * cost, not just its accumulation.
 * <p>
 * Does not affect dom/wdeg variable-ordering weight updates ({@code
 * DomWdegVariableSelector#incrementWeights}), which are a separate heuristic (Boussemart et al.
 * 2004) applied independently of nogood learning.
 * <p>
 * Worth using when learned nogoods rarely get reused for a given problem shape -- e.g. searches
 * that backtrack rarely, or where the same conflict essentially never recurs -- so the
 * explanation cost would be pure overhead with no compensating pruning benefit.
 */
public final class NullConflictExplainer implements ConflictExplainer {
    public static final NullConflictExplainer INSTANCE = new NullConflictExplainer();

    private NullConflictExplainer() {
    }

    @Override
    public Optional<NogoodConstraint> explain(ConstraintSatisfactionProblem csp, Variable<?> variable, Assignment assignment) {
        return Optional.empty();
    }
}
