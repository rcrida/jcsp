package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class NQueensTest {
    static int N = 8;
    static Domain DOMAIN = IntRangeDomain.of(1, N);
    static Variable[] VARIABLES;

    public static ConstraintSatisfactionProblem nQueens() {
        val cspBuilder = ConstraintSatisfactionProblem.builder();
        val labels = new String[N];
        for (int i = 0; i < N; i++) {
            labels[i] = String.valueOf(i+1);
        }
        VARIABLES = cspBuilder.create1dVariableArray(labels, "Q", DOMAIN);
        System.out.println(Arrays.toString(VARIABLES));
        // vertical constraint
        cspBuilder.allDiffConstraint(Set.of(VARIABLES));
        // down right diagonal constraints
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                cspBuilder.offsetConstraint(VARIABLES[i], j - i, Operator.NEQ, VARIABLES[j]);
            }
        }
        // down left diagonal constraints
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < i; j++) {
                cspBuilder.offsetConstraint(VARIABLES[i], i - j, Operator.NEQ, VARIABLES[j]);
            }
        }
        return cspBuilder.build();
    }

    static void printAssignment(Assignment assignment) {
        for (int i = 0; i < N; i++) {
            val col = (int) assignment.getValue(VARIABLES[i]).orElseThrow();
            for (int j = 1; j < col; j++) {
                System.out.print(" ");
            }
            System.out.println("Q");
        }
    }

    @Test
    void solution() {
        val csp = nQueens();
        assertThat(csp.getSearchSpace()).isEqualTo(BigInteger.valueOf(16777216));
        val solver = Solver.Factory.INSTANCE.createSolver();
        val optionalSolution = solver.getSolution(csp);
        printAssignment(optionalSolution.orElseThrow());
        System.out.println(optionalSolution.orElseThrow().getStatistics());
    }

    @Test
    void solutions() {
        val csp = nQueens();
        val solver = Solver.Factory.INSTANCE.createSolver();
        assertThat(solver.getSolutions(csp)).hasSize(92);
    }

    @Test
    void localSolution() {
        val csp = nQueens();
        val solver = new MinConflictsSolver(500);
        val optionalSolution = solver.getLocalSolution(csp, new RandomAssignmentFactory());
        printAssignment(optionalSolution.orElseThrow());
    }
}
