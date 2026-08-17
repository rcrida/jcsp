package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DistinctVectorsConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> a0 = F.create("a0");
    Variable<Integer> a1 = F.create("a1");
    Variable<Integer> b0 = F.create("b0");
    Variable<Integer> b1 = F.create("b1");
    Variable<Integer> c0 = F.create("c0");
    Variable<Integer> c1 = F.create("c1");

    // --- of() validation ---

    @Test
    void of_fewerThanTwoVectors_asserts() {
        assertThatThrownBy(() -> DistinctVectorsConstraint.of(List.of(List.of(a0))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_emptyVectors_asserts() {
        assertThatThrownBy(() -> DistinctVectorsConstraint.of(List.<List<Variable<Integer>>>of(List.of(), List.of())))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_unequalLengthVectors_asserts() {
        assertThatThrownBy(() -> DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0))))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_differingVectors_true() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, a1, 2, b0, 1, b1, 3)))).isTrue();
    }

    @Test
    void isSatisfiedBy_identicalVectors_false() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, a1, 2, b0, 1, b1, 2)))).isFalse();
    }

    @Test
    void isSatisfiedBy_threeVectors_onlyOneCollidingPairFails() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0), List.of(b0), List.of(c0)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, b0, 1, c0, 2)))).isFalse();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, b0, 2, c0, 3)))).isTrue();
    }

    @Test
    void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, b0, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_partialButAlreadyDiffering_true() {
        // a0 != b0 already decides the pair, even though a1/b1 remain unassigned.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a0, 1, b0, 2)))).isTrue();
    }

    // --- propagate ---

    @Test
    void propagate_multipleAmbiguousPositions_noChange() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 3), a1, IntRangeDomain.of(1, 3),
                b0, IntRangeDomain.of(1, 3), b1, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_guaranteedDifferPosition_shortCircuitsNoChange() {
        // Position 0's domains are disjoint (a0 in {1,2}, b0 in {3,4}), so the pair is already
        // satisfied there regardless of position 1's still-ambiguous domains.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 2), a1, IntRangeDomain.of(1, 3),
                b0, IntRangeDomain.of(3, 4), b1, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_soleAmbiguousPosition_narrowsNonSingletonSide() {
        // Position 0 is singleton-equal (both 1); position 1 is the sole ambiguous position, with
        // b1 singleton at 5 -- a1 must be narrowed to exclude 5.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 1), a1, IntRangeDomain.of(4, 5),
                b0, IntRangeDomain.of(1, 1), b1, IntRangeDomain.of(5, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(a1);
        assertThat(result.get().get(a1)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_soleAmbiguousPosition_narrowsOtherDirection() {
        // Mirror of the above with the singleton side on vector B's own left operand -- a1 singleton,
        // b1 must be narrowed instead.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 1), a1, IntRangeDomain.of(5, 5),
                b0, IntRangeDomain.of(1, 1), b1, IntRangeDomain.of(4, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(b1);
        assertThat(result.get().get(b1)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_bothSidesNonSingletonAtSoleAmbiguousPosition_noNarrowing() {
        // Position 0 singleton-equal; position 1 is the sole ambiguous position but neither side is
        // singleton, so no specific value can be excluded from either.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 1), a1, IntRangeDomain.of(4, 5),
                b0, IntRangeDomain.of(1, 1), b1, IntRangeDomain.of(4, 5));
        var result = c.propagate(domains);
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_allPositionsForcedEqual_infeasible() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 1), a1, IntRangeDomain.of(2, 2),
                b0, IntRangeDomain.of(1, 1), b1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_singlePositionVectors_singletonPairAlreadyDifferent_feasibleNoChange() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0), List.of(b0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(a0, IntRangeDomain.of(1, 1), b0, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).contains(Map.of());
    }

    @Test
    void propagate_simultaneousNarrowingFromTwoPairs_intersects() {
        // Three length-1 vectors sharing no variables directly, but a0 participates in both pairs
        // (A,B) and (A,C). b0=1, c0=2 are singleton; a0={1,2,3} is ambiguous against both. Pair
        // (A,B) alone would exclude 1 from a0; pair (A,C) alone would exclude 2 -- together they
        // must compose (intersect), not overwrite, leaving only {3}.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0), List.of(b0), List.of(c0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 3), b0, IntRangeDomain.of(1, 1), c0, IntRangeDomain.of(2, 2));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(a0)).isEqualTo(IntRangeDomain.of(3, 3));
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_forcedEqualPair_citesOnlyThatPairsVariables() {
        // Three vectors: (A,B) forced identical; C is unrelated and should not be cited.
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0), List.of(b0), List.of(c0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 1), b0, IntRangeDomain.of(1, 1), c0, IntRangeDomain.of(5, 5));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(a0, 1, b0, 1)));
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                a0, IntRangeDomain.of(1, 3), a1, IntRangeDomain.of(1, 3),
                b0, IntRangeDomain.of(1, 3), b1, IntRangeDomain.of(1, 3));
        assertThat(c.propagate(domains)).isPresent();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- toString / getRelation ---

    @Test
    void toString_showsRelation() {
        var c = DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)));
        assertThat(c.toString()).isEqualTo("<(a0, a1, b0, b1), distinctVectors(2 vectors of length 2)>");
    }

    @Test
    void getRelation_emptyVectorsViaRawBuilder_reportsZeroLength() {
        // of() asserts non-empty vectors, but assertions are off by default in production and the
        // @SuperBuilder is public -- getRelation() must not NPE if that path is bypassed.
        var c = DistinctVectorsConstraint.builder().vectors(List.of()).build();
        assertThat(c.getRelation()).isEqualTo("distinctVectors(0 vectors of length 0)");
    }

    // --- CSP builder method ---

    @Test
    void cspBuilder_distinctVectorsConstraint_method() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a0, IntRangeDomain.of(1, 2))
                .variableDomain(b0, IntRangeDomain.of(1, 2))
                .distinctVectorsConstraint(List.of(List.of(a0), List.of(b0)))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }

    // --- solver integration ---

    @Test
    void solver_threeVectorsOfTwoBits_findsAllInjectiveAssignments() {
        // 3 lists of 2 booleans -- 4 possible 2-bit vectors, need all 3 pairwise distinct:
        // 4*3*2 = 24 injective assignments of 3 lists to 4 distinct values.
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a0, IntRangeDomain.of(0, 1)).variableDomain(a1, IntRangeDomain.of(0, 1))
                .variableDomain(b0, IntRangeDomain.of(0, 1)).variableDomain(b1, IntRangeDomain.of(0, 1))
                .variableDomain(c0, IntRangeDomain.of(0, 1)).variableDomain(c1, IntRangeDomain.of(0, 1))
                .constraint(DistinctVectorsConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1), List.of(c0, c1))))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList()).hasSize(24);
    }

    // --- randomized cross-check against brute-force GAC ---

    /**
     * Checks soundness against exhaustive brute-force search, not full GAC-equivalence -- per
     * {@link DistinctVectorsConstraint}'s own class Javadoc, its per-pair unit propagation doesn't
     * perform cross-pair Hall-set reasoning, so it can (like the reified decomposition it replaced,
     * confirmed empirically to have the identical limitation) retain a value with no valid
     * completion when detecting that requires considering three or more pairs jointly. Two
     * properties must still hold unconditionally: (1) if a genuine solution exists, {@code
     * propagate} must never claim infeasibility; (2) whatever values it retains must be a superset
     * of the true GAC-consistent set -- it may under-prune, but never wrongly exclude a value that
     * some completion actually uses.
     */
    @Test
    void propagate_randomizedCrossCheckAgainstBruteForceGac() {
        var random = new Random(7);

        for (int trial = 0; trial < 300; trial++) {
            int k = 2 + random.nextInt(3); // 2..4 vectors
            int m = 1 + random.nextInt(2); // length 1..2
            List<List<Variable<Integer>>> vectors = new ArrayList<>();
            List<List<List<Integer>>> domLists = new ArrayList<>(); // [vector][position] -> values
            Map<Variable<?>, Domain<?>> domains = new HashMap<>();
            for (int v = 0; v < k; v++) {
                List<Variable<Integer>> vector = new ArrayList<>();
                List<List<Integer>> vectorDoms = new ArrayList<>();
                for (int p = 0; p < m; p++) {
                    Variable<Integer> var = Variable.Factory.INSTANCE.create("t" + trial + "_v" + v + "_p" + p);
                    vector.add(var);
                    Set<Integer> dom = new HashSet<>();
                    while (dom.isEmpty()) {
                        for (int val = 0; val < 3; val++) if (random.nextBoolean()) dom.add(val);
                    }
                    List<Integer> domList = new ArrayList<>(dom);
                    vectorDoms.add(domList);
                    domains.put(var, buildFrom(domList));
                }
                vectors.add(vector);
                domLists.add(vectorDoms);
            }

            var constraint = DistinctVectorsConstraint.of(vectors);
            int[][] current = new int[k][m];
            boolean bruteForceFeasible = anyCompletionSatisfies(domLists, current, -1, -1, -1, 0, 0);
            var result = constraint.propagate(domains);

            if (!bruteForceFeasible) {
                // Under-detecting infeasibility is acceptable (see method Javadoc); nothing further
                // to check once there's genuinely no solution to compare pruning against.
                continue;
            }
            assertThat(result).as("trial %d: a solution exists, must not be reported infeasible, domLists=%s",
                    trial, domLists).isPresent();

            for (int v = 0; v < k; v++) {
                for (int p = 0; p < m; p++) {
                    Set<Integer> expected = new HashSet<>();
                    for (Integer val : domLists.get(v).get(p)) {
                        if (anyCompletionSatisfies(domLists, current, v, p, val, 0, 0)) expected.add(val);
                    }
                    Variable<?> var = vectors.get(v).get(p);
                    Domain<?> actualDomain = result.get().getOrDefault(var, domains.get(var));
                    @SuppressWarnings("unchecked")
                    Set<Integer> actual = new HashSet<>(((DiscreteDomain<Integer>) actualDomain).toList());
                    assertThat(actual).as("trial %d, vector %d position %d: domLists=%s -- must retain every "
                                    + "GAC-consistent value (may retain more)", trial, v, p, domLists)
                            .containsAll(expected);
                }
            }
        }
    }

    private static Domain<Integer> buildFrom(List<Integer> values) {
        return io.github.rcrida.jcsp.domains.NumericDiscreteDomain.of(values.toArray(new Integer[0]));
    }

    /**
     * Exhaustive search over vector index {@code v} then position {@code p} within it: does some
     * assignment exist, drawn from each position's own domain (with {@code fixedV/fixedP} forced to
     * {@code fixedVal} instead), keeping every pair of vectors distinct?
     */
    private static boolean anyCompletionSatisfies(List<List<List<Integer>>> domLists, int[][] current,
                                                    int fixedV, int fixedP, int fixedVal, int v, int p) {
        int k = domLists.size(), m = domLists.get(0).size();
        if (v == k) {
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    boolean differs = false;
                    for (int pos = 0; pos < m; pos++) {
                        if (current[i][pos] != current[j][pos]) { differs = true; break; }
                    }
                    if (!differs) return false;
                }
            }
            return true;
        }
        int nextV = p + 1 == m ? v + 1 : v;
        int nextP = p + 1 == m ? 0 : p + 1;
        if (v == fixedV && p == fixedP) {
            current[v][p] = fixedVal;
            return anyCompletionSatisfies(domLists, current, fixedV, fixedP, fixedVal, nextV, nextP);
        }
        for (Integer val : domLists.get(v).get(p)) {
            current[v][p] = val;
            if (anyCompletionSatisfies(domLists, current, fixedV, fixedP, fixedVal, nextV, nextP)) return true;
        }
        return false;
    }
}
