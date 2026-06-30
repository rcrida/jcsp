package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FixpointConsistencyTest {

    @Test
    void apply_noMatchingConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(FixpointConsistency.of(SumConstraint.class).apply(csp)).hasValue(csp);
    }

    @Test
    void explainConflict_noMatchingConstraints_returnsEmpty() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(FixpointConsistency.of(SumConstraint.class).explainConflict(csp)).isEmpty();
    }

    @Test
    void explainConflict_feasibleConstraintWithUpdates_returnsEmpty() {
        // SumConstraint(x+y≤3) with x,y∈{1..5}: first pass narrows domains (updates non-empty →
        // changed=true branch), second pass makes no further progress → while exits → Optional.empty()
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumConstraint.class).explainConflict(csp)).isEmpty();
    }
}
