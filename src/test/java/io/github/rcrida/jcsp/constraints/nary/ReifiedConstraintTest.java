package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryReifiedUnaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReifiedConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> B = F.create("b");
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");

    // body: x = 3
    static final UnaryValueConstraint<Integer> BODY =
            UnaryValueConstraint.of(X, 3);

    static Assignment a(boolean b, int x) {
        return Assignment.builder().value(B, b).value(X, x).build();
    }

    @Test
    void satisfiedWhenIndicatorMatchesBody() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.isSatisfiedBy(a(true,  3))).isTrue();   // b=true,  x=3  (body satisfied)
        assertThat(rc.isSatisfiedBy(a(false, 4))).isTrue();   // b=false, x=4  (body not satisfied)
        assertThat(rc.isSatisfiedBy(a(true,  4))).isFalse();  // b=true,  x!=3 (mismatch)
        assertThat(rc.isSatisfiedBy(a(false, 3))).isFalse();  // b=false, x=3  (mismatch)
    }

    @Test
    void optimisticallyTrueForPartialAssignment() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.isSatisfiedBy(Assignment.builder().value(B, true).build())).isTrue();
        assertThat(rc.isSatisfiedBy(Assignment.builder().value(B, false).build())).isTrue();
        assertThat(rc.isSatisfiedBy(Assignment.builder().build())).isTrue();
    }

    @Test
    void variablesContainsIndicatorAndBodyVariables() {
        assertThat(ReifiedConstraint.of(B, BODY).getVariables()).containsExactlyInAnyOrder(B, X);
    }

    @Test
    void unaryBody_decomposesToBinaryReifiedConstraint() {
        val rc = ReifiedConstraint.of(B, BODY);
        assertThat(rc.getAsBinaryConstraints()).isPresent();
        assertThat(rc.getAsBinaryConstraints().get()).hasSize(1);
        assertThat(rc.getAsBinaryConstraints().get().iterator().next())
                .isInstanceOf(BinaryReifiedUnaryConstraint.class);
    }

    @Test
    void nonUnaryBody_noDecomposition() {
        val body = BinaryNotEqualsConstraint.<Integer>builder().left(X).right(Y).build();
        assertThat(ReifiedConstraint.of(B, body).getAsBinaryConstraints()).isEmpty();
    }

    @Test
    void getRelationDescribesEquivalence() {
        assertThat(ReifiedConstraint.of(B, BODY).getRelation()).contains("<->");
    }

    @Test
    void forcedIndicator_propagatesToBodyVariable() {
        // b <-> (x = 3), b forced true => x must be 3
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(B, BooleanDomain.INSTANCE)
                .variableDomain(X, IntRangeDomain.of(1, 5))
                .reifyConstraint(B, BODY)
                .equalsConstraint(B, true)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(X)).hasValue(3);
    }
}
