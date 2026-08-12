package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Instance;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests parsing real XCSP3 instance files (under {@code
 * src/test/resources/xcsp3/}) via {@link Xcsp3Parser} and solving the resulting {@link
 * io.github.rcrida.jcsp.ConstraintSatisfactionProblem} through the ordinary {@link Solver.Factory}
 * chains, the same way every other {@code solver.examples} test exercises a hand-built CSP.
 */
class Xcsp3InstanceTest {

    private static Path resource(String name) throws URISyntaxException {
        return Paths.get(Xcsp3InstanceTest.class.getResource("/xcsp3/" + name).toURI());
    }

    @Test void sendMoreMoney_solvesToTheKnownUniqueSolution() throws IOException, URISyntaxException {
        Xcsp3Instance instance = Xcsp3Parser.parse(resource("send-more-money.xml"));
        assertThat(instance.objective()).isNull();

        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp()).getSolution();
        assertThat(solution).isPresent();
        Assignment assignment = solution.get();

        assertThat(digitOf(assignment, "S")).isEqualTo(9);
        assertThat(digitOf(assignment, "EE")).isEqualTo(5);
        assertThat(digitOf(assignment, "N")).isEqualTo(6);
        assertThat(digitOf(assignment, "D")).isEqualTo(7);
        assertThat(digitOf(assignment, "M")).isEqualTo(1);
        assertThat(digitOf(assignment, "O")).isEqualTo(0);
        assertThat(digitOf(assignment, "R")).isEqualTo(8);
        assertThat(digitOf(assignment, "Y")).isEqualTo(2);

        int send = 1000 * digitOf(assignment, "S") + 100 * digitOf(assignment, "EE") + 10 * digitOf(assignment, "N") + digitOf(assignment, "D");
        int more = 1000 * digitOf(assignment, "M") + 100 * digitOf(assignment, "O") + 10 * digitOf(assignment, "R") + digitOf(assignment, "EE");
        int money = 10000 * digitOf(assignment, "M") + 1000 * digitOf(assignment, "O") + 100 * digitOf(assignment, "N") + 10 * digitOf(assignment, "EE") + digitOf(assignment, "Y");
        assertThat(send + more).isEqualTo(money);
    }

    private static int digitOf(Assignment assignment, String name) {
        Variable<Integer> variable = Variable.Factory.INSTANCE.create(name);
        return assignment.getValue(variable).orElseThrow();
    }

    @Test void smallKnapsack_minimisesWeightSubjectToValueThreshold() throws IOException, URISyntaxException {
        Xcsp3Instance instance = Xcsp3Parser.parse(resource("small-knapsack.xml"));
        assertThat(instance.objective()).isNotNull();
        assertThat(instance.maximize()).isFalse();

        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(solution).isPresent();
        Assignment assignment = solution.get();

        assertThat(digitOf(assignment, "x1")).isEqualTo(1);
        assertThat(digitOf(assignment, "x2")).isEqualTo(1);
        assertThat(digitOf(assignment, "x3")).isEqualTo(0);
        assertThat(instance.objective().applyAsDouble(assignment)).isEqualTo(5.0);
    }
}
