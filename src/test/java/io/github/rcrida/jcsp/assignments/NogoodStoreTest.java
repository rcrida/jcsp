package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NogoodStoreTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;
    private static final Variable<Integer> X = VF.create("x");
    private static final Variable<Integer> Y = VF.create("y");
    private static final Variable<Integer> Z = VF.create("z");

    @Test
    void emptyStoreIsNeverViolated() {
        NogoodStore store = new NogoodStore();
        Assignment a = Assignment.of(Map.of(X, 1, Y, 2));
        assertThat(store.isViolated(a)).isFalse();
    }

    @Test
    void sizeReflectsRecordedNogoods() {
        NogoodStore store = new NogoodStore();
        assertThat(store.size()).isZero();
        store.record(Map.of(X, 1));
        assertThat(store.size()).isEqualTo(1);
        store.record(Map.of(Y, 2));
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void exactMatchIsViolated() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1, Y, 2));
        assertThat(store.isViolated(Assignment.of(Map.of(X, 1, Y, 2)))).isTrue();
    }

    @Test
    void supersetOfNogoodIsViolated() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1));
        // assignment with extra variable still subsumes the nogood
        assertThat(store.isViolated(Assignment.of(Map.of(X, 1, Y, 2)))).isTrue();
    }

    @Test
    void wrongValueIsNotViolated() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1, Y, 2));
        assertThat(store.isViolated(Assignment.of(Map.of(X, 1, Y, 3)))).isFalse();
    }

    @Test
    void partialSubsetIsNotViolated() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1, Y, 2, Z, 3));
        // only two of three nogood vars assigned — not fully subsumed
        assertThat(store.isViolated(Assignment.of(Map.of(X, 1, Y, 2)))).isFalse();
    }

    @Test
    void secondNogoodCanTriggerViolation() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1));
        store.record(Map.of(Y, 5));
        assertThat(store.isViolated(Assignment.of(Map.of(X, 2, Y, 5)))).isTrue();
    }

    @Test
    void emptyNogoodIsIgnoredNotRecorded() {
        // An empty nogood would vacuously match every assignment (allMatch on an empty stream
        // is true), pruning the entire search tree. record() must ignore it rather than store it.
        NogoodStore store = new NogoodStore();
        store.record(Map.of());
        assertThat(store.size()).isZero();
        assertThat(store.isViolated(Assignment.of(Map.of(X, 1)))).isFalse();
        assertThat(store.isViolated(Assignment.of(Map.of()))).isFalse();
    }

    @Test
    void equalsAndHashCodeExcludeMutableList() {
        NogoodStore a = new NogoodStore();
        NogoodStore b = new NogoodStore();
        a.record(Map.of(X, 1));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
