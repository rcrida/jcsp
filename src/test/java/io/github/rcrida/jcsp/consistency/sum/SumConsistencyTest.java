package io.github.rcrida.jcsp.consistency.sum;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SumConsistencyTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void apply_noSumConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(SumConsistency.INSTANCE.apply(csp)).hasValue(csp);
    }

    @Test
    void apply_tightensDomainsViaEq() {
        // v1∈{1..9}, v2={8}, v3={6}, sum=15 → v1 forced to 1
        Variable<Integer> v1 = F.create("v1"), v2 = F.create("v2"), v3 = F.create("v3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 9))
                .variableDomain(v2, IntRangeDomain.of(8, 8))
                .variableDomain(v3, IntRangeDomain.of(6, 6))
                .sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 15)
                .build();
        var result = SumConsistency.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(v1)).hasValue(IntRangeDomain.of(1, 1));
    }

    @Test
    void apply_infeasible_returnsEmpty() {
        // min sum = 10, bound = 8 for LEQ → infeasible
        Variable<Integer> v1 = F.create("v1"), v2 = F.create("v2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(5, 9))
                .variableDomain(v2, IntRangeDomain.of(5, 9))
                .sumConstraint(Set.of(v1, v2), Operator.LEQ, 8)
                .build();
        assertThat(SumConsistency.INSTANCE.apply(csp)).isEmpty();
    }
}
