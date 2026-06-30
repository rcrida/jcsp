package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MacAndFixpointConflictExplainerTest {

    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void explain_macFails_returnsAssignmentValues() {
        // y's only possible value is 1, but notEquals(x,y) forbids y=1 once x=1.
        // MAC assigns x=1: the notEquals arc wipes y's domain entirely.
        // postMac.isEmpty()=true → returns assignment.getValues() unchanged.
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        var result = MacAndFixpointConflictExplainer.INSTANCE.explain(csp, x, assignment);
        assertThat(result).isEqualTo(assignment.getValues());
    }

    @Test
    void explain_macSucceedsAndNoConflictFound_returnsAssignmentValues() {
        // MAC assigns x=1: notEquals(x,y) prunes y to {2}. No constraint fails.
        // reason.isEmpty()=true → returns assignment.getValues() unchanged.
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .build();
        var assignment = Assignment.of(Map.of(x, 1));
        var result = MacAndFixpointConflictExplainer.INSTANCE.explain(csp, x, assignment);
        assertThat(result).isEqualTo(assignment.getValues());
    }

    @Test
    void explain_macSucceedsButAllDiffHallViolation_returnsReason() {
        // x=3 causes notEquals(x,a/b/c) to narrow a,b,c from {1,2,3} to {1,2}.
        // With a=b=c={1,2} after MAC, pairwise AC3 finds every value supported (e.g. a=1
        // is supported by b=2) so AC3 does NOT detect the infeasibility. AllDiff GAC detects
        // the Hall violation (3 variables confined to 2 values) via propagateWithReasons,
        // which returns a non-empty reason map.
        // reason.isEmpty()=false → returns the reason instead of the assignment.
        Variable<Integer> x = F.create("x");
        Variable<Integer> a = F.create("a"), b = F.create("b"), c = F.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(3, 4))
                .variableDomain(a, IntRangeDomain.of(1, 3))
                .variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(x, a)
                .notEqualsConstraint(x, b)
                .notEqualsConstraint(x, c)
                .allDiffConstraint(Set.of(a, b, c))
                .build();
        var assignment = Assignment.of(Map.of(x, 3));
        var result = MacAndFixpointConflictExplainer.INSTANCE.explain(csp, x, assignment);
        assertThat(result).isNotEqualTo(assignment.getValues());
        assertThat(result).isNotEmpty();
    }
}
