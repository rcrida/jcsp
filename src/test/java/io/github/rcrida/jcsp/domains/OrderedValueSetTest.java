package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OrderedValueSet} replaces {@code Collections.unmodifiableSet(LinkedHashSet)} as every
 * set-backed domain's value store, so what matters is that it is a faithful {@link Set} with
 * insertion order -- the ordering the whole solver's value ordering is defined against.
 */
class OrderedValueSetTest {

    private static OrderedValueSet<Integer> of(Integer... values) {
        return OrderedValueSet.handingOver(new LinkedHashSet<>(List.of(values)));
    }

    @Test
    void preservesInsertionOrder() {
        assertThat(of(5, 1, 9, 3)).containsExactly(5, 1, 9, 3);
    }

    @Test
    void sizeAndIsEmpty() {
        assertThat(of(5, 1).size()).isEqualTo(2);
        assertThat(of(5, 1).isEmpty()).isFalse();
        assertThat(OrderedValueSet.<Integer>handingOver(new LinkedHashSet<>()).isEmpty()).isTrue();
    }

    @Test
    void contains() {
        OrderedValueSet<Integer> set = of(5, 1, 9);

        assertThat(set.contains(9)).isTrue();
        assertThat(set.contains(7)).isFalse();
        assertThat(set.contains(null)).isFalse();
    }

    @Test
    void iterator_exhausted_throws() {
        Iterator<Integer> iterator = of(5).iterator();

        assertThat(iterator.next()).isEqualTo(5);
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void toArray_returnsACopy() {
        OrderedValueSet<Integer> set = of(5, 1);
        Object[] array = set.toArray();
        array[0] = 99;

        assertThat(set).containsExactly(5, 1);
    }

    @Test
    void spliterator_reportsOrderedDistinctAndImmutable() {
        Spliterator<Integer> spliterator = of(5, 1, 9).spliterator();

        assertThat(spliterator.hasCharacteristics(Spliterator.ORDERED)).isTrue();
        assertThat(spliterator.hasCharacteristics(Spliterator.DISTINCT)).isTrue();
        assertThat(spliterator.hasCharacteristics(Spliterator.IMMUTABLE)).isTrue();
        assertThat(spliterator.estimateSize()).isEqualTo(3);
    }

    @Test
    void stream_traversesInOrder() {
        assertThat(of(5, 1, 9).stream().toList()).containsExactly(5, 1, 9);
    }

    /**
     * {@link DiscreteSetDomain#domainEquals} compares two domains by their value sets, so this must
     * agree with the {@link LinkedHashSet} it replaced -- in both directions, and regardless of the
     * order either was built in.
     */
    @Test
    void equalsAndHashCode_matchAnEquivalentLinkedHashSet() {
        OrderedValueSet<Integer> set = of(5, 1, 9);
        Set<Integer> equivalent = new LinkedHashSet<>(List.of(9, 5, 1));

        assertThat(set).isEqualTo(equivalent);
        assertThat(equivalent).isEqualTo(set);
        assertThat(set.hashCode()).isEqualTo(equivalent.hashCode());
    }

    @Test
    void isImmutable() {
        assertThatThrownBy(() -> of(5, 1).add(7)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> of(5, 1).remove(5)).isInstanceOf(UnsupportedOperationException.class);
    }
}
