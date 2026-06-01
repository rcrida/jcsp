package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A restaurant offers a fixed-price three-course menu. Not all combinations of
 * starter, main, and dessert are available — only the explicitly listed combos
 * are on the menu. This is modelled using a {@code tuplesConstraint}, which is the
 * natural fit when a multi-variable relationship is defined by an explicit table
 * rather than a formula.
 *
 * <p>The valid menus are:
 * <pre>
 *   Starter     | Main    | Dessert
 *   ------------|---------|----------
 *   SOUP        | PASTA   | TIRAMISU
 *   SOUP        | STEAK   | TIRAMISU
 *   SALAD       | FISH    | SORBET
 *   SALAD       | PASTA   | SORBET
 *   BRUSCHETTA  | STEAK   | CAKE
 *   BRUSCHETTA  | FISH    | CAKE
 * </pre>
 */
public class MenuCombinationTest {
    enum Starter  { SOUP, SALAD, BRUSCHETTA }
    enum Main     { PASTA, STEAK, FISH }
    enum Dessert  { TIRAMISU, SORBET, CAKE }

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Starter> STARTER = F.create("starter");
    static final Variable<Main>    MAIN    = F.create("main");
    static final Variable<Dessert> DESSERT = F.create("dessert");

    static final Set<Assignment> MENU = Set.of(
            Assignment.of(Map.of(STARTER, Starter.SOUP,       MAIN, Main.PASTA, DESSERT, Dessert.TIRAMISU)),
            Assignment.of(Map.of(STARTER, Starter.SOUP,       MAIN, Main.STEAK, DESSERT, Dessert.TIRAMISU)),
            Assignment.of(Map.of(STARTER, Starter.SALAD,      MAIN, Main.FISH,  DESSERT, Dessert.SORBET)),
            Assignment.of(Map.of(STARTER, Starter.SALAD,      MAIN, Main.PASTA, DESSERT, Dessert.SORBET)),
            Assignment.of(Map.of(STARTER, Starter.BRUSCHETTA, MAIN, Main.STEAK, DESSERT, Dessert.CAKE)),
            Assignment.of(Map.of(STARTER, Starter.BRUSCHETTA, MAIN, Main.FISH,  DESSERT, Dessert.CAKE))
    );

    static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(STARTER, EnumDomain.allOf(Starter.class))
                .variableDomain(MAIN,    EnumDomain.allOf(Main.class))
                .variableDomain(DESSERT, EnumDomain.allOf(Dessert.class))
                .tuplesConstraint(MENU)
                .build();
    }

    @Test
    void allSolutions() {
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(problem()).toList();
        assertThat(solutions).hasSize(6);
    }

    @Test
    void solution() {
        val result = Solver.Factory.INSTANCE.createSolver().getSolution(problem());
        assertThat(result).hasValueSatisfying(assignment -> {
            assertThat(assignment.isSolution(problem())).isTrue();
            System.out.println("Menu: " + assignment);
        });
    }
}
