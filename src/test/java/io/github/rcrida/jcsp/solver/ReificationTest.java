package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates reification via a preferential graph-colouring problem.
 *
 * <p>Three vertices (a, b, c) are connected as a path: a-b and b-c. Each must receive a
 * different colour from its neighbour (hard constraint). Three preferences specify ideal
 * colours for each vertex. A reified boolean indicator captures whether each preference is
 * satisfied, and an {@code atLeastN} constraint requires at least two to hold.
 */
public class ReificationTest {
    enum Colour { RED, GREEN, BLUE }

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Colour> A = F.create("a");
    static final Variable<Colour> B = F.create("b");
    static final Variable<Colour> C = F.create("c");

    // Preference indicators: r1 <-> a=RED, r2 <-> b=GREEN, r3 <-> c=BLUE
    static final Variable<Boolean> R1 = F.create("r1");
    static final Variable<Boolean> R2 = F.create("r2");
    static final Variable<Boolean> R3 = F.create("r3");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(A, EnumDomain.allOf(Colour.class))
            .variableDomain(B, EnumDomain.allOf(Colour.class))
            .variableDomain(C, EnumDomain.allOf(Colour.class))
            .variableDomain(R1, BooleanDomain.INSTANCE)
            .variableDomain(R2, BooleanDomain.INSTANCE)
            .variableDomain(R3, BooleanDomain.INSTANCE)
            // Hard: adjacent vertices must differ
            .notEqualsConstraint(A, B)
            .notEqualsConstraint(B, C)
            // Reify preferences
            .reifyConstraint(R1, UnaryValueConstraint.<Colour>builder().variable(A).value(Colour.RED).build())
            .reifyConstraint(R2, UnaryValueConstraint.<Colour>builder().variable(B).value(Colour.GREEN).build())
            .reifyConstraint(R3, UnaryValueConstraint.<Colour>builder().variable(C).value(Colour.BLUE).build())
            // At least 2 of the 3 preferences must be satisfied
            .atLeastNConstraint(Set.of(R1, R2, R3), 2)
            .build();

    @Test
    void allSolutionsSatisfyAtLeastTwoPreferences() {
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(CSP).toList();
        assertThat(solutions).isNotEmpty();
        assertThat(solutions).allSatisfy(sol -> {
            long satisfied = Stream.of(
                    sol.getValue(R1).orElse(false),
                    sol.getValue(R2).orElse(false),
                    sol.getValue(R3).orElse(false)
            ).filter(b -> b).count();
            assertThat(satisfied).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void exactSolutionSetIsCorrect() {
        // With a ≠ b, b ≠ c and at least 2 of {a=RED, b=GREEN, c=BLUE}:
        //   (RED, GREEN, BLUE) — all 3 preferences     ← r1=T, b ≠ a ✓, b ≠ c ✓
        //   (RED, GREEN, RED)  — r1 and r2             ← b=GREEN ≠ RED ✓, c=RED ≠ GREEN ✓
        //   (BLUE, GREEN, BLUE) — r2 and r3            ← a=BLUE ≠ GREEN ✓, c=BLUE ≠ GREEN ✓
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(CSP).toList();
        assertThat(solutions).hasSize(3);
        assertThat(solutions).anySatisfy(s -> {
            assertThat(s.getValue(A)).hasValue(Colour.RED);
            assertThat(s.getValue(B)).hasValue(Colour.GREEN);
            assertThat(s.getValue(C)).hasValue(Colour.BLUE);
        });
        assertThat(solutions).anySatisfy(s -> {
            assertThat(s.getValue(A)).hasValue(Colour.RED);
            assertThat(s.getValue(B)).hasValue(Colour.GREEN);
            assertThat(s.getValue(C)).hasValue(Colour.RED);
        });
        assertThat(solutions).anySatisfy(s -> {
            assertThat(s.getValue(A)).hasValue(Colour.BLUE);
            assertThat(s.getValue(B)).hasValue(Colour.GREEN);
            assertThat(s.getValue(C)).hasValue(Colour.BLUE);
        });
    }
}
