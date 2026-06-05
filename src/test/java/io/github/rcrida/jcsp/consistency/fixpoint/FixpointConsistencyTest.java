package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FixpointConsistencyTest {

    @Test
    void apply_noMatchingConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(FixpointConsistency.of(SumConstraint.class).apply(csp)).hasValue(csp);
    }
}
