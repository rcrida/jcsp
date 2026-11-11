package io.github.rcrida.jcsp.constraints.nary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SubsetSumCoveragePropagationTest {

    @Test
    void keepsExactlyTheValuesSomeCombinationOfTheOthersSupports() {
        // x1 ∈ {0,3}, x2 ∈ {0,5}, x1 + x2 == 5: only x1 = 0 with x2 = 5.
        var kept = SubsetSumCoveragePropagation.computeSubsetSumCoverage(
                List.of(new int[] {0, 3}, new int[] {0, 5}), new int[] {1, 1}, new int[] {0, 0}, new int[] {3, 5}, 5);

        assertThat(kept).contains(List.of(Set.of(0), Set.of(5)));
    }

    @Test
    void anUnreachableBoundIsInfeasible() {
        // x1 ∈ {0,3}, x2 ∈ {0,5}, x1 + x2 == 4: within [0,8], but no combination reaches it.
        var kept = SubsetSumCoveragePropagation.computeSubsetSumCoverage(
                List.of(new int[] {0, 3}, new int[] {0, 5}), new int[] {1, 1}, new int[] {0, 0}, new int[] {3, 5}, 4);

        assertThat(kept).isEmpty();
    }

    @Test
    void agreesWithEnumeratingEveryReachableSum() {
        Random random = new Random(12345);
        for (int trial = 0; trial < 2000; trial++) {
            int n = 1 + random.nextInt(4);
            List<int[]> values = new ArrayList<>();
            int[] coeffs = new int[n];
            int[] minContribs = new int[n];
            int[] maxContribs = new int[n];
            for (int i = 0; i < n; i++) {
                coeffs[i] = (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(random.nextBoolean() ? 1 : 5));
                values.add(randomValues(random));
                int lo = Integer.MAX_VALUE;
                int hi = Integer.MIN_VALUE;
                for (int v : values.get(i)) {
                    lo = Math.min(lo, coeffs[i] * v);
                    hi = Math.max(hi, coeffs[i] * v);
                }
                // A declared domain may be wider than the live values; the contract allows it.
                minContribs[i] = lo - random.nextInt(3) * Math.abs(coeffs[i]);
                maxContribs[i] = hi + random.nextInt(3) * Math.abs(coeffs[i]);
            }
            int bound = reachableBound(random, values, coeffs);

            var actual = SubsetSumCoveragePropagation.computeSubsetSumCoverage(values, coeffs, minContribs, maxContribs, bound);

            assertThat(actual).as("trial %d", trial).isEqualTo(expected(values, coeffs, bound));
        }
    }

    /** Either a contiguous range (sometimes wider than a word) or a few gapped values. */
    private static int[] randomValues(Random random) {
        int start = random.nextInt(21) - 10;
        if (random.nextBoolean()) {
            return IntStream.rangeClosed(start, start + random.nextInt(random.nextBoolean() ? 6 : 140)).toArray();
        }
        return IntStream.generate(() -> start + random.nextInt(40)).limit(1 + random.nextInt(5)).distinct().toArray();
    }

    /** Usually a sum some combination reaches, sometimes an arbitrary one nearby. */
    private static int reachableBound(Random random, List<int[]> values, int[] coeffs) {
        int bound = 0;
        for (int i = 0; i < values.size(); i++) {
            int[] vs = values.get(i);
            bound += coeffs[i] * vs[random.nextInt(vs.length)];
        }
        return random.nextInt(4) == 0 ? bound + random.nextInt(7) - 3 : bound;
    }

    private static Optional<List<Set<Integer>>> expected(List<int[]> values, int[] coeffs, int bound) {
        int n = coeffs.length;
        List<Set<Integer>> kept = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Set<Integer> others = Set.of(0);
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                Set<Integer> next = new HashSet<>();
                for (int sum : others) {
                    for (int v : values.get(j)) next.add(sum + coeffs[j] * v);
                }
                others = next;
            }
            Set<Integer> keptForTerm = new HashSet<>();
            for (int v : values.get(i)) {
                if (others.contains(bound - coeffs[i] * v)) keptForTerm.add(v);
            }
            if (keptForTerm.isEmpty()) return Optional.empty();
            kept.add(keptForTerm);
        }
        return Optional.of(kept);
    }
}
