package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSearchSupportTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void conflictConstraints_includesExactlyOneAlongsideItsIncompleteDecomposition() {
        var x = F.<Boolean>create("x");
        var y = F.<Boolean>create("y");
        var z = F.<Boolean>create("z");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, new BooleanDomain())
                .variableDomain(y, new BooleanDomain())
                .variableDomain(z, new BooleanDomain())
                .exactlyOneConstraint(Set.of(x, y, z))
                .build();

        var constraints = LocalSearchSupport.conflictConstraints(csp).toList();

        assertThat(constraints).anyMatch(c -> c instanceof ExactlyOneConstraint);
    }

    @Test
    void conflictConstraints_includesCircuitAlongsideItsIncompleteDecomposition() {
        var s1 = F.<Integer>create("s1");
        var s2 = F.<Integer>create("s2");
        var s3 = F.<Integer>create("s3");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s1, domain)
                .variableDomain(s2, domain)
                .variableDomain(s3, domain)
                .circuitConstraint(List.of(s1, s2, s3))
                .build();

        var constraints = LocalSearchSupport.conflictConstraints(csp).toList();

        assertThat(constraints).anyMatch(c -> c instanceof CircuitConstraint);
    }

    @Test
    void conflictConstraints_omitsAtMostOneSinceItsDecompositionIsComplete() {
        // AtMostOneConstraint's own semantics (unlike its subclass ExactlyOneConstraint's) are
        // fully captured by the inherited pairwise-NAND decomposition, so the original n-ary
        // constraint should not also appear alongside it.
        var x = F.<Boolean>create("x");
        var y = F.<Boolean>create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, new BooleanDomain())
                .variableDomain(y, new BooleanDomain())
                .atMostOneConstraint(Set.of(x, y))
                .build();

        var constraints = LocalSearchSupport.conflictConstraints(csp).toList();

        assertThat(constraints).noneMatch(c -> c instanceof AtMostOneConstraint);
    }
}
