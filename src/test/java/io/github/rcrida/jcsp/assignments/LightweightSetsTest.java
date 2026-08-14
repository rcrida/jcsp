package io.github.rcrida.jcsp.assignments;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightweightSetsTest {

    @Test
    void unionView_combinesTwoDisjointNonEmptySets() {
        Set<String> first = Set.of("a", "b");
        Set<String> second = Set.of("c");

        Set<String> union = LightweightSets.unionView(first, second);

        assertThat(union).hasSize(3);
        assertThat(union).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(union.isEmpty()).isFalse();
    }

    @Test
    void unionView_contains_checksBothDelegates() {
        Set<String> union = LightweightSets.unionView(Set.of("a"), Set.of("b"));

        assertThat(union.contains("a")).isTrue();
        assertThat(union.contains("b")).isTrue();
        assertThat(union.contains("z")).isFalse();
    }

    @Test
    void unionView_isEmpty_trueOnlyWhenBothDelegatesEmpty() {
        assertThat(LightweightSets.unionView(Set.of(), Set.of()).isEmpty()).isTrue();
        assertThat(LightweightSets.unionView(Set.of(), Set.of("a")).isEmpty()).isFalse();
        assertThat(LightweightSets.unionView(Set.of("a"), Set.of()).isEmpty()).isFalse();
    }

    @Test
    void unionView_iterator_exhaustedThrows() {
        Set<String> union = LightweightSets.unionView(Set.of("a"), Set.of("b"));
        Iterator<String> it = union.iterator();
        it.next();
        it.next();

        assertThat(it.hasNext()).isFalse();
        assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unionView_iterator_emptySecondDelegate_exhaustsImmediatelyAfterFirst() {
        Set<String> union = LightweightSets.unionView(Set.of("a"), Set.of());
        Iterator<String> it = union.iterator();

        assertThat(it.next()).isEqualTo("a");
        assertThat(it.hasNext()).isFalse();
        assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void snapshot_copiesElementsWithoutDeduplicationConcerns() {
        Set<String> source = Set.of("x", "y", "z");

        Set<String> copy = LightweightSets.snapshot(source);

        assertThat(copy).hasSize(3);
        assertThat(copy).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    void snapshot_ofEmptyCollection_isEmpty() {
        Set<String> copy = LightweightSets.snapshot(Set.of());

        assertThat(copy).isEmpty();
        assertThatThrownBy(copy.iterator()::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void snapshot_iterator_exhaustedThrows() {
        Set<String> copy = LightweightSets.snapshot(Set.of("only"));
        Iterator<String> it = copy.iterator();
        it.next();

        assertThat(it.hasNext()).isFalse();
        assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    }
}
