package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LexChainConstraintTest {
    final Variable.Factory F = new Variable.Factory();

    Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
    Variable<Integer> y1 = F.create("y1"), y2 = F.create("y2");
    Variable<Integer> z1 = F.create("z1"), z2 = F.create("z2");

    private static Domain<Integer> set(int... values) {
        return NumericDiscreteDomain.of(Arrays.stream(values).boxed().toArray(Integer[]::new));
    }

    private static List<Integer> valuesOf(Domain<?> domain) {
        return ((DiscreteDomain<?>) domain).toList().stream().map(v -> (Integer) v).sorted().toList();
    }

    private static boolean contains(Domain<?> domain, int value) {
        return valuesOf(domain).contains(value);
    }

    // --- satisfaction ---

    @Test
    void isSatisfiedBy_everyConsecutivePairMustBeOrdered() {
        var leq = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), Operator.LEQ);
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 9, y1, 2, y2, 0, z1, 2, z2, 0)))).isTrue();
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 9, y1, 2, y2, 0, z1, 2, z2, 5)))).isTrue();
        // the last pair is out of order even though the first is fine
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 9, y1, 2, y2, 5, z1, 2, z2, 0)))).isFalse();
        // and the other way round
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 3, x2, 0, y1, 2, y2, 5, z1, 4, z2, 0)))).isFalse();
    }

    @Test
    void isSatisfiedBy_strictOperatorRejectsEqualRows() {
        var lt = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2)), Operator.LT);
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 2, y1, 1, y2, 2)))).isFalse();
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 2, y1, 1, y2, 3)))).isTrue();
    }

    @Test
    void isSatisfiedBy_unassignedPositionIsOptimistic() {
        var leq = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2)), Operator.LEQ);
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 5, y1, 1)))).isFalse(); // decided at position 0
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x1, 1, y1, 1, x2, 9)))).isTrue(); // y2 unassigned
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(x2, 9, y2, 1)))).isTrue(); // position 0 unassigned
    }

    @Test
    void isSatisfiedBy_agreesWithTheChainOfPairwiseLexConstraints() {
        Random random = new Random(11);
        for (Operator operator : List.of(Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT)) {
            var chain = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), operator);
            var pairA = LexConstraint.of(List.of(x1, x2), operator, List.of(y1, y2));
            var pairB = LexConstraint.of(List.of(y1, y2), operator, List.of(z1, z2));
            for (int trial = 0; trial < 300; trial++) {
                Map<Variable<?>, Object> tuple = new HashMap<>();
                for (Variable<Integer> v : List.of(x1, x2, y1, y2, z1, z2)) {
                    if (random.nextInt(10) > 0) tuple.put(v, random.nextInt(3)); // sometimes unassigned
                }
                var assignment = Assignment.of(tuple);
                assertThat(chain.isSatisfiedBy(assignment))
                        .as("%s %s", operator, tuple)
                        .isEqualTo(pairA.isSatisfiedBy(assignment) && pairB.isSatisfiedBy(assignment));
            }
        }
    }

    // --- propagation ---

    @Test
    void propagate_twoRows_tailThatCannotBeOrderedMakesTheFirstPositionStrict() {
        // [x1,x2] <= [y1,y2]: x2=[5..9] always exceeds y2=[0..3], so x1 = y1 would leave the tail violated.
        var c = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2)), Operator.LEQ);
        var result = c.propagate(Map.of(
                x1, IntRangeDomain.of(1, 5), y1, IntRangeDomain.of(1, 4),
                x2, IntRangeDomain.of(5, 9), y2, IntRangeDomain.of(0, 3))).orElseThrow();
        assertThat(result).containsOnlyKeys(x1, y1);
        assertThat(result.get(x1)).isEqualTo(IntRangeDomain.of(1, 3));
        assertThat(result.get(y1)).isEqualTo(IntRangeDomain.of(2, 4));
    }

    @Test
    void propagate_threeRows_boundsTravelAlongTheWholeChain() {
        // a <= b <= c with a >= 3 and c <= 4 leaves all three in [3..4], including the middle one that
        // has no bound of its own.
        var a = F.<Integer>create("a");
        var b = F.<Integer>create("b");
        var c = F.<Integer>create("c");
        var chain = LexChainConstraint.of(List.of(List.of(a), List.of(b), List.of(c)), Operator.LEQ);
        var result = chain.propagate(Map.of(
                a, IntRangeDomain.of(3, 5), b, IntRangeDomain.of(0, 9), c, IntRangeDomain.of(0, 4))).orElseThrow();
        assertThat(result.get(a)).isEqualTo(IntRangeDomain.of(3, 4));
        assertThat(result.get(b)).isEqualTo(IntRangeDomain.of(3, 4));
        assertThat(result.get(c)).isEqualTo(IntRangeDomain.of(3, 4));
    }

    @Test
    void propagate_aGapInADomainMakesATieImpossible() {
        // [a0,a1] <= [b0,b1]: a0 in {2,5}, b0 in {2,3,5}; a1=[7..9] is above b1=[0..3], so a tie at
        // position 0 would fail. a0 must be below b0, which rules a0 = 5 out (nothing in b0 is above 5)
        // and leaves b0 in {3,5}. Bounds alone cannot see this: 5 is within b0's range.
        var a0 = F.<Integer>create("a0");
        var a1 = F.<Integer>create("a1");
        var b0 = F.<Integer>create("b0");
        var b1 = F.<Integer>create("b1");
        var c = LexChainConstraint.of(List.of(List.of(a0, a1), List.of(b0, b1)), Operator.LEQ);
        var result = c.propagate(Map.of(
                a0, set(2, 5), a1, IntRangeDomain.of(7, 9), b0, set(2, 3, 5), b1, IntRangeDomain.of(0, 3))).orElseThrow();
        assertThat(valuesOf(result.get(a0))).containsExactly(2);
        assertThat(valuesOf(result.get(b0))).containsExactly(3, 5);
        assertThat(result).doesNotContainKeys(a1, b1);
    }

    @Test
    void propagate_descendingOperatorGivesTheSameNarrowingWithTheRowsReversed() {
        // The previous case written the other way round: [b0,b1] >= [a0,a1].
        var a0 = F.<Integer>create("a0");
        var a1 = F.<Integer>create("a1");
        var b0 = F.<Integer>create("b0");
        var b1 = F.<Integer>create("b1");
        var c = LexChainConstraint.of(List.of(List.of(b0, b1), List.of(a0, a1)), Operator.GEQ);
        var result = c.propagate(Map.of(
                a0, set(2, 5), a1, IntRangeDomain.of(7, 9), b0, set(2, 3, 5), b1, IntRangeDomain.of(0, 3))).orElseThrow();
        assertThat(valuesOf(result.get(a0))).containsExactly(2);
        assertThat(valuesOf(result.get(b0))).containsExactly(3, 5);
    }

    @Test
    void propagate_domainsThatDoNotIterateInOrder_stillFindTheNearestValueAboveAndBelow() {
        // [a] < [b] with a = {3} and b = {6,4,5,3} (in that iteration order): b must be above 3, and the
        // nearest value above 3 is found by scanning, not by assuming the order.
        var a = F.<Integer>create("a");
        var b = F.<Integer>create("b");
        var chain = LexChainConstraint.of(List.of(List.of(a), List.of(b)), Operator.LT);
        var above = chain.propagate(Map.of(a, set(3), b, set(6, 4, 5, 3))).orElseThrow();
        assertThat(valuesOf(above.get(b))).containsExactly(4, 5, 6);
        // And the mirror: a = {3,5,4,7} against b = {7}: a must be below 7.
        var below = chain.propagate(Map.of(a, set(3, 5, 4, 7), b, set(7))).orElseThrow();
        assertThat(valuesOf(below.get(a))).containsExactly(3, 4, 5);
    }

    @Test
    void propagate_strictChain_forcesAStrictlyIncreasingRun() {
        var a = F.<Integer>create("a");
        var b = F.<Integer>create("b");
        var c = F.<Integer>create("c");
        var chain = LexChainConstraint.of(List.of(List.of(a), List.of(b), List.of(c)), Operator.LT);
        var d = IntRangeDomain.of(0, 2);
        var result = chain.propagate(Map.of(a, d, b, d, c, d)).orElseThrow();
        assertThat(valuesOf(result.get(a))).containsExactly(0);
        assertThat(valuesOf(result.get(b))).containsExactly(1);
        assertThat(valuesOf(result.get(c))).containsExactly(2);
    }

    @Test
    void propagate_strictChainLongerThanTheDomainAllows_isInfeasible() {
        var a = F.<Integer>create("a");
        var b = F.<Integer>create("b");
        var c = F.<Integer>create("c");
        var chain = LexChainConstraint.of(List.of(List.of(a), List.of(b), List.of(c)), Operator.LT);
        var d = IntRangeDomain.of(0, 1);
        assertThat(chain.propagate(Map.of(a, d, b, d, c, d))).isEmpty();
    }

    @Test
    void propagate_aVariableSharedByTwoRows_canEmptyItsDomain() {
        // [v] < [v] over {1,2}: the first row needs v below the second's, the second above the first's,
        // and the same variable cannot satisfy both.
        var v = F.<Integer>create("v");
        var chain = LexChainConstraint.of(List.of(List.of(v), List.of(v)), Operator.LT);
        assertThat(chain.propagate(Map.of(v, IntRangeDomain.of(1, 2)))).isEmpty();
    }

    @Test
    void propagate_continuousDomains_arePassedOver() {
        var a = F.<Double>create("a");
        var b = F.<Double>create("b");
        var chain = LexChainConstraint.of(List.of(List.of(a), List.of(b)), Operator.LEQ);
        assertThat(chain.propagate(Map.of(a, IntervalDomain.of(5.0, 9.0), b, IntervalDomain.of(0.0, 3.0))).orElseThrow())
                .isEmpty();
    }

    @Test
    void aSingleRow_isTriviallyOrdered() {
        var chain = LexChainConstraint.of(List.of(List.of(x1, x2)), Operator.LT);
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(0, 3), x2, IntRangeDomain.of(0, 3));
        assertThat(chain.propagate(domains).orElseThrow()).isEmpty();
        assertThat(chain.isSatisfiedBy(Assignment.of(Map.of(x1, 1, x2, 2)))).isTrue();
    }

    @Test
    void propagate_rowsOfNoPositions_areAllEqual() {
        var leq = LexChainConstraint.<Integer>of(List.of(List.of(), List.of()), Operator.LEQ);
        var lt = LexChainConstraint.<Integer>of(List.of(List.of(), List.of()), Operator.LT);
        assertThat(leq.propagate(Map.of()).orElseThrow()).isEmpty();
        assertThat(lt.propagate(Map.of())).isEmpty();
    }

    // --- explanation ---

    @Test
    void explainInfeasible_citesEveryVariableWhenAllAreSingletons() {
        var c = LexChainConstraint.of(List.of(List.of(x1), List.of(y1)), Operator.LT);
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(x1, 2, y1, 2)));
    }

    @Test
    void explainInfeasible_hasNoReasonToGiveWhileAnyVariableIsOpen() {
        var c = LexChainConstraint.of(List.of(List.of(x1), List.of(y1)), Operator.LT);
        var domains = Map.<Variable<?>, Domain<?>>of(x1, IntRangeDomain.of(2, 3), y1, IntRangeDomain.of(2, 2));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- construction ---

    @Test
    void of_needsARowOfTheSameLengthAsTheOthersAndAnOrderingOperator() {
        assertThatThrownBy(() -> LexChainConstraint.<Integer>of(List.of(), Operator.LEQ))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1)), Operator.LEQ))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LexChainConstraint.of(List.of(List.of(x1), List.of(y1)), Operator.EQ))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void equalChainsAreEqual_andTheRelationListsTheRows() {
        var one = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), Operator.LEQ);
        var two = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), Operator.LEQ);
        var other = LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), Operator.LT);
        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two).isNotEqualTo(other);
        assertThat(one.getRelation()).isEqualTo("[x1, x2] <= [y1, y2] <= [z1, z2]");
    }

    // --- soundness, strength and fixpoint, over random small chains ---

    private static final int[][] DOMAINS = {{0}, {1}, {2}, {3}, {0, 1}, {1, 2}, {2, 3}, {0, 2}, {1, 3}, {0, 3},
            {0, 1, 2}, {1, 2, 3}, {0, 1, 3}, {0, 2, 3}, {0, 1, 2, 3}};

    private List<Variable<Integer>> all() {
        return List.of(x1, x2, y1, y2, z1, z2);
    }

    private LexChainConstraint<Integer> chain(Operator operator) {
        return LexChainConstraint.of(List.of(List.of(x1, x2), List.of(y1, y2), List.of(z1, z2)), operator);
    }

    private Map<Variable<?>, Domain<?>> randomDomains(Random random) {
        Map<Variable<?>, Domain<?>> domains = new HashMap<>();
        for (Variable<Integer> v : all()) domains.put(v, set(DOMAINS[random.nextInt(DOMAINS.length)]));
        return domains;
    }

    @Test
    void propagate_neverLosesASolution_andExplanationsAreValidNogoods() {
        // Brute force over {0..3}^6: every tuple the chain accepts and the domains allow must survive the
        // narrowing, an infeasible verdict means there was no such tuple, and any nogood offered for it
        // excludes only tuples the chain rejects.
        var random = new Random(20261030);
        for (Operator operator : List.of(Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT)) {
            var c = chain(operator);
            for (int trial = 0; trial < 400; trial++) {
                var domains = randomDomains(random);
                var result = c.propagate(domains);
                var nogood = result.isEmpty() ? c.explainInfeasible(domains) : Optional.<NogoodConstraint>empty();
                boolean anySolution = false;
                for (int code = 0; code < 4096; code++) {
                    Map<Variable<?>, Object> tuple = new HashMap<>();
                    boolean inDomains = true;
                    for (int v = 0; v < 6; v++) {
                        int value = (code >> (2 * v)) & 3;
                        tuple.put(all().get(v), value);
                        inDomains &= contains(domains.get(all().get(v)), value);
                    }
                    var assignment = Assignment.of(tuple);
                    boolean accepted = c.isSatisfiedBy(assignment);
                    if (inDomains && accepted) {
                        anySolution = true;
                        assertThat(result).as("%s lost solution %s", operator, tuple).isPresent();
                        for (int v = 0; v < 6; v++) {
                            Domain<?> narrowed = result.get().getOrDefault(all().get(v), domains.get(all().get(v)));
                            assertThat(contains(narrowed, (code >> (2 * v)) & 3)).isTrue();
                        }
                    }
                    if (nogood.isPresent() && !nogood.get().isSatisfiedBy(assignment)) {
                        assertThat(accepted).as("%s nogood excluded an accepted tuple %s", operator, tuple).isFalse();
                    }
                }
                if (anySolution) assertThat(result).isPresent();
            }
        }
    }

    @Test
    void propagate_isAtLeastAsStrongAsTheChainOfPairwiseConstraints() {
        // Run the two pairwise lex constraints to their joint fixpoint and compare: whatever they leave
        // out, the chain leaves out too, and it is infeasible whenever they are.
        var random = new Random(31);
        for (Operator operator : List.of(Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT)) {
            var c = chain(operator);
            var pairs = List.of(LexConstraint.of(List.of(x1, x2), operator, List.of(y1, y2)),
                    LexConstraint.of(List.of(y1, y2), operator, List.of(z1, z2)));
            for (int trial = 0; trial < 400; trial++) {
                var domains = randomDomains(random);
                var chained = c.propagate(domains);
                Map<Variable<?>, Domain<?>> pairwise = new HashMap<>(domains);
                boolean pairwiseInfeasible = false;
                boolean changed = true;
                while (changed && !pairwiseInfeasible) {
                    changed = false;
                    for (var pair : pairs) {
                        var r = pair.propagate(pairwise);
                        if (r.isEmpty()) { pairwiseInfeasible = true; break; }
                        for (var entry : r.get().entrySet()) {
                            if (entry.getValue().size() != pairwise.get(entry.getKey()).size()) changed = true;
                            pairwise.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                if (pairwiseInfeasible) {
                    assertThat(chained).as("%s pairwise infeasible on %s", operator, domains).isEmpty();
                } else if (chained.isPresent()) {
                    for (Variable<Integer> v : all()) {
                        Domain<?> narrowed = chained.get().getOrDefault(v, domains.get(v));
                        for (int value : valuesOf(narrowed)) {
                            assertThat(contains(pairwise.get(v), value))
                                    .as("%s %s kept %d that pairwise removed, from %s", operator, v, value, domains)
                                    .isTrue();
                        }
                    }
                }
            }
        }
    }

    @Test
    void propagate_reachesItsOwnFixpoint() {
        var random = new Random(7);
        for (Operator operator : List.of(Operator.LEQ, Operator.LT, Operator.GEQ, Operator.GT)) {
            var c = chain(operator);
            for (int trial = 0; trial < 400; trial++) {
                var domains = randomDomains(random);
                var first = c.propagate(domains);
                if (first.isEmpty()) continue;
                Map<Variable<?>, Domain<?>> narrowed = new HashMap<>(domains);
                narrowed.putAll(first.get());
                assertThat(c.propagate(narrowed).orElseThrow()).as("%s second call on %s", operator, narrowed).isEmpty();
            }
        }
    }

    // --- through the solver ---

    private long solutions(Operator operator) {
        var domain = IntRangeDomain.of(1, 2);
        List<List<Variable<Integer>>> rows = new ArrayList<>();
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < 3; i++) {
            List<Variable<Integer>> row = new ArrayList<>();
            for (int j = 0; j < 2; j++) row.add(builder.createVariable("r" + i + "_" + j, domain));
            rows.add(row);
        }
        return Solver.Factory.INSTANCE.createSolver(builder.lexChainConstraint(rows, operator).build())
                .getSolutions().count();
    }

    @Test
    void solver_countsTheOrderedChains() {
        // Four vectors over {1,2}^2. Non-decreasing chains of three: C(4+3-1, 3) = 20; strictly: C(4, 3) = 4.
        assertThat(solutions(Operator.LEQ)).isEqualTo(20);
        assertThat(solutions(Operator.GEQ)).isEqualTo(20);
        assertThat(solutions(Operator.LT)).isEqualTo(4);
        assertThat(solutions(Operator.GT)).isEqualTo(4);
    }
}
