package org.jcsp.variables;

import org.jcsp.domains.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VariableTest {
    @Mock
    Variable variable;
    @Mock
    Domain domain;
    @Mock
    Object value;
    @Mock
    Object anotherValue;

    @Test
    void isAllowedValue() {
        when(domain.contains(value)).thenReturn(true);
        when(variable.getDomain()).thenReturn(domain);
        when(variable.isAllowedValue(any())).thenCallRealMethod();
        assertThat(variable.isAllowedValue(value)).isTrue();
        assertThat(variable.isAllowedValue(anotherValue)).isFalse();
    }

    @Test
    void factory() {
        var variableFactory = Variable.Factory.INSTANCE;
        var name = UUID.randomUUID().toString();
        Variable variable = variableFactory.create(name, domain);
        assertThat(variable.getName()).isEqualTo(name);
        assertThat(variable.getDomain()).isEqualTo(domain);
    }
}
