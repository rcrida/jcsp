package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    /** A minimal Inference that only implements {@code apply}, relying on the interface's default
     * {@code applyWithReason} entirely -- exercises that default directly, since every production
     * implementation ({@code MAC}, {@code Solver.Factory#FULL_PROPAGATION_INFERENCE}) overrides it. */
    private static final Inference NO_OP = (problem, variable, assignment) -> Optional.of(problem);
    private static final Inference ALWAYS_FAILS = (problem, variable, assignment) -> Optional.empty();

    @Test
    void applyWithReason_defaultDelegatesToApply_feasible() {
        Variable<Integer> x = VF.create("x");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = NO_OP.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.problem()).isEqualTo(csp);
    }

    @Test
    void seededApply_defaultIgnoresTheCallerNarrowedVariables() {
        // Correct for an implementation that seeds nothing: it re-derives everything regardless, so
        // there is nothing for alsoChanged to wake. One that does seed must override this.
        Variable<Integer> x = VF.create("seededApplyX");
        Variable<Integer> y = VF.create("seededApplyY");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .build();
        var assignment = Assignment.of(Map.of(x, 1));

        assertThat(NO_OP.apply(csp, x, assignment, Set.of(y))).contains(csp);
    }

    @Test
    void seededApplyWithReason_defaultDelegatesToTheUnseededVariant() {
        Variable<Integer> x = VF.create("seededReasonX");
        Variable<Integer> y = VF.create("seededReasonY");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .build();
        var assignment = Assignment.of(Map.of(x, 1));

        ConsistencyResult result = ALWAYS_FAILS.applyWithReason(csp, x, assignment, Set.of(y));

        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(assignment.getValues()));
    }

    @Test
    void applyWithReason_defaultFallsBackToFullAssignment_infeasible() {
        Variable<Integer> x = VF.create("x");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        var assignment = Assignment.of(Map.of(x, 1));
        ConsistencyResult result = ALWAYS_FAILS.applyWithReason(csp, x, assignment);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(assignment.getValues()));
    }
}
