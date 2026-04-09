package org.jcsp.variables;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class VariableTest {
    @Test
    void factory() {
        var variableFactory = Variable.Factory.INSTANCE;
        var name = UUID.randomUUID().toString();
        Variable variable = variableFactory.create(name);
        assertThat(variable.getName()).isEqualTo(name);
    }
}
