package org.jcsp.constraints.unary;

import lombok.experimental.SuperBuilder;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class UnaryConstraintTest {
    static final Object VALUE = 5;
    static final Domain DOMAIN = new IntRangeDomain(0, 100);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};

    Variable variable = VARIABLE_FACTORY.create("variable", DOMAIN);

    @SuperBuilder
    static class TestUnaryConstraint extends UnaryConstraint {

        @Override
        public boolean isSatisfiedBy(@Nullable Object value) {
            return false;
        }

        @Override
        public String getRelation() {
            return "testRelation";
        }
    }
    UnaryConstraint constraint = TestUnaryConstraint.builder().variable(variable).build();

    @Test
    void getVariables() {
        assertThat(constraint.getVariables()).containsExactly(variable);
    }

    @Test
    void testToString() {
        assertThat(constraint).asString().isEqualTo("<(variable), testRelation>");
    }
}
