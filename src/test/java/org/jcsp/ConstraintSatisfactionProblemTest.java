package org.jcsp;

import lombok.val;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.solver.AustraliaMapColouringTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jcsp.solver.AustraliaMapColouringTest.NSW;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.Q;
import static org.jcsp.solver.AustraliaMapColouringTest.SA;
import static org.jcsp.solver.AustraliaMapColouringTest.T;
import static org.jcsp.solver.AustraliaMapColouringTest.V;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;

public class ConstraintSatisfactionProblemTest {
    @Test
    void getNeightbours() {
        val csp = AustraliaMapColouringTest.problem();
        val expected = Map.of(
                WA, Set.of(NT, SA),
                NT, Set.of(WA, SA, Q),
                SA, Set.of(WA, NT, Q, NSW, V),
                Q, Set.of(NT, SA, NSW),
                NSW, Set.of(SA, Q, V),
                V, Set.of(SA, NSW),
                T, Set.of()
        );
        assertThat(csp.getNeighbours()).isEqualTo(expected);
    }

    @Test
    void decomposeSubproblems() {
        val csp = AustraliaMapColouringTest.problem();
        val expected = Set.of(
                ConstraintSatisfactionProblem.builder()
                        .variables(Set.of(WA, NT, Q, NSW, V, SA))
                        .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(WA).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(NT).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(Q).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(NSW).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(SA).right(V).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(WA).right(NT).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(NT).right(Q).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(Q).right(NSW).build())
                        .constraint(BinaryNotEqualsConstraint.builder().left(NSW).right(V).build())
                        .build(),
                ConstraintSatisfactionProblem.builder()
                        .variable(T)
                        .build()
        );
        assertThat(csp.decomposeSubproblems()).isEqualTo(expected);
    }
}
