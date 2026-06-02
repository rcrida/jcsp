package io.github.rcrida.jcsp.consistency.cumulative;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CumulativeConsistencyTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void apply_noCumulativeConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(CumulativeConsistency.INSTANCE.apply(csp)).hasValue(csp);
    }

    @Test
    void apply_tightensStartDomain() {
        // x1 ∈ [0,1], x2 ∈ [0,3], d=2, r=2, limit=2
        // x1's compulsory part [1,2) puts P(1)=2, blocking x2 from starting at 0 or 1.
        // After timetabling: x2 domain → [2,3].
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(0, 1))
                .variableDomain(x2, IntRangeDomain.of(0, 3))
                .cumulativeConstraint(List.of(x1, x2), List.of(2, 2), List.of(2, 2), 2)
                .build();
        var result = CumulativeConsistency.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(x2)).hasValue(IntRangeDomain.of(2, 3));
    }

    @Test
    void apply_infeasible_returnsEmpty() {
        // Both tasks fixed to start=1 with d=2, r=1, limit=1: they must overlap → infeasible.
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(1, 1))
                .cumulativeConstraint(List.of(x1, x2), List.of(2, 2), List.of(1, 1), 1)
                .build();
        assertThat(CumulativeConsistency.INSTANCE.apply(csp)).isEmpty();
    }
}
