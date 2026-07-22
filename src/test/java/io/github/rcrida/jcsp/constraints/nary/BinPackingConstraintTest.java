package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class BinPackingConstraintTest {
    @Mock Variable<Integer> v1;
    @Mock Variable<Integer> v2;
    @Mock Variable<Integer> v3;

    // 3 bins, capacity 10 each; item weight 6 -> any 2 items sharing a bin overload it (12 > 10)
    static final List<Integer> CAPACITIES = List.of(10, 10, 10);
    static final int WEIGHT = 6;

    BinPackingConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = BinPackingConstraint.of(List.of(v1, v2, v3), List.of(WEIGHT, WEIGHT, WEIGHT), CAPACITIES);
    }

    // --- isSatisfiedBy() ---

    @Test
    void fullyAssigned_fitsExactly_satisfied() {
        // bin0: v1(6) -> 6<=10; bin1: v2(6) -> 6<=10; bin2: v3(6) -> 6<=10
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 0, v2, 1, v3, 2)))).isTrue();
    }

    @Test
    void partialAssignment_alreadyOverloaded_earlyFailure() {
        // v1, v2 both in bin0: 6+6=12 > 10, even though v3 is still unassigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 0, v2, 0)))).isFalse();
    }

    @Test
    void partialAssignment_belowCapacity_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 0)))).isTrue();
    }

    // --- propagate() ---

    static final Domain<Integer> BIN_0 = IntRangeDomain.of(0, 0);
    static final Domain<Integer> BIN_1 = IntRangeDomain.of(1, 1);

    @Test
    void propagate_noOverload_noChange() {
        // v1={0} definite (load[0]=6); v2,v3={1,2} open, weight 6 -> neither bin1 nor bin2 has
        // any definite load yet, so 6<=10 in both -> nothing to prune
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, BIN_0, v2, NumericDiscreteDomain.of(1, 2), v3, NumericDiscreteDomain.of(1, 2));
        var result = constraint.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_prunesOneCandidateBin_staysOpen() {
        // v1={0} definite (load[0]=6); v2={0,1,2} open, weight 6 -> bin0: 6+6=12>10 (pruned),
        // bin1: 0+6=6<=10 (kept), bin2: 0+6=6<=10 (kept) -> v2 narrows to {1,2}, still open
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, NumericDiscreteDomain.of(0, 1, 2));
        var c = BinPackingConstraint.of(List.of(v1, v2), List.of(WEIGHT, WEIGHT), CAPACITIES);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(NumericDiscreteDomain.of(1, 2));
    }

    @Test
    void propagate_prunesDownToForcedSingleton() {
        // v1={0} definite (load[0]=6); v2={0,1} open, weight 6 -> bin0 pruned (12>10), bin1 kept
        // -> v2 forced to singleton {1}
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, NumericDiscreteDomain.of(0, 1));
        var c = BinPackingConstraint.of(List.of(v1, v2), List.of(WEIGHT, WEIGHT), CAPACITIES);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(BIN_1);
    }

    @Test
    void propagate_infeasible_definiteLoadExceedsCapacity() {
        // v1, v2 both definite in bin0: 6+6=12 > 10
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, BIN_0);
        var c = BinPackingConstraint.of(List.of(v1, v2), List.of(WEIGHT, WEIGHT), CAPACITIES);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_openItemDomainEmptiedByPruning() {
        // v1={0}, v2={1} definite (load[0]=6, load[1]=6, neither individually overloaded);
        // v3={0,1} open, weight 6 -> bin0: 6+6=12>10 (pruned), bin1: 6+6=12>10 (pruned) -> empty
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, BIN_1, v3, NumericDiscreteDomain.of(0, 1));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, BIN_0, v2, NumericDiscreteDomain.of(1, 2), v3, NumericDiscreteDomain.of(1, 2));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_definiteLoadOverload_alwaysSucceeds() {
        // Every cited variable is singleton by construction ("definite" means singleton), so this
        // branch is unconditionally sound -- no allSingletonReason-gating failure case exists here,
        // unlike GlobalCardinalityConstraint/NValueConstraint's analogous branches.
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, BIN_0);
        var c = BinPackingConstraint.of(List.of(v1, v2), List.of(WEIGHT, WEIGHT), CAPACITIES);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, 0, v2, 0)));
    }

    @Test
    void explainInfeasible_openItemDomainEmptiedByPruning_returnsEmptyReason() {
        // No bin's definiteLoad exceeds capacity individually (6<=10 in both bin0 and bin1) --
        // only the combined per-item pruning empties v3's domain, which is deliberately
        // unexplainable (see the class Javadoc).
        var domains = Map.<Variable<?>, Domain<?>>of(v1, BIN_0, v2, BIN_1, v3, NumericDiscreteDomain.of(0, 1));
        var result = constraint.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- toString() / of() ---

    @Test
    void testToString() {
        var c = BinPackingConstraint.of(List.of(v1, v2), List.of(3, 4), List.of(10, 10));
        assertThat(c.toString()).isEqualTo("<(v1, v2), binPacking(bins=2, items=2)>");
    }

    @Test
    void of_unequalListLengths_asserts() {
        assertThatThrownBy(() -> BinPackingConstraint.of(List.of(v1, v2), List.of(WEIGHT), CAPACITIES))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinPackingConstraint.of(List.of(v1, v2, v3), List.of(WEIGHT, WEIGHT, WEIGHT), CAPACITIES))
                .isEqualTo(constraint);
    }

    // --- solver integration: composes with nValueConstraint to minimise bins used ---

    @Test
    void solver_composesWithNValueConstraint_minimisesDistinctBinsUsed() {
        // 4 items of weight 3, 3 bins of capacity 6 (holds at most 2 items each) -> minimum
        // possible distinct bins is ceil(4/2)=2, and 2 bins can hold exactly 4 items (2+2), so
        // 2 is achievable and optimal.
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        Variable<Integer> x4 = Variable.Factory.INSTANCE.create("x4");
        Variable<Integer> count = Variable.Factory.INSTANCE.create("count");
        List<Variable<Integer>> items = List.of(x1, x2, x3, x4);

        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(0, 2)).variableDomain(x2, IntRangeDomain.of(0, 2))
                .variableDomain(x3, IntRangeDomain.of(0, 2)).variableDomain(x4, IntRangeDomain.of(0, 2))
                .variableDomain(count, IntRangeDomain.of(1, 3))
                .binPackingConstraint(items, List.of(3, 3, 3, 3), List.of(6, 6, 6))
                .nValueConstraint(new HashSet<>(items), count)
                .build();

        var objective = Solver.Factory.INSTANCE.createSolver(csp,
                (Assignment a) -> a.getValue(count).map(c -> (double) c).orElse(1.0));
        var solution = objective.getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(count)).hasValue(2);

        int[] load = new int[3];
        for (Variable<Integer> item : items) load[solution.get().getValue(item).orElseThrow()] += 3;
        for (int l : load) assertThat(l).isLessThanOrEqualTo(6);
    }
}
