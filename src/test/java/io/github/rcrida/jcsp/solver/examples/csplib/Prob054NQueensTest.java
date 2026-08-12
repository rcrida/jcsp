package io.github.rcrida.jcsp.solver.examples.csplib;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class Prob054NQueensTest {
    static int N = 8;
    static Domain DOMAIN = IntRangeDomain.of(1, N);
    static Variable[] VARIABLES;

    record NQueensProblem(ConstraintSatisfactionProblem csp, Variable[] variables) {}

    public static ConstraintSatisfactionProblem nQueens() {
        val problem = nQueens(N);
        VARIABLES = problem.variables();
        return problem.csp();
    }

    /**
     * Board size is a parameter, so a larger instance can be built (e.g. for benchmarking); unlike
     * {@link #nQueens()}, this overload doesn't touch the shared static {@link #VARIABLES} field.
     * At board size {@link #N} loads the real XCSP3 instance file (Queens-0008-m1.xml from the
     * XCSP3 Queens series, https://xcsp.org/instances/, unmodified -- its diagonal-attack rule
     * uses the {@code dist()} intension operator); every other size builds the CSP programmatically.
     */
    public static NQueensProblem nQueens(int n) {
        if (n == N) {
            return xcsp3NQueens();
        }

        val cspBuilder = ConstraintSatisfactionProblem.builder();
        val labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = String.valueOf(i + 1);
        }
        Variable[] variables = cspBuilder.create1dVariableArray(labels, "Q", IntRangeDomain.of(1, n));
        System.out.println(Arrays.toString(variables));
        // vertical constraint
        cspBuilder.allDiffConstraint(Set.of(variables));
        // down right diagonal constraints
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                cspBuilder.offsetConstraint(variables[i], j - i, Operator.NEQ, variables[j]);
            }
        }
        // down left diagonal constraints
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                cspBuilder.offsetConstraint(variables[i], i - j, Operator.NEQ, variables[j]);
            }
        }
        return new NQueensProblem(cspBuilder.build(), variables);
    }

    private static NQueensProblem xcsp3NQueens() {
        var instance = Xcsp3CsplibResource.parse("nqueens-8.xml");
        Variable[] variables = IntStream.range(0, N).mapToObj(i -> Variable.Factory.INSTANCE.create("q[" + i + "]")).toArray(Variable[]::new);
        return new NQueensProblem(instance.csp(), variables);
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
