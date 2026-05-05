package io.github.rcrida.jcsp.consistency.arc;

import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArcTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable from = VARIABLE_FACTORY.create("from");
    Variable to = VARIABLE_FACTORY.create("to");
    Arc arc;

    @BeforeEach
    void setUp() {
        arc = Arc.of(from, to);
    }

    @Test
    void construct_asserts() {
        assertThatThrownBy(() -> new Arc(from, from))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(arc).asString().isEqualTo("(from -> to)");
    }
}
