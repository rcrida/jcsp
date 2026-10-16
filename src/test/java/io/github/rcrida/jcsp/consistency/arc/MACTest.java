package io.github.rcrida.jcsp.consistency.arc;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.ObjectSingletonDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MACTest {

    private static final Variable.Factory F = Variable.Factory.INSTANCE;

    private final Variable<Integer> x = F.create("macx");
    private final Variable<Integer> y = F.create("macy");
    private final Variable<Integer> z = F.create("macz");
    private final ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
            .variableDomain(x, IntRangeDomain.of(1, 3))
            .variableDomain(y, IntRangeDomain.of(1, 3))
            .variableDomain(z, IntRangeDomain.of(1, 3))
            .notEqualsConstraint(x, y)
            .build();

    @Test
    void revisesNothing_isFalseWhileANeighbourIsUnassigned() {
        assertThat(MAC.revisesNothing(csp, x, Assignment.of(Map.of(x, 1)))).isFalse();
    }

    @Test
    void revisesNothing_isTrueOnceEveryNeighbourIsAssigned() {
        assertThat(MAC.revisesNothing(csp, x, Assignment.of(Map.of(x, 1, y, 2)))).isTrue();
    }

    @Test
    void revisesNothing_isTrueForAVariableInNoBinaryConstraint() {
        assertThat(MAC.revisesNothing(csp, z, Assignment.of(Map.of(z, 1)))).isTrue();
    }

    @Test
    void assignedDomain_isTheSingletonOfTheAssignedValue() {
        assertThat(MAC.assignedDomain(y, Assignment.of(Map.of(y, 2)))).isEqualTo(new ObjectSingletonDomain<>(2));
    }

    @Test
    void apply_narrowsTheAssignedVariableAndItsNeighbours() {
        var result = MAC.INSTANCE.apply(csp, x, Assignment.of(Map.of(x, 1)));

        assertThat(result).isPresent();
        assertThat(result.get().getDomain(x)).isEqualTo(new ObjectSingletonDomain<>(1));
        assertThat(result.get().getDomain(y)).isEqualTo(IntRangeDomain.of(2, 3));
    }
}
