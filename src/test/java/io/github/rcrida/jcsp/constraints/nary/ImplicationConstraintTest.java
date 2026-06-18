package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImplicationConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> B = F.create("b");
    static final Variable<Integer> X = F.create("x");

    static final UnaryValueConstraint<Integer> BODY =
            UnaryValueConstraint.of(X, 3);

    static Assignment a(boolean b, int x) {
        return Assignment.builder().value(B, b).value(X, x).build();
    }

    @Test
    void satisfiedWhenIndicatorFalseRegardlessOfBody() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(a(false, 3))).isTrue();
        assertThat(ic.isSatisfiedBy(a(false, 4))).isTrue();
    }

    @Test
    void trueIndicator_enforcesBody() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(a(true, 3))).isTrue();
        assertThat(ic.isSatisfiedBy(a(true, 4))).isFalse();
    }

    @Test
    void optimisticallyTrueForPartialAssignment() {
        val ic = ImplicationConstraint.of(B, BODY);
        assertThat(ic.isSatisfiedBy(Assignment.builder().value(B, true).build())).isTrue();
        assertThat(ic.isSatisfiedBy(Assignment.builder().build())).isTrue();
    }

    @Test
    void variablesContainsIndicatorAndBodyVariables() {
        assertThat(ImplicationConstraint.of(B, BODY).getVariables()).containsExactlyInAnyOrder(B, X);
    }

    @Test
    void getRelationDescribesImplication() {
        assertThat(ImplicationConstraint.of(B, BODY).getRelation()).contains("->");
    }

    @Test
    void forcedIndicator_enforcesBodyInSolver() {
        // b -> (x = 3), b forced true => x must be 3
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .impliesConstraint(B, BODY)
                .equalsConstraint(B, true)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(X)).hasValue(3);
    }

    @Test
    void falseIndicator_allBodyValuesAllowed() {
        // b -> (x = 3), b forced false => all x values valid
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .impliesConstraint(B, BODY)
                .equalsConstraint(B, false)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(5);
    }

    @Test
    void testToString() {
        assertThat(ImplicationConstraint.of(B, BODY).toString()).isEqualTo("<(b, x), b -> (x == 3)>");
    }
}
