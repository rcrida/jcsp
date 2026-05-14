package io.github.rcrida.jcsp.constraints.unary;

import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class UnaryConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable variable = VARIABLE_FACTORY.create("variable");

    @SuperBuilder
    static class TestUnaryConstraint extends UnaryConstraint<Object> {

        @Override
        protected boolean checkValue(@Nullable Object value) {
            return false;
        }

        @Override
        public String getRelation() {
            return "testRelation";
        }
    }
    UnaryConstraint<?> constraint = TestUnaryConstraint.builder().variable(variable).build();

    @Test
    void getVariables() {
        assertThat(constraint.getVariables()).containsExactly(variable);
    }

    @Test
    void testToString() {
        assertThat(constraint).asString().isEqualTo("<(variable), testRelation>");
    }
}
