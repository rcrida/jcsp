package io.github.rcrida.jcsp.domains;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumericSetDomainTest {

    @Test
    void of_oneValue_returnsNumericSingletonDomain() {
        NumericDomain<Integer> domain = NumericDiscreteDomain.of(5);

        assertThat(domain).isInstanceOf(NumericSingletonDomain.class);
        assertThat(domain.getMin()).isEqualTo(5);
        assertThat(domain.getMax()).isEqualTo(5);
    }

    @Test
    void of_multipleValues_returnsNumericSetDomain() {
        NumericDomain<Integer> domain = NumericDiscreteDomain.of(1, 2, 3);

        assertThat(domain).isInstanceOf(NumericSetDomain.class);
        assertThat(((NumericSetDomain<Integer>) domain).values()).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void testToString() {
        assertThat(NumericDiscreteDomain.of(1, 2, 3).toString()).isEqualTo("{1, 2, 3}");
    }

    // ── Cached bounds (see this domain's own Javadoc: null bounds are reachable only through the
    // canonical constructor, since the builder collapses an empty value set to NumericEmptyDomain) ──

    @Test
    void getMinAndGetMax_unorderedValues_returnTheExtremes() {
        NumericDiscreteDomain<Integer> domain = NumericDiscreteDomain.of(3, 1, 2);

        assertThat(domain.getMin()).isEqualTo(1);
        assertThat(domain.getMax()).isEqualTo(3);
    }

    @Test
    void getMin_emptyValues_throws() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.<Integer>of()).getMin())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMax_emptyValues_throws() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.<Integer>of()).getMax())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void constructor_mismatchedMin_throwsAssertionError() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.of(1, 2, 3), 100, 3))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void constructor_mismatchedMax_throwsAssertionError() {
        assertThatThrownBy(() -> new NumericSetDomain<>(Set.of(1, 2, 3), 1, 200))
                .isInstanceOf(AssertionError.class);
    }

    // ── Value-by-value deletion (the AC3 arc-revision path -- polymorphic toBuilder()/delete(),
    // distinct from the withBounds path covered by NumericDomainTest) ──

    @Test
    void toBuilder_deletedDownToOneValue_buildsNumericSingletonDomain() {
        DiscreteDomain<Integer> narrowed = new NumericSetDomain<>(Set.of(1, 2)).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericSingletonDomain.class);
        assertThat(narrowed.singleValue()).contains(2);
    }

    @Test
    void toBuilder_deletedDownToZeroValues_buildsNumericEmptyDomain() {
        DiscreteDomain<Integer> narrowed = new NumericSetDomain<>(Set.of(1)).toBuilder().delete(1).build();

        assertThat(narrowed).isInstanceOf(NumericEmptyDomain.class);
        assertThat(narrowed.isEmpty()).isTrue();
    }

    @Test
    void builder_neverAddedAnyValue_buildsNumericEmptyDomain() {
        DiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().build();

        assertThat(built).isInstanceOf(NumericEmptyDomain.class);
        assertThat(built.isEmpty()).isTrue();
    }

    @Test
    void builder_value_addsOneAtATime() {
        NumericDiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().value(1).value(2).build();

        assertThat(built).isInstanceOf(NumericSetDomain.class);
        assertThat(built.toList()).containsExactly(1, 2);
    }

    @Test
    void builder_values_addsACollection() {
        NumericDiscreteDomain<Integer> built = NumericDiscreteDomain.<Integer>builder().values(List.of(1, 2)).build();

        assertThat(built.toList()).containsExactly(1, 2);
    }

    @Test
    void builder_buildThenMutated_throwsAssertionError() {
        var builder = NumericDiscreteDomain.<Integer>builder().value(1).value(2);
        builder.build();

        assertThatThrownBy(() -> builder.delete(1)).isInstanceOf(AssertionError.class);
    }

    /**
     * Deleting a bound is the one case the builder can't keep tracking in constant time, so
     * {@link NumericDiscreteDomain.NumericDiscreteDomainBuilder#build} rescans -- and the
     * assertion in {@link NumericSetDomain}'s canonical constructor is what would catch it
     * failing to.
     */
    private static NumericSetDomain<Integer> of(Integer... values) {
        return (NumericSetDomain<Integer>) NumericDiscreteDomain.of(values);
    }

    // ── Narrowing through toBuilder: deletions filter the existing array, nothing is rehashed ──

    @Test
    void toBuilder_deleteInterior_keepsOrderAndBounds() {
        NumericDiscreteDomain<Integer> narrowed = of(4, 1, 3, 2).toBuilder().delete(3).build();

        assertThat(narrowed.toList()).containsExactly(4, 1, 2);
        assertThat(narrowed.getMin()).isEqualTo(1);
        assertThat(narrowed.getMax()).isEqualTo(4);
        assertThat(narrowed.contains(3)).isFalse();
        assertThat(narrowed.contains(4)).isTrue();
    }

    @Test
    void toBuilder_deleteMinimum_rescansTheBounds() {
        NumericDiscreteDomain<Integer> narrowed = of(4, 1, 3, 2).toBuilder().delete(1).build();

        assertThat(narrowed.getMin()).isEqualTo(2);
        assertThat(narrowed.getMax()).isEqualTo(4);
    }

    @Test
    void toBuilder_deleteMaximum_rescansTheBounds() {
        NumericDiscreteDomain<Integer> narrowed = of(4, 1, 3, 2).toBuilder().delete(4).build();

        assertThat(narrowed.getMin()).isEqualTo(1);
        assertThat(narrowed.getMax()).isEqualTo(3);
    }

    @Test
    void toBuilder_deleteAnAbsentValue_leavesTheDomainAsItWas() {
        NumericDiscreteDomain<Integer> domain = of(1, 2, 3);

        assertThat(domain.toBuilder().delete(99).build()).isEqualTo(domain);
    }

    @Test
    void toBuilder_noDeletions_leavesTheDomainAsItWas() {
        NumericDiscreteDomain<Integer> domain = of(1, 2, 3);

        NumericDiscreteDomain<Integer> rebuilt = (NumericDiscreteDomain<Integer>) domain.toBuilder().build();

        assertThat(rebuilt).isEqualTo(domain);
        assertThat(rebuilt.getMax()).isEqualTo(3);
    }

    @Test
    void toBuilder_deleteDownToOne_isASingleton() {
        assertThat(of(1, 2, 3).toBuilder().delete(1).delete(3).build()).isInstanceOf(NumericSingletonDomain.class);
    }

    @Test
    void toBuilder_deleteEverything_isEmpty() {
        assertThat(of(1, 2).toBuilder().delete(1).delete(2).build()).isInstanceOf(NumericEmptyDomain.class);
    }

    @Test
    void toBuilder_noDeletionsFromAnEmptyOrSingletonValueSet_collapsesAsBuildDoes() {
        var empty = new NumericSetDomain<Integer>(OrderedValueSet.handingOver(new java.util.LinkedHashSet<>()));
        var single = new NumericSetDomain<>(OrderedValueSet.handingOver(new java.util.LinkedHashSet<>(List.of(7))));

        assertThat(empty.toBuilder().build()).isInstanceOf(NumericEmptyDomain.class);
        assertThat(single.toBuilder().build()).isInstanceOf(NumericSingletonDomain.class);
    }

    @Test
    void toBuilder_addAfterDeleting_appliesTheDeletionsFirst() {
        NumericDiscreteDomain<Integer> built = of(1, 2, 3).toBuilder().delete(2).value(9).build();

        assertThat(built.toList()).containsExactly(1, 3, 9);
        assertThat(built.getMax()).isEqualTo(9);
    }

    @Test
    void toBuilder_addWithoutDeleting_keepsEveryValue() {
        NumericDiscreteDomain<Integer> built = of(1, 2, 3).toBuilder().value(9).build();

        assertThat(built.toList()).containsExactly(1, 2, 3, 9);
    }

    @Test
    void toBuilder_plainSetBackedDomain_isCopied() {
        var domain = new NumericSetDomain<>(Set.of(1, 2, 3));

        assertThat(domain.toBuilder().delete(1).build().toList()).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void toBuilder_narrowedBuilderBuiltThenMutated_throwsAssertionError() {
        var builder = of(1, 2, 3).toBuilder().delete(1);
        builder.build();

        assertThatThrownBy(() -> builder.delete(2)).isInstanceOf(AssertionError.class);
    }

    @Test
    void toBuilder_intRangeDomain_narrowsWithoutLosingNumericness() {
        NumericDiscreteDomain<Integer> narrowed = IntRangeDomain.of(1, 20).toBuilder().delete(1).delete(7).build();

        assertThat(narrowed).isInstanceOf(NumericSetDomain.class);
        assertThat(narrowed.getMin()).isEqualTo(2);
        assertThat(narrowed.size()).isEqualTo(18);
        assertThat(narrowed.contains(7)).isFalse();
        assertThat(narrowed.contains(8)).isTrue();
    }

    @Test
    void builder_deletedTheMaximum_rescansTheBounds() {
        NumericDiscreteDomain<Integer> built =
                NumericDiscreteDomain.<Integer>builder().values(List.of(1, 2, 3)).delete(3).build();

        assertThat(built.getMin()).isEqualTo(1);
        assertThat(built.getMax()).isEqualTo(2);
    }

    @Test
    void builder_deletedAnInteriorOrAbsentValue_keepsTheBounds() {
        NumericDiscreteDomain<Integer> built =
                NumericDiscreteDomain.<Integer>builder().values(List.of(1, 2, 3)).delete(2).delete(99).build();

        assertThat(built.toList()).containsExactly(1, 3);
        assertThat(built.getMin()).isEqualTo(1);
        assertThat(built.getMax()).isEqualTo(3);
    }

    @Test
    void builder_deletedTheMinimum_rescansTheBounds() {
        NumericDiscreteDomain<Integer> built =
                NumericDiscreteDomain.<Integer>builder().values(List.of(1, 2, 3)).delete(1).build();

        assertThat(built.getMin()).isEqualTo(2);
        assertThat(built.getMax()).isEqualTo(3);
    }
}
