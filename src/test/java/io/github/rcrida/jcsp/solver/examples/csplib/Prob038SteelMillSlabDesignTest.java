package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Steel Mill Slab Design (CSPLib prob038), modelled directly from CSPLib's reference model
 * ({@code Problems/prob038/models/SteelMillSlab.py} on GitHub):
 * <ul>
 *   <li><b>Load/capacity</b>: {@code sb[i]} is the slab producing order {@code i}; a slab's total
 *       assigned order size must not exceed its capacity — exactly {@code binPackingConstraint}'s
 *       semantics, matching the reference model's
 *       {@code [sb[i]==j for i in orders] * sizes == ld[j]} load constraint composed with its
 *       capacity check.</li>
 *   <li><b>{@code ld[j]} as a real linked variable</b>: {@code ld[j] == sum(sizes[i] : sb[i]==j)},
 *       matching the reference model's own {@code y}/{@code ld} construction, via {@code
 *       linearConstraint}'s target-variable overload (added specifically to close this gap — see
 *       {@code NumericBounds#propagateWeightedSumVsTarget}'s Javadoc). Getting from {@code sb[i]}
 *       (categorical: which slab) to weighted {@code 0}/{@code size[i]} terms needs one boolean
 *       indicator per (order, slab) pair, {@link #INDICATOR}, reified from {@code sb[i]==j} via
 *       {@code reifyConstraint} — whose first parameter is hard-typed {@code Variable<Boolean>},
 *       so a second reification per pair, {@link #Y_INT}, channels that same boolean to an
 *       equivalent {@code Variable<Integer>} in {@code \{0,1\}} (reifying the <em>same</em>
 *       indicator against two different body constraints transitively links them), which {@code
 *       linearConstraint} can then use directly as a coefficient variable — {@code
 *       linearConstraint} requires every coefficient variable to share {@code target}'s numeric
 *       type, and {@code Boolean} doesn't extend {@code Number}. The reference model doesn't need
 *       this second step since PyCSP3 doesn't distinguish {@code Boolean} from {@code 0}/{@code 1}
 *       {@code Integer}; jcsp's stricter type system does.</li>
 *   <li><b>Colour limit</b>: at most 2 distinct order colours per slab, matching
 *       {@code Sum(disjunction(sb[i]==j for i in g) for g in colorGroups) <= 2}. No dedicated
 *       constraint type exists for this "at most K distinct groups represented" shape, so it's
 *       expressed via {@code predicateConstraint} directly over the assignment — final-check only
 *       (no incremental propagation, per {@code PredicateConstraint}'s own contract), acceptable
 *       at this instance size.</li>
 *   <li><b>Objective</b>: minimise total loss (wasted capacity) summed over slabs actually used,
 *       matching {@code minimize(Sum(ls))} — computed directly from the assignment in {@link
 *       #totalLoss}, the same pattern {@code Prob075ProductMatrixTspTest}/{@code
 *       Prob061JobShopSchedulingTest} use for their own objectives. {@code ld} is a real linked
 *       variable now (see above), but {@code ls} (per-slab loss) still isn't materialised as one —
 *       loss is a simple closed form ({@code capacity - load} for a used slab, {@code 0} for an
 *       unused one, since this instance uses a single fixed capacity — see below) with no further
 *       use elsewhere in the model, so there's nothing to gain by also giving it a CSP variable.</li>
 *   <li><b>Symmetry breaking</b>: {@code sb[i] <= sb[j]} for every pair of orders with identical
 *       (size, colour), matching the reference model's order-identity symmetry constraint exactly
 *       (via {@code comparatorConstraint}) — <em>and</em>, now that {@code ld} is a real variable,
 *       {@code Decreasing(ld)} (slabs ordered by non-increasing load, breaking slab-relabelling
 *       symmetry) via {@code decreasingConstraint}, matching the reference model's other
 *       symmetry-breaking rule exactly. Neither rule changes the optimal loss value or problem
 *       semantics (CSPLib's own source tags both {@code tag(symmetry-breaking)}, distinct from the
 *       {@code satisfy(...)} blocks that define actual feasibility) — only solver performance.</li>
 * </ul>
 * <p>
 * The only remaining simplification relative to the full reference model: it lets each slab choose
 * its capacity from a discrete set of standard slab sizes (minimising per-slab loss via a lookup
 * table). This instance uses a single fixed capacity for every slab instead — still a
 * mathematically valid instantiation of the general model (CSPLib's {@code capacities} parameter is
 * just data; nothing in the model requires more than one value), and it collapses the loss table to
 * the simple closed form {@code loss = capacity - load} for a used slab, {@code 0} for an unused
 * one — which is what {@link #totalLoss} computes.
 * <p>
 * CSPLib's own published instance ({@code 111Orders.txt}) is far too large for a fast unit test,
 * so a small instance is constructed instead, the same approach used for {@code
 * Prob034WarehouseLocationTest}.
 * <p>
 * Orders: sizes {@code [5,5,4,4,3,3]} (colours A,A,B,B,C,C), capacity 10 per slab. Since every used
 * slab has the same fixed capacity, {@code totalLoss = capacity * slabsUsed - totalOrderSize}
 * always (loss from unused slabs is defined as 0, and the sum of all loads, used slabs or not,
 * always equals the fixed total order size) — so minimising loss is arithmetically equivalent to
 * minimising the number of slabs used here. Total size is 24; capacity 10 means 2 slabs
 * (capacity 20) cannot fit everything, so the minimum is 3 slabs, giving
 * {@code totalLoss = 10*3 - 24 = 6} — provable directly by arithmetic, no exhaustive search
 * needed, and confirmed achievable (e.g. {5,5}, {4,4}, {3,3} each totalling <=10 with only one
 * colour per slab, satisfying the colour limit trivially). Adding {@code ld}/symmetry-breaking
 * doesn't change this: they're redundant/symmetry-breaking, not new correctness constraints.
 */
public class Prob038SteelMillSlabDesignTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int NUM_ORDERS = 6;
    static final int NUM_SLABS = NUM_ORDERS; // worst case: one order per slab, matching the reference model
    static final int CAPACITY = 10;
    static final List<Integer> ORDER_SIZES = List.of(5, 5, 4, 4, 3, 3);
    static final List<Character> ORDER_COLORS = List.of('A', 'A', 'B', 'B', 'C', 'C');

    static final int OPTIMAL_LOSS = 6;

    static final List<Variable<Integer>> SB = IntStream.range(0, NUM_ORDERS)
            .mapToObj(i -> F.<Integer>create("sb" + i))
            .toList();
    static final List<Variable<Integer>> LD = IntStream.range(0, NUM_SLABS)
            .mapToObj(j -> F.<Integer>create("ld" + j))
            .toList();
    /** {@code INDICATOR[i][j]} — boolean, reified as {@code sb[i]==j}. */
    static final Variable<Boolean>[][] INDICATOR = buildIndicatorArray();
    /** {@code Y_INT[i][j]} — integer {0,1}, channelled to equal {@code INDICATOR[i][j]}. */
    static final Variable<Integer>[][] Y_INT = buildYIntArray();

    @SuppressWarnings("unchecked")
    static Variable<Boolean>[][] buildIndicatorArray() {
        Variable<Boolean>[][] arr = new Variable[NUM_ORDERS][NUM_SLABS];
        for (int i = 0; i < NUM_ORDERS; i++) {
            for (int j = 0; j < NUM_SLABS; j++) arr[i][j] = F.create("ind" + i + "_" + j);
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    static Variable<Integer>[][] buildYIntArray() {
        Variable<Integer>[][] arr = new Variable[NUM_ORDERS][NUM_SLABS];
        for (int i = 0; i < NUM_ORDERS; i++) {
            for (int j = 0; j < NUM_SLABS; j++) arr[i][j] = F.create("y" + i + "_" + j);
        }
        return arr;
    }

    static final ConstraintSatisfactionProblem CSP = buildCsp();

    static ConstraintSatisfactionProblem buildCsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        var slabDomain = IntRangeDomain.of(0, NUM_SLABS - 1);
        SB.forEach(sb -> builder.variableDomain(sb, slabDomain));
        LD.forEach(ld -> builder.variableDomain(ld, IntRangeDomain.of(0, CAPACITY)));
        for (int i = 0; i < NUM_ORDERS; i++) {
            for (int j = 0; j < NUM_SLABS; j++) {
                builder.variableDomain(INDICATOR[i][j], BooleanDomain.INSTANCE);
                builder.variableDomain(Y_INT[i][j], IntRangeDomain.of(0, 1));
                builder.reifyConstraint(INDICATOR[i][j], UnaryValueConstraint.of(SB.get(i), j));
                builder.reifyConstraint(INDICATOR[i][j], UnaryValueConstraint.of(Y_INT[i][j], 1));
            }
        }

        List<Integer> capacityPerSlab = IntStream.range(0, NUM_SLABS).mapToObj(j -> CAPACITY).toList();
        builder.binPackingConstraint(SB, ORDER_SIZES, capacityPerSlab);

        // ld[j] == sum(sizes[i] : sb[i]==j), via the reified Y_INT indicators
        for (int j = 0; j < NUM_SLABS; j++) {
            Map<Variable<Integer>, Integer> coefficients = new HashMap<>();
            for (int i = 0; i < NUM_ORDERS; i++) coefficients.put(Y_INT[i][j], ORDER_SIZES.get(i));
            builder.linearConstraint(coefficients, Operator.EQ, LD.get(j));
        }

        // colour limit: at most 2 distinct colours per slab
        builder.predicateConstraint(new HashSet<>(SB), Prob038SteelMillSlabDesignTest::respectsColorLimit);

        // symmetry breaking: identical (size, colour) orders are interchangeable
        for (int i = 0; i < NUM_ORDERS; i++) {
            for (int j = i + 1; j < NUM_ORDERS; j++) {
                if (ORDER_SIZES.get(i).equals(ORDER_SIZES.get(j)) && ORDER_COLORS.get(i).equals(ORDER_COLORS.get(j))) {
                    builder.comparatorConstraint(SB.get(i), Operator.LEQ, SB.get(j));
                }
            }
        }
        // symmetry breaking: slabs ordered by non-increasing load (breaks slab-relabelling symmetry)
        builder.decreasingConstraint(LD);

        return builder.build();
    }

    static boolean respectsColorLimit(Assignment a) {
        for (Variable<Integer> sb : SB) {
            if (a.getValue(sb).isEmpty()) return true; // optimistic until fully assigned
        }
        for (int slab = 0; slab < NUM_SLABS; slab++) {
            HashSet<Character> colors = new HashSet<>();
            for (int i = 0; i < NUM_ORDERS; i++) {
                if (a.getValue(SB.get(i)).orElseThrow() == slab) colors.add(ORDER_COLORS.get(i));
            }
            if (colors.size() > 2) return false;
        }
        return true;
    }

    /** Trivially valid lower bound (0) until every order is assigned, since loss is never negative. */
    static double totalLoss(Assignment a) {
        for (Variable<Integer> sb : SB) {
            if (a.getValue(sb).isEmpty()) return 0.0;
        }
        int[] load = new int[NUM_SLABS];
        for (int i = 0; i < NUM_ORDERS; i++) {
            load[a.getValue(SB.get(i)).orElseThrow()] += ORDER_SIZES.get(i);
        }
        int loss = 0;
        for (int l : load) if (l > 0) loss += CAPACITY - l;
        return loss;
    }

    @Test
    void optimize_findsMinimumLossMatchingArithmeticBound() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob038SteelMillSlabDesignTest::totalLoss).getSolution();
        assertThat(result).isPresent();
        assertThat(totalLoss(result.get())).isCloseTo(OPTIMAL_LOSS, offset(1e-9));
    }

    @Test
    void optimalSolutionRespectsCapacityAndColorLimits() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob038SteelMillSlabDesignTest::totalLoss)
                .getSolution().orElseThrow();
        int[] load = new int[NUM_SLABS];
        for (int i = 0; i < NUM_ORDERS; i++) {
            load[result.getValue(SB.get(i)).orElseThrow()] += ORDER_SIZES.get(i);
        }
        for (int l : load) assertThat(l).isLessThanOrEqualTo(CAPACITY);
        assertThat(Arrays.stream(load).sum()).isEqualTo(ORDER_SIZES.stream().mapToInt(Integer::intValue).sum());
        assertThat(respectsColorLimit(result)).isTrue();
    }

    @Test
    void optimalSolutionRespectsOrderSymmetryBreaking() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob038SteelMillSlabDesignTest::totalLoss)
                .getSolution().orElseThrow();
        assertThat(result.getValue(SB.get(0)).orElseThrow()).isLessThanOrEqualTo(result.getValue(SB.get(1)).orElseThrow());
        assertThat(result.getValue(SB.get(2)).orElseThrow()).isLessThanOrEqualTo(result.getValue(SB.get(3)).orElseThrow());
        assertThat(result.getValue(SB.get(4)).orElseThrow()).isLessThanOrEqualTo(result.getValue(SB.get(5)).orElseThrow());
    }

    @Test
    void optimalSolutionRespectsLoadSymmetryBreaking_andLdMatchesActualLoad() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob038SteelMillSlabDesignTest::totalLoss)
                .getSolution().orElseThrow();
        int[] load = new int[NUM_SLABS];
        for (int i = 0; i < NUM_ORDERS; i++) {
            load[result.getValue(SB.get(i)).orElseThrow()] += ORDER_SIZES.get(i);
        }
        // ld[j] is a real linked variable now -- confirm it actually matches the load sb implies
        for (int j = 0; j < NUM_SLABS; j++) {
            assertThat(result.getValue(LD.get(j)).orElseThrow()).isEqualTo(load[j]);
        }
        for (int j = 1; j < NUM_SLABS; j++) {
            assertThat(result.getValue(LD.get(j - 1)).orElseThrow()).isGreaterThanOrEqualTo(result.getValue(LD.get(j)).orElseThrow());
        }
    }

    @Test
    void getSolutions_returnsImprovingLosses() {
        val improving = Solver.Factory.INSTANCE.createSolver(CSP, Prob038SteelMillSlabDesignTest::totalLoss)
                .getSolutions().toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(totalLoss(improving.get(i))).isLessThan(totalLoss(improving.get(i - 1)));
        }
        assertThat(totalLoss(improving.getLast())).isCloseTo(OPTIMAL_LOSS, offset(1e-9));
    }
}
