package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryReifiedUnaryConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> B = F.create("b");
    static final Variable<Integer> X = F.create("x");

    static final UnaryValueConstraint<Integer> BODY =
            UnaryValueConstraint.of(X, 3);

    static final BinaryReifiedUnaryConstraint<Integer> CONSTRAINT =
            BinaryReifiedUnaryConstraint.<Integer>builder()
                    .left(B).right(X).body(BODY).build();

    @Test
    void indicatorTrueAndValueMatchesBody_satisfied() {
        assertThat(CONSTRAINT.isSatisfiedBy(true, 3)).isTrue();
    }

    @Test
    void indicatorTrueAndValueNotMatchingBody_notSatisfied() {
        assertThat(CONSTRAINT.isSatisfiedBy(true, 4)).isFalse();
    }

    @Test
    void indicatorFalseAndValueMatchesBody_notSatisfied() {
        assertThat(CONSTRAINT.isSatisfiedBy(false, 3)).isFalse();
    }

    @Test
    void indicatorFalseAndValueNotMatchingBody_satisfied() {
        assertThat(CONSTRAINT.isSatisfiedBy(false, 4)).isTrue();
    }

    @Test
    void getRelationDescribesEquivalence() {
        assertThat(CONSTRAINT.getRelation()).contains("<->");
    }
}
