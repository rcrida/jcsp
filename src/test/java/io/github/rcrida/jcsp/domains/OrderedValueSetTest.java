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
    void ofDistinct_smallSet_containsScansTheArray() {
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(new Object[]{5, 1, 9});

        assertThat(set).containsExactly(5, 1, 9);
        assertThat(set.contains(1)).isTrue();
        assertThat(set.contains(7)).isFalse();
        assertThat(set.contains(null)).isFalse();
    }

    @Test
    void ofDistinct_largeSet_containsBuildsTheIndexOnceAndReusesIt() {
        Object[] values = new Object[20];
        for (int i = 0; i < values.length; i++) values[i] = i * 2;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(6)).isTrue();
        assertThat(set.contains(7)).isFalse();
        assertThat(set.contains(38)).isTrue();
    }

    @Test
    void ofDistinct_denseIntegers_containsTestsABitAndMatchesAHashSet() {
        Object[] values = new Object[40];
        for (int i = 0; i < values.length; i++) values[i] = 100 + i * 3;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);
        Set<Object> oracle = new LinkedHashSet<>(List.of(values));

        for (int probe = 90; probe < 250; probe++) {
            assertThat(set.contains(probe)).as("contains(%d)", probe).isEqualTo(oracle.contains(probe));
        }
        assertThat(set.contains(Integer.MIN_VALUE)).isFalse();
        assertThat(set.contains(Integer.MAX_VALUE)).isFalse();
    }

    @Test
    void ofDistinct_denseIntegersAcrossZero_containsHandlesNegativeValues() {
        Object[] values = new Object[20];
        for (int i = 0; i < values.length; i++) values[i] = i - 7;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(-7)).isTrue();
        assertThat(set.contains(0)).isTrue();
        assertThat(set.contains(12)).isTrue();
        assertThat(set.contains(-8)).isFalse();
        assertThat(set.contains(13)).isFalse();
    }

    @Test
    void ofDistinct_aBitSetIsNotMistakenForOtherTypes() {
        Object[] values = new Object[20];
        for (int i = 0; i < values.length; i++) values[i] = i;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(5)).isTrue();
        assertThat(set.contains(5L)).isFalse();
        assertThat(set.contains("5")).isFalse();
        assertThat(set.contains(null)).isFalse();
    }

    @Test
    void ofDistinct_sparseIntegers_fallBackToHashingAndStayCorrect() {
        Object[] values = new Object[12];
        for (int i = 0; i < values.length; i++) values[i] = i * 100_000;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(300_000)).isTrue();
        assertThat(set.contains(300_001)).isFalse();
    }

    @Test
    void ofDistinct_integersSpanningTheWholeIntRange_doNotOverflowTheSpan() {
        Object[] values = new Object[10];
        for (int i = 0; i < values.length - 2; i++) values[i] = i;
        values[8] = Integer.MIN_VALUE;
        values[9] = Integer.MAX_VALUE;
        OrderedValueSet<Integer> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(Integer.MIN_VALUE)).isTrue();
        assertThat(set.contains(Integer.MAX_VALUE)).isTrue();
        assertThat(set.contains(3)).isTrue();
        assertThat(set.contains(9)).isFalse();
    }

    @Test
    void handingOver_denseIntegers_areTestedByBitsDespiteTheIndexHandedIn() {
        Set<Integer> handedIn = new LinkedHashSet<>();
        for (int i = 0; i < 40; i++) handedIn.add(200 + i * 2);
        OrderedValueSet<Integer> set = OrderedValueSet.handingOver(handedIn);

        for (int probe = 190; probe < 300; probe++) {
            assertThat(set.contains(probe)).as("contains(%d)", probe).isEqualTo(handedIn.contains(probe));
        }
    }

    @Test
    void handingOver_sparseIntegers_stayOnTheIndexHandedIn() {
        Set<Integer> handedIn = new LinkedHashSet<>();
        for (int i = 0; i < 12; i++) handedIn.add(i * 100_000);
        OrderedValueSet<Integer> set = OrderedValueSet.handingOver(handedIn);

        assertThat(set.contains(700_000)).isTrue();
        assertThat(set.contains(700_001)).isFalse();
    }

    @Test
    void handingOver_aSmallSet_isAlsoTestedByBits() {
        OrderedValueSet<Integer> set = OrderedValueSet.handingOver(new LinkedHashSet<>(List.of(4, 8, 6)));

        assertThat(set.contains(6)).isTrue();
        assertThat(set.contains(5)).isFalse();
    }

    @Test
    void ofDistinct_mixedTypes_fallBackToHashing() {
        Object[] values = new Object[12];
        for (int i = 0; i < values.length - 1; i++) values[i] = i;
        values[11] = "eleven";
        OrderedValueSet<Object> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains("eleven")).isTrue();
        assertThat(set.contains(4)).isTrue();
        assertThat(set.contains(11)).isFalse();
    }

    @Test
    void ofDistinct_longs_fallBackToHashingAndAnIntegerOfTheSameValueIsAbsent() {
        Object[] values = new Object[12];
        for (int i = 0; i < values.length; i++) values[i] = (long) i;
        OrderedValueSet<Long> set = OrderedValueSet.ofDistinct(values);

        assertThat(set.contains(5L)).isTrue();
        assertThat(set.contains(5)).isFalse();
    }

    @Test
    void elements_isTheBackingArrayInOrder() {
        assertThat(of(5, 1, 9).elements()).containsExactly(5, 1, 9);
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
