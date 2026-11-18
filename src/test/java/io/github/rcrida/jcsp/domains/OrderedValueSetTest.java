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

    // ---- ranges and their slices ----------------------------------------------------------------

    @Test
    void range_isTheConsecutiveIntegersAscending() {
        OrderedValueSet<Integer> range = OrderedValueSet.range(-2, 2);
        assertThat(range.isRange()).isTrue();
        assertThat(range).containsExactly(-2, -1, 0, 1, 2);
        assertThat(of(1, 2, 3).isRange()).isFalse();
    }

    @Test
    void slice_isTheSubRange() {
        OrderedValueSet<Integer> slice = OrderedValueSet.range(0, 9).between(3, 6);
        assertThat(slice.isRange()).isTrue();
        assertThat(slice.size()).isEqualTo(4);
        assertThat(slice).containsExactly(3, 4, 5, 6);
        assertThat(slice.toArray()).containsExactly(3, 4, 5, 6);
        assertThat(slice.stream().toList()).containsExactly(3, 4, 5, 6);
        assertThat(slice.get(0)).isEqualTo(3);
        assertThat(slice.between(4, 5)).containsExactly(4, 5);
    }

    @Test
    void between_clampsToTheElementsPresent() {
        OrderedValueSet<Integer> range = OrderedValueSet.range(0, 9);
        assertThat(range.between(-1, 5)).containsExactly(0, 1, 2, 3, 4, 5).hasToString("[0..5]");
        assertThat(range.between(5, 10)).containsExactly(5, 6, 7, 8, 9).hasToString("[5..9]");
        assertThat(range.between(2.5, 4.5)).containsExactly(3, 4);
        assertThat(range.between(6, 5)).isEmpty();
        assertThat(range.between(20, 30)).isEmpty();
        assertThat(range.between(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)).isEqualTo(range);
    }

    @Test
    void between_onASetThatDoesNotAscend_isRejected() {
        assertThatThrownBy(() -> of(3, 1, 2).between(1, 2)).isInstanceOf(AssertionError.class);
    }

    // ---- ascending sets that are not ranges --------------------------------------------------------

    @Test
    void isAscending_isDecidedByTheElementsInTheirOwnOrder() {
        assertThat(OrderedValueSet.range(0, 3).isAscending()).isTrue();
        assertThat(of(1, 4, 9).isAscending()).isTrue();
        assertThat(of().isAscending()).isTrue();
        assertThat(of(3, 1, 2).isAscending()).isFalse();
        assertThat(of(10, 9, 8).isAscending()).isFalse();
        assertThat(OrderedValueSet.ofDistinct(new Object[]{1, "a"}).isAscending()).isFalse();
    }

    @Test
    void ofDistinct_prefix_takesTheCallersOrderOnTrust() {
        assertThat(OrderedValueSet.ofDistinct(new Object[]{1, 2, 3}, 3, true).isAscending()).isTrue();
        assertThat(OrderedValueSet.ofDistinct(new Object[]{1, 2, 3}, 3, false).isAscending()).isFalse();
    }

    @Test
    void ofDistinct_whole_decidesItsOrderByLooking() {
        assertThat(OrderedValueSet.ofDistinct(new Object[]{1, 2, 3}).isAscending()).isTrue();
        assertThat(OrderedValueSet.ofDistinct(new Object[]{2, 1, 3}).isAscending()).isFalse();
    }

    @Test
    void between_aRange_staysARange_andAnAscendingSetDoesNotBecomeOne() {
        assertThat(OrderedValueSet.range(0, 9).between(2, 5).isRange()).isTrue();
        assertThat(of(1, 2, 3, 4).isRange()).isFalse();
        assertThat(of(1, 2, 3, 4).between(2, 3).isRange()).isFalse();
        assertThat(of(1, 2, 3, 4).between(2, 3).isAscending()).isTrue();
    }

    /** Odd numbers 1..39: ascending, not a range, and wide enough that a slice of it isn't scanned. */
    private static OrderedValueSet<Integer> odds() {
        Object[] values = new Object[20];
        for (int i = 0; i < values.length; i++) values[i] = 2 * i + 1;
        return OrderedValueSet.ofDistinct(values);
    }

    @Test
    void between_anAscendingSet_isAViewCutAtBothEnds() {
        OrderedValueSet<Integer> slice = odds().between(6, 30);

        assertThat(slice.isRange()).isFalse();
        assertThat(slice).containsExactly(7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29);
        assertThat(slice.get(0)).isEqualTo(7);
        assertThat(slice.toArray()).hasSize(12);
        assertThat(slice.between(10, 14)).containsExactly(11, 13);
    }

    @Test
    void between_anAscendingSet_testsMembershipByItsBoundsAndTheWholeSetsIndex() {
        OrderedValueSet<Integer> slice = odds().between(6, 30);

        assertThat(slice.contains(7)).isTrue();
        assertThat(slice.contains(29)).isTrue();
        assertThat(slice.contains(5)).isFalse();   // in the whole set, below the slice
        assertThat(slice.contains(31)).isFalse();  // in the whole set, above the slice
        assertThat(slice.contains(8)).isFalse();   // within the slice's bounds, in neither
        assertThat(slice.contains("7")).isFalse();
        assertThat(slice.contains(null)).isFalse();
        // A slice of a slice still answers through the bounds of its own run.
        OrderedValueSet<Integer> inner = slice.between(9, 27);
        assertThat(inner.contains(7)).isFalse();
        assertThat(inner.contains(27)).isTrue();
        // A slice small enough to scan scans its own run.
        OrderedValueSet<Integer> small = slice.between(10, 14);
        assertThat(small.contains(11)).isTrue();
        assertThat(small.contains(9)).isFalse();
    }

    @Test
    void ofDistinct_prefix_ignoresTheRestOfTheArray() {
        // What a filtered narrowing leaves behind it: the array it was filtered into, part used.
        assertThat(OrderedValueSet.ofDistinct(new Object[]{5, 1, 9, 99}, 3, false)).containsExactly(5, 1, 9)
                .doesNotContain(99);

        Object[] dense = new Object[12];
        for (int i = 0; i < dense.length; i++) dense[i] = i;
        OrderedValueSet<Integer> densePrefix = OrderedValueSet.ofDistinct(dense, 10, false);
        assertThat(densePrefix.contains(9)).isTrue();
        assertThat(densePrefix.contains(10)).isFalse();

        Object[] words = new Object[12];
        for (int i = 0; i < words.length; i++) words[i] = "w" + i;
        OrderedValueSet<String> wordPrefix = OrderedValueSet.ofDistinct(words, 10, false);
        assertThat(wordPrefix.contains("w9")).isTrue();
        assertThat(wordPrefix.contains("w10")).isFalse();
        assertThat(wordPrefix).hasSize(10);
    }

    @Test
    void slice_iteratorStopsAtItsOwnEnd() {
        Iterator<Integer> iterator = OrderedValueSet.range(0, 9).between(8, 9).iterator();
        iterator.next();
        iterator.next();
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void range_containsIsABoundsCheck() {
        OrderedValueSet<Integer> slice = OrderedValueSet.range(0, 9).between(3, 6);
        assertThat(slice.contains(3)).isTrue();
        assertThat(slice.contains(6)).isTrue();
        assertThat(slice.contains(2)).isFalse();
        assertThat(slice.contains(7)).isFalse();
        assertThat(slice.contains(Integer.MIN_VALUE)).isFalse();
        assertThat(slice.contains(4L)).isFalse();
        assertThat(slice.contains(null)).isFalse();
    }

    @Test
    void range_equalsAndHashCodeAgreeWithAnyEqualSet() {
        OrderedValueSet<Integer> slice = OrderedValueSet.range(0, 9).between(3, 6);
        assertThat(slice).isEqualTo(OrderedValueSet.range(3, 6));
        assertThat(slice).isNotEqualTo(OrderedValueSet.range(3, 7));
        assertThat(slice).isNotEqualTo(OrderedValueSet.range(4, 7));
        assertThat(slice).isEqualTo(of(6, 5, 4, 3));
        assertThat(slice).isEqualTo(Set.of(3, 4, 5, 6));
        assertThat(slice.hashCode()).isEqualTo(Set.of(3, 4, 5, 6).hashCode());
        assertThat(OrderedValueSet.range(-3, 1).hashCode()).isEqualTo(Set.of(-3, -2, -1, 0, 1).hashCode());
        OrderedValueSet<Integer> wide = OrderedValueSet.range(Integer.MAX_VALUE - 2, Integer.MAX_VALUE);
        assertThat(wide.hashCode()).isEqualTo(Set.of(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE).hashCode());
        assertThat(of(1, 2).hashCode()).isEqualTo(Set.of(1, 2).hashCode());
        assertThat(of(1, 2)).isEqualTo(OrderedValueSet.range(1, 2));
    }

    @Test
    void get_readsTheElementsInOrder() {
        OrderedValueSet<Integer> set = of(5, 1, 9);
        assertThat(List.of(set.get(0), set.get(1), set.get(2))).containsExactly(5, 1, 9);
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
