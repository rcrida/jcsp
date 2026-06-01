package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two-sum puzzle modelled as a CSP using {@code elementConstraint}.
 *
 * <p>Given the array [2, 7, 11, 15], find all index pairs (i, j) where
 * {@code array[i] + array[j] == 9}. The {@code elementConstraint} binds each
 * index variable to its corresponding value in the array.
 *
 * <p>The only pair summing to 9 is {2, 7}, appearing at indices (1, 2) and (2, 1).
 */
public class TwoSumTest {
    static final List<Integer> ARRAY = List.of(2, 7, 11, 15);
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final Variable<Integer> I  = F.create("i");
    static final Variable<Integer> J  = F.create("j");
    static final Variable<Integer> VI = F.create("vi");
    static final Variable<Integer> VJ = F.create("vj");

    static ConstraintSatisfactionProblem problem() {
        val valueDomain = DomainObjectSet.<Integer>builder().values(ARRAY).build();
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(I,  IntRangeDomain.of(1, 4))
                .variableDomain(J,  IntRangeDomain.of(1, 4))
                .variableDomain(VI, valueDomain)
                .variableDomain(VJ, valueDomain)
                .elementConstraint(I, VI, ARRAY)
                .elementConstraint(J, VJ, ARRAY)
                .sumConstraint(Set.of(VI, VJ), Operator.EQ, 9)
                .notEqualsConstraint(I, J)
                .build();
    }

    @Test
    void solutions() {
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(problem()).toList();
        assertThat(solutions).hasSize(2);
        solutions.forEach(System.out::println);
    }
}
