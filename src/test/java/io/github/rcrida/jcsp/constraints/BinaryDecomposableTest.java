package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryDecomposableTest {
    private final Variable.Factory factory = new Variable.Factory();

    @Test
    void defaults_answerFromTheDecompositionItself() {
        Variable<Integer> a = factory.create("a");
        Variable<Integer> b = factory.create("b");
        BinaryDecomposable decomposable = () -> Set.<BinaryConstraint<?, ?>>of(BinaryNotEqualsConstraint.of(a, b));
        BinaryDecomposable empty = Set::of;

        assertThat(decomposable.propagatesItsDecomposition()).isFalse();
        assertThat(decomposable.hasBinaryDecomposition()).isTrue();
        assertThat(empty.hasBinaryDecomposition()).isFalse();
        assertThat(decomposable.mayDecomposeInto(BinaryNotEqualsConstraint.class)).isTrue();
        assertThat(decomposable.mayDecomposeInto(BinaryComparatorConstraint.class)).isFalse();
    }
}
