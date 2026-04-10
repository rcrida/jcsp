package org.jcsp.constraints.binary;

import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArcTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable left = VARIABLE_FACTORY.create("left");
    Variable right = VARIABLE_FACTORY.create("right");
    Arc arc;

    @BeforeEach
    void setUp() {
        arc = new Arc(left, right);
    }

    @Test
    void construct_asserts() {
        assertThatThrownBy(() -> new Arc(left, left))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(arc).asString().isEqualTo("(left -> right)");
    }
}
