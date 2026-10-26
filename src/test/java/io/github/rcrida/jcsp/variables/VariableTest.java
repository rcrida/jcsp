package io.github.rcrida.jcsp.variables;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class VariableTest {
    @Test
    void factory() {
        var variableFactory = Variable.Factory.INSTANCE;
        var name = UUID.randomUUID().toString();
        Variable variable = variableFactory.create(name);
        assertThat(variable.getName()).isEqualTo(name);
    }

    @Test
    void factoryNumbersVariablesFromZeroInCreationOrder() {
        var factory = new Variable.Factory();
        assertThat(factory.create("a").getIndex()).isEqualTo(0);
        assertThat(factory.create("b").getIndex()).isEqualTo(1);
        assertThat(new Variable.Factory().create("a").getIndex()).isEqualTo(0);
    }

    @Test
    void factoryRejectsANameItAlreadyIssued() {
        var factory = new Variable.Factory();
        factory.create("a");
        assertThatThrownBy(() -> factory.create("a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a");
    }

    @Test
    void sharedInstanceAcceptsARepeatedName() {
        var name = UUID.randomUUID().toString();
        assertThat(Variable.Factory.INSTANCE.create(name)).isEqualTo(Variable.Factory.INSTANCE.create(name));
    }

    @Test
    void indexDoesNotAffectEquality() {
        var first = new Variable.Factory().create("x");
        var second = new Variable.Factory();
        second.create("pad");
        var other = second.create("x");
        assertThat(other.getIndex()).isNotEqualTo(first.getIndex());
        assertThat(other).isEqualTo(first).hasSameHashCodeAs(first);
    }

    @Test
    void variableNoFactoryIssuedIsUnindexed() {
        Variable<Integer> custom = () -> "custom";
        assertThat(custom.getIndex()).isEqualTo(Variable.UNINDEXED);
    }
}
