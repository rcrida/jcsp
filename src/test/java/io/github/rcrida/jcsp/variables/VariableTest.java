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
    void forkContinuesNumberingAndCountsTheParentsNamesAsIssued() {
        var parent = new Variable.Factory();
        parent.create("a");
        parent.create("b");
        var fork = parent.fork();
        assertThat(fork.create("c").getIndex()).isEqualTo(2);
        assertThatThrownBy(() -> fork.create("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fork.create("c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forksOfOneFactoryAreIndependentOfEachOtherAndOfTheirParent() {
        var parent = new Variable.Factory();
        parent.create("a");
        var first = parent.fork();
        var second = parent.fork();
        assertThat(first.create("x").getIndex()).isEqualTo(1);
        assertThat(second.create("x").getIndex()).isEqualTo(1);
        assertThat(parent.create("x").getIndex()).isEqualTo(1);
    }

    @Test
    void forkOfAForkSeesEveryAncestorsNames() {
        var root = new Variable.Factory();
        root.create("a");
        var middle = root.fork();
        middle.create("b");
        var leaf = middle.fork();
        assertThatThrownBy(() -> leaf.create("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leaf.create("b")).isInstanceOf(IllegalArgumentException.class);
        assertThat(leaf.create("c").getIndex()).isEqualTo(2);
    }

    @Test
    void sharedInstanceCannotBeForked() {
        assertThatThrownBy(Variable.Factory.INSTANCE::fork).isInstanceOf(AssertionError.class);
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
