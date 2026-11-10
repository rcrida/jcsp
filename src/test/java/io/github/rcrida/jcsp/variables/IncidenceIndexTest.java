package io.github.rcrida.jcsp.variables;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidenceIndexTest {
    private final Variable.Factory factory = new Variable.Factory();
    private final Variable<Integer> a = factory.create("a");
    private final Variable<Integer> b = factory.create("b");
    private final Variable<Integer> c = factory.create("c");

    private record Unindexed(String name) implements Variable<Integer> {
        @Override
        public String getName() {
            return name;
        }
    }

    @Test
    void findsEveryPositionAVariableAppearsAtInAscendingOrder() {
        var index = IncidenceIndex.of(List.of(Set.of(a, c), Set.of(), Set.of(c), Set.of(a)));

        assertThat(index.positionsOf(a)).containsExactly(0, 3);
        assertThat(index.positionsOf(c)).containsExactly(0, 2);
    }

    @Test
    void aVariableInNoScopeFindsNothing() {
        var index = IncidenceIndex.of(List.of(Set.of(a, c)));
        Variable<Integer> later = factory.create("later");

        assertThat(index.positionsOf(b)).isEmpty();
        assertThat(index.positionsOf(later)).isEmpty();
        assertThat(index.positionsOf(new Unindexed("u"))).isEmpty();
        assertThat(IncidenceIndex.of(List.of()).positionsOf(a)).isEmpty();
    }

    @Test
    void aVariableSharingAnIndexedOnesIndexFindsNothing() {
        var index = IncidenceIndex.of(List.of(Set.of(b), Set.of(a)));
        Variable<Integer> sameIndexAsA = new Variable.Factory().create("other");

        assertThat(index.positionsOf(sameIndexAsA)).isEmpty();
        assertThat(index.variables()).doesNotContain(sameIndexAsA);
    }

    @Test
    void variablesAreEveryIndexedVariableInIndexOrder() {
        var index = IncidenceIndex.of(List.of(Set.of(c), Set.of(a, c)));

        assertThat(index.variables()).containsExactly(a, c).hasSize(2);
        assertThat(index.variables().contains(a)).isTrue();
        assertThat(index.variables().contains(b)).isFalse();
        assertThat(index.variables().contains("a")).isFalse();
        assertThat(IncidenceIndex.of(List.of()).variables()).isEmpty();
    }

    @Test
    void theVariablesIteratorThrowsWhenExhausted() {
        var iterator = IncidenceIndex.of(List.of(Set.of(b))).variables().iterator();

        assertThat(iterator.next()).isSameAs(b);
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void rejectsAVariableNoFactoryIssued() {
        assertThatThrownBy(() -> IncidenceIndex.of(List.of(Set.of(new Unindexed("u")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable was not issued by a Variable.Factory: Unindexed[name=u]");
    }

    @Test
    void rejectsTwoVariablesSharingAnIndex() {
        Variable<Integer> sameIndexAsA = new Variable.Factory().create("other");

        assertThatThrownBy(() -> IncidenceIndex.of(List.of(Set.of(a), Set.of(sameIndexAsA))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variables a and other share index 0");
    }
}
