package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.GreedyAssignmentFactory;
import io.github.rcrida.jcsp.solver.LocalSolver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
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
        val solver = Solver.Factory.INSTANCE.createSolver(csp);
        val optionalSolution = solver.getSolution();
        printAssignment(optionalSolution.orElseThrow());
        System.out.println(optionalSolution.orElseThrow().getStatistics());
    }

    @Test
    void solutions() {
        val csp = nQueens();
        val solver = Solver.Factory.INSTANCE.createSolver(csp);
        assertThat(solver.getSolutions()).hasSize(92);
    }

    @Test
    void solutions_symmetryBreaking() {
        // The 92 solutions come in left-right mirror pairs. Requiring the first queen's column
        // to be less than the last queen's column (Q[0] < Q[7]) eliminates exactly one solution
        // from each mirror pair, halving the count.  Since allDiff guarantees Q[0] != Q[7],
        // increasing(Q[0], Q[7]) is equivalent to Q[0] < Q[7].
        val cspBuilder = ConstraintSatisfactionProblem.builder();
        val labels = new String[N];
        for (int i = 0; i < N; i++) labels[i] = String.valueOf(i + 1);
        VARIABLES = cspBuilder.create1dVariableArray(labels, "Q", DOMAIN);
        cspBuilder.allDiffConstraint(Set.of(VARIABLES));
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++)
                cspBuilder.offsetConstraint(VARIABLES[i], j - i, Operator.NEQ, VARIABLES[j]);
        for (int i = 0; i < N; i++)
            for (int j = 0; j < i; j++)
                cspBuilder.offsetConstraint(VARIABLES[i], i - j, Operator.NEQ, VARIABLES[j]);
        cspBuilder.increasingConstraint(List.of(VARIABLES[0], VARIABLES[N - 1]));
        assertThat(Solver.Factory.INSTANCE.createSolver(cspBuilder.build()).getSolutions()).hasSize(46);
    }

    @Test
    void localSolution() {
        val csp = nQueens();
        val solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 500, GreedyAssignmentFactory.INSTANCE);
        val optionalSolution = solver.getLocalSolution(csp);
        printAssignment(optionalSolution.orElseThrow());
    }
}
