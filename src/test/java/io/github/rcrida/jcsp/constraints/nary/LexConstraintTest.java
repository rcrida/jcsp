package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LexConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x1 = F.create("x1");
    Variable<Integer> x2 = F.create("x2");
    Variable<Integer> y1 = F.create("y1");
    Variable<Integer> y2 = F.create("y2");

    LexConstraint<Integer> leq;
    LexConstraint<Integer> lt;

    @BeforeEach
    void setUp() {
        leq = LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2));
        lt  = LexConstraint.of(List.of(x1, x2), Operator.LT,  List.of(y1, y2));
    }

    @Test
    void firstDiffers_left_less_satisfied() {
        // (1, 9) lex< (2, 0): first position 1 < 2 → true regardless of rest
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 9, y1, 2, y2, 0)))).isTrue();
    }

    @Test
    void firstDiffers_left_greater_notSatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 0, y1, 1, y2, 9)))).isFalse();
    }

    @Test
    void secondDiffers_left_less_satisfied() {
        // (1, 2) lex< (1, 3): first equal, second 2 < 3
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 2, y1, 1, y2, 3)))).isTrue();
    }

    @Test
    void secondDiffers_left_greater_notSatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 3, y1, 1, y2, 2)))).isFalse();
    }

    @Test
    void allEqual_leq_satisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 2, y1, 2, y2, 2)))).isTrue();
    }

    @Test
    void allEqual_lt_notSatisfied() {
        // strict less: equal sequences do not satisfy
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 2, x2, 2, y1, 2, y2, 2)))).isFalse();
    }

    @Test
    void lt_firstDiffers_left_less_satisfied() {
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 5, y1, 2, y2, 0)))).isTrue();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, y1, 1)))).isTrue();
        // Even if left first position > right first, if right second is unknown we're optimistic
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 5)))).isTrue();
    }

    @Test
    void of_unequalLengths_asserts() {
        assertThatThrownBy(() -> LexConstraint.of(List.of(x1), Operator.LEQ, List.of(y1, y2)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(leq.toString()).isEqualTo("<(x1, x2, y1, y2), [x1, x2] <= [y1, y2]>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LexConstraint.of(List.of(x1, x2), Operator.LEQ, List.of(y1, y2))).isEqualTo(leq);
    }

    @Test
    void solver_lexLeq_solutionCount() {
        // [a,b] leq [c,d] over domain {1,2,3}: there are 9 possible (a,b) pairs and 9 (c,d) pairs.
        // Pairs where [a,b] <= [c,d]: (9*9 + 9) / 2 = 45.
        Variable<Integer> a = F.create("a"), b = F.create("b");
        Variable<Integer> c = F.create("c"), d = F.create("d");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, domain).variableDomain(b, domain)
                .variableDomain(c, domain).variableDomain(d, domain)
                .lexConstraint(List.of(a, b), Operator.LEQ, List.of(c, d))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(45);
    }
}
