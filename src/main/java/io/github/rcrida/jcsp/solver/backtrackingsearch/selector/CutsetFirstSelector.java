package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * dom/wdeg that branches on a small cycle cutset first. When the constraint graph has a greedy
 * {@link FeedbackVertexSet} of at most {@link #MAX_CUTSET_FRACTION} of its variables, every pick
 * comes from that cutset, ranked by {@link DomWdegVariableSelector#selectAmong}, until all of it is
 * assigned; from then on, and throughout when there is no such cutset, this is exactly {@link
 * DomWdegVariableSelector}. Every event is forwarded to the one underlying selector, so weights and
 * last-conflict are shared across both phases.
 * <p>
 * See <a href="../../../../../../../../docs/adr/0066-branch-on-a-small-cycle-cutset-first.md">ADR-0066</a>
 * for why the cutset must be small for this to help.
 */
public class CutsetFirstSelector implements AdaptiveVariableSelector {

    /** The largest share of the graph's variables a cutset may hold for it to be branched on first. */
    static final double MAX_CUTSET_FRACTION = 0.10;

    private final DomWdegVariableSelector domWdeg;
    private final Set<Variable<?>> cutset;

    public CutsetFirstSelector(@NonNull Set<Constraint> constraints) {
        this.domWdeg = new DomWdegVariableSelector(constraints);
        Set<Variable<?>> variables = new HashSet<>();
        for (Constraint constraint : constraints) {
            if (!(constraint instanceof NogoodConstraint)) variables.addAll(constraint.getVariables());
        }
        int maxSize = (int) Math.floor(MAX_CUTSET_FRACTION * variables.size());
        this.cutset = FeedbackVertexSet.within(constraints, maxSize).orElse(Set.of());
    }

    /** The variables branched on first; empty when the graph has no cutset small enough. */
    Set<Variable<?>> cutset() {
        return cutset;
    }

    @Override
    public Variable<?> select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        return domWdeg.selectAmong(csp, assignment, cutset).orElseGet(() -> domWdeg.select(csp, assignment));
    }

    @Override
    public void onConflict(@NonNull Variable<?> variable, @NonNull Assignment nextAssignment) {
        domWdeg.onConflict(variable, nextAssignment);
    }

    @Override
    public void onValueRejected(@NonNull Variable<?> variable) {
        domWdeg.onValueRejected(variable);
    }

    @Override
    public void onRestart(@Nullable Random tieBreak) {
        domWdeg.onRestart(tieBreak);
    }

    @Override
    public void onStagnation() {
        domWdeg.onStagnation();
    }
}
