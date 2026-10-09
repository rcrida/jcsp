package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ObjectiveCut#enforce}, the apply-once-per-search half of the cut. {@link ObjectiveCut#narrow}
 * is exercised through {@link BranchAndBoundSolver}, which is its only caller.
 */
public class ObjectiveCutTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("cutX");
    static final Variable<Integer> Y = F.create("cutY");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(0, 9))
            .variableDomain(Y, IntRangeDomain.of(0, 9))
            .build();

    static final LinearObjective SUM = LinearObjective.builder()
            .coefficient(X, 1.0).coefficient(Y, 1.0).build();

    @Test
    void enforce_addsTheCutAsAConstraintAndLeavesEveryDomainAlone() {
        // The property the whole method exists for. A bound of 16 on x + y narrows neither domain --
        // either variable can still take 9 while the other takes 0 -- so a search handed only what one
        // propagation pass narrowed would be given back the unbounded problem and re-answer it. The
        // bound has to survive as a constraint to be re-propagated at every node, against the domains
        // that node has narrowed.
        val bounded = new ObjectiveCut().enforce(CSP, SUM, 16.0);

        assertThat(bounded.getVariableDomains())
                .as("a thin bound narrows nothing, which is exactly why narrowing is not enough")
                .isEqualTo(CSP.getVariableDomains());
        assertThat(bounded.getConstraints()).hasSize(1);
        val cut = (LinearBoundConstraint<?>) bounded.getConstraints().iterator().next();
        // Strictly better than 16, i.e. at most 15, since the objective is wholly integral.
        assertThat(cut.getOperator()).isEqualTo(Operator.LEQ);
        assertThat(cut.getBound()).isEqualTo(15);
        assertThat(cut.getVariables()).containsExactlyInAnyOrder(X, Y);
    }

    @Test
    void enforce_isPropagatedByAFixpointBuiltForTheResult() {
        // The second half of the fix, and the reason enforce's result must be what a FixpointPropagation
        // is built from: forProblem filters on the constraint types the problem has, so a cut added to a
        // problem that had no LinearBoundConstraint is propagated only by a list filtered for the cut
        // problem. Filtered for the original, the cut is in the constraint set and never looked at.
        val bounded = new ObjectiveCut().enforce(CSP, SUM, 6.0);

        val forOriginal = FixpointPropagation.Factory.INSTANCE.forProblem(CSP, false);
        val forBounded = FixpointPropagation.Factory.INSTANCE.forProblem(bounded, false);

        assertThat(fixpoint(forOriginal, bounded).getDomain(X))
                .as("filtered for a problem with no linear constraint, the cut is never propagated")
                .isEqualTo(CSP.getDomain(X));
        assertThat(fixpoint(forBounded, bounded).getDomain(X))
                .as("x + y <= 5 bounds each of them at 5")
                .isEqualTo(IntRangeDomain.of(0, 5));
    }

    static ConstraintSatisfactionProblem fixpoint(FixpointPropagation propagation,
                                                  ConstraintSatisfactionProblem csp) {
        return propagation.applyFixpoint(csp, null, io.github.rcrida.jcsp.solver.listener.SolverListener.NONE,
                new io.github.rcrida.jcsp.assignments.Statistics(), new Cancellation()).orElseThrow();
    }

    @Test
    void enforce_declinesAnInfiniteBound() {
        // What an as-yet-unknown incumbent looks like: no bound at all, so nothing to add.
        assertThat(new ObjectiveCut().enforce(CSP, SUM, Double.MAX_VALUE)).isSameAs(CSP);
    }

    @Test
    void enforce_declinesAnObjectiveWithNoExactCut() {
        // A fractional coefficient cannot be cut exactly, and an inexact cut would have to be loosened
        // by an epsilon to stay sound -- see ObjectiveCut#build.
        val halfSum = LinearObjective.builder().coefficient(X, 0.5).coefficient(Y, 0.5).build();

        assertThat(new ObjectiveCut().enforce(CSP, halfSum, 16.0)).isSameAs(CSP);
    }
}
