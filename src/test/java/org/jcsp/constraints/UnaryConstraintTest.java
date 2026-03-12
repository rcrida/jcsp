package org.jcsp.constraints;

import lombok.val;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.relations.UnaryRelation;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnaryConstraintTest {
    static final Domain DOMAIN = new IntRangeDomain(0, 10);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};
    static final Variable VARIABLE = VARIABLE_FACTORY.create("X", DOMAIN);

    @Mock
    UnaryRelation unaryRelation;
    @Mock
    Assignment assignment;
    UnaryConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = UnaryConstraint.of(VARIABLE, unaryRelation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy_assignment(boolean result) {
        when(unaryRelation.isSatisfied(assignment)).thenReturn(result);
        assertThat(constraint.isSatisfiedBy(assignment)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy_array(boolean result) {
        val value = 10;
        when(unaryRelation.isSatisfied(value)).thenReturn(result);
        assertThat(constraint.isSatisfied(value)).isEqualTo(result);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(X), unaryRelation>");
    }
}
