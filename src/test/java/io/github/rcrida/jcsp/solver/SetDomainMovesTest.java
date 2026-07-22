package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SetDomainMovesTest {

    // --- randomValue ---

    @Test
    void randomValue_singletonDomain_returnsExactlyThatValue() {
        val domain = SetIntervalDomain.of(Set.of("a", "b"), Set.of("a", "b"), 2, 2);
        assertThat(SetDomainMoves.randomValue(domain)).isEqualTo(Set.of("a", "b"));
    }

    @Test
    void randomValue_fixedCardinality_alwaysReturnsThatSize() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 2, 2);
        for (int i = 0; i < 30; i++) {
            val value = SetDomainMoves.randomValue(domain);
            assertThat(value).hasSize(2);
            assertThat(domain.contains(value)).isTrue();
        }
    }

    @Test
    void randomValue_emptyDomain_throwsAssertionError() {
        // Narrowing methods (unlike the `of` factories) don't guard against infeasibility -- they're
        // used by propagation, which legitimately produces an empty domain and checks isEmpty()
        // rather than catching an exception. randomValue's precondition assert must still fire here.
        val domain = SetIntervalDomain.of(Set.of("a"), Set.of("a", "b"), 1, 2);
        val empty = domain.withUpperBound(Set.of());
        assertThat(empty.isEmpty()).isTrue();
        assertThatThrownBy(() -> SetDomainMoves.randomValue(empty)).isInstanceOf(AssertionError.class);
    }

    @Test
    void randomValue_freeCardinalityRange_variesInSize() {
        // Target cardinality is drawn uniformly from {0,1,2,3,4}; the probability that 50
        // independent draws all land on the same value is (1/5)^49, negligible.
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 0, 4);
        val sizesSeen = IntStream.range(0, 50)
                .mapToObj(i -> SetDomainMoves.randomValue(domain).size())
                .collect(Collectors.toSet());
        assertThat(sizesSeen).hasSizeGreaterThan(1);
        sizesSeen.forEach(size -> assertThat(size).isBetween(0, 4));
    }

    // --- neighbours ---

    @Test
    void neighbours_addBranchTaken_removeBranchSkipped_atCardinalityFloor() {
        // current.size()==minCardinality (remove skipped), current.size()<maxCardinality (add active).
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c"), 1, 2);
        val current = Set.of("a");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).contains(current, Set.of("a", "b"), Set.of("a", "c"));
        assertThat(results).noneMatch(v -> v.size() < current.size());
    }

    @Test
    void neighbours_addBranchSkipped_removeBranchTaken_atCardinalityCeiling() {
        // current.size()==maxCardinality (add skipped), current.size()>minCardinality (remove active).
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c"), 0, 1);
        val current = Set.of("a");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).contains(current, Set.of());
        assertThat(results).noneMatch(v -> v.size() > current.size());
    }

    @Test
    void neighbours_removeBranchTaken_producesSmallerCandidates() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 0, 2);
        val current = Set.of("a", "b");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).contains(Set.of("a"), Set.of("b"));
    }

    @Test
    void neighbours_removeBranchSkipped_atCardinalityFloor_noSmallerCandidates() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 2, 3);
        val current = Set.of("a", "b");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).noneMatch(v -> v.size() < current.size());
    }

    @Test
    void neighbours_swap_removableEmpty_producesNoSwapCandidates() {
        // current == lowerBound with cardinality fixed at current.size(): add and remove are both
        // skipped, and removable (current \ lowerBound) is empty, so swapCandidates' "removable
        // empty" short-circuit (independent of addable, which is non-empty here) is what's exercised.
        val domain = SetIntervalDomain.of(Set.of("a"), Set.of("a", "b", "c"), 1, 1);
        val results = SetDomainMoves.neighbours(domain, Set.of("a")).toList();
        assertThat(results).containsExactly(Set.of("a"));
    }

    @Test
    void neighbours_swap_addableEmpty_producesNoSwapCandidates() {
        // current == upperBound with remove active (minCardinality < current.size()): addable
        // (upperBound \ current) is empty, so swapCandidates' "addable empty" short-circuit
        // (independent of removable, which is non-empty here) is what's exercised.
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b"), 0, 2);
        val results = SetDomainMoves.neighbours(domain, Set.of("a", "b")).toList();
        assertThat(results).containsExactlyInAnyOrder(Set.of("a", "b"), Set.of("a"), Set.of("b"));
    }

    @Test
    void neighbours_swap_smallGap_enumeratesFullCrossProduct() {
        // Fixed cardinality (add/remove both skipped): removable={a,b} (size 2), addable={c,d}
        // (size 2) -> 4 candidates, well under the cap, so every (out,in) pair must appear.
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 2, 2);
        val current = Set.of("a", "b");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).containsExactlyInAnyOrder(
                current, Set.of("b", "c"), Set.of("b", "d"), Set.of("a", "c"), Set.of("a", "d"));
    }

    @Test
    void neighbours_swap_largeGap_isCappedAndDeduplicated() {
        val universe = IntStream.range(0, 40).boxed().collect(Collectors.toSet());
        val current = universe.stream().limit(20).collect(Collectors.toSet());
        // Fixed cardinality 20: removable and addable are each size 20 -> 400 possible swap pairs,
        // well over the cap, forcing the sampled path.
        val domain = SetIntervalDomain.of(Set.of(), universe, 20, 20);
        val results = SetDomainMoves.neighbours(domain, current).toList();

        assertThat(results).hasSizeLessThanOrEqualTo(1 + SetDomainMoves.MAX_SWAP_CANDIDATES);
        assertThat(results).doesNotHaveDuplicates();
        results.forEach(v -> {
            assertThat(v).hasSize(20);
            assertThat(domain.contains(v)).isTrue();
        });
    }

    @Test
    void neighbours_singletonDomain_returnsOnlyCurrent() {
        val domain = SetIntervalDomain.of(Set.of("a", "b"), Set.of("a", "b"), 2, 2);
        val results = SetDomainMoves.neighbours(domain, Set.of("a", "b")).toList();
        assertThat(results).containsExactly(Set.of("a", "b"));
    }

    @Test
    void neighbours_noDuplicatesAcrossAddRemoveAndSwapCombined() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d", "e"), 1, 3);
        val current = Set.of("a", "b");
        val results = SetDomainMoves.neighbours(domain, current).toList();
        assertThat(results).doesNotHaveDuplicates();
    }

    // --- representativeSeeds ---

    @Test
    void representativeSeeds_emptyDomain_throwsAssertionError() {
        val domain = SetIntervalDomain.of(Set.of("a"), Set.of("a", "b"), 1, 2);
        val empty = domain.withUpperBound(Set.of());
        assertThat(empty.isEmpty()).isTrue();
        assertThatThrownBy(() -> SetDomainMoves.representativeSeeds(empty, 3)).isInstanceOf(AssertionError.class);
    }

    @Test
    void representativeSeeds_paddingAndTrimmingBranchesTaken() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 2, 3);
        val seeds = SetDomainMoves.representativeSeeds(domain, 3);
        assertThat(seeds).contains(Set.of("a", "b"));   // padded lowerBound up to minCardinality
        assertThat(seeds).contains(Set.of("a", "b", "c")); // trimmed upperBound down to maxCardinality
        seeds.forEach(s -> assertThat(domain.contains(s)).isTrue());
    }

    @Test
    void representativeSeeds_paddingAndTrimmingBranchesSkipped() {
        val domain = SetIntervalDomain.of(Set.of("a", "b"), Set.of("a", "b", "c"), 2, 3);
        val seeds = SetDomainMoves.representativeSeeds(domain, 3);
        assertThat(seeds).contains(Set.of("a", "b"));       // lowerBound already at minCardinality
        assertThat(seeds).contains(Set.of("a", "b", "c"));  // upperBound already at maxCardinality
    }

    @Test
    void representativeSeeds_singletonDomain_deduplicatesToOneEntry() {
        // lowerBound == upperBound, so minimalValue, maximalValue, and every randomValue draw
        // (gap is empty) all coincide -- proves dedup actually collapses, not just doesn't grow.
        val domain = SetIntervalDomain.of(Set.of("a", "b"), Set.of("a", "b"), 2, 2);
        val seeds = SetDomainMoves.representativeSeeds(domain, 3);
        assertThat(seeds).containsExactly(Set.of("a", "b"));
    }

    // --- candidateValues ---

    @Test
    void candidateValues_discreteDomain_matchesStream() {
        val domain = IntRangeDomain.of(1, 3);
        assertThat(SetDomainMoves.candidateValues(domain, 1).toList())
                .containsExactlyInAnyOrderElementsOf(domain.stream().toList());
    }

    @Test
    void candidateValues_setBoundedDomain_matchesNeighbours() {
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c"), 1, 2);
        val current = Set.of("a");
        assertThat(SetDomainMoves.candidateValues(domain, current).toList())
                .containsExactlyInAnyOrderElementsOf(SetDomainMoves.neighbours(domain, current).toList());
    }

    @Test
    void candidateValues_unsupportedDomainKind_throwsIllegalStateException() {
        Domain<Integer> unsupported = new Domain<>() {
            @Override public boolean contains(@Nullable Object value) { return false; }
            @Override public boolean isEmpty() { return false; }
            @Override public int size() { return 0; }
            @Override public Optional<Integer> singleValue() { return Optional.empty(); }
        };
        assertThatThrownBy(() -> SetDomainMoves.candidateValues(unsupported, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
