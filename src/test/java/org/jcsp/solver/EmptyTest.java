package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EmptyTest {
    public static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder().build();
    }

    @Test
    void solution() {
        val csp = problem();
        assertThat(csp.getSearchSpace()).isEqualTo(BigInteger.valueOf(1));
        val optionalSolution = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        assertThat(optionalSolution).contains(Assignment.EMPTY);
    }
}
