package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Warehouse Location Problem (CSPLib prob034), "compact" variant: each store {@code i} is
 * supplied by exactly one warehouse {@code w[i]}, subject to per-warehouse capacity limits, and
 * the objective is {@code sum(supplyCost[i][w[i]]) + nvalue(w) * fixedCost} — total supply cost
 * plus a fixed cost charged once per <em>distinct</em> warehouse actually used, not once per
 * store. That second term is exactly {@code nValueConstraint}'s reason for existing: the naive
 * "assign every store to its individually cheapest warehouse" answer is provably not optimal
 * once opening a warehouse has its own cost, and only a genuine distinct-value count (not a
 * fixed set of per-value quotas, which {@code globalCardinalityConstraint} would require knowing
 * in advance) can express "minimise how many warehouses end up used at all".
 * <p>
 * Modelled directly from CSPLib's reference model
 * ({@code Problems/prob034/models/WarehouseLocation.py}'s {@code compact} variant on GitHub):
 * {@code Count(w, value=j) <= capacities[j]} per warehouse, and
 * {@code minimize(Sum(costs[i][w[i]]) + NValues(w) * cost)}.
 * <p>
 * Instance data (5 stores, 3 warehouses, capacity 5 each — generous enough that capacity never
 * binds, so the optimum is driven purely by the consolidation trade-off) is constructed rather
 * than taken from CSPLib's own published data files, which are 16-warehouse/50-store OR-Library
 * instances (`cap44.dat` etc.) too large for a fast unit test. The true optimum (25.0, using
 * exactly 2 of the 3 warehouses) was confirmed by exhaustive brute force over all
 * {@code 3^5 = 243} possible assignments — strictly better than the 29.0 a "cheapest warehouse
 * per store" greedy assignment would give, which is the whole point of the test.
 */
public class Prob034WarehouseLocationTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int NUM_STORES = 5;
    static final int NUM_WAREHOUSES = 3;
    static final int FIXED_COST = 8;
    static final int[] CAPACITY = {5, 5, 5};

    static final int[][] SUPPLY_COST = {
            {5, 1, 9},
            {5, 1, 9},
            {9, 5, 1},
            {9, 5, 1},
            {1, 9, 5},
    };

    static final List<Variable<Integer>> W = IntStream.range(0, NUM_STORES)
            .mapToObj(i -> F.<Integer>create("w" + i))
            .toList();
    static final Variable<Integer> COUNT = F.create("count");

    static final double OPTIMAL_COST = 25.0;
    static final int OPTIMAL_DISTINCT_WAREHOUSES = 2;

    static final ConstraintSatisfactionProblem CSP = buildCsp();

    static ConstraintSatisfactionProblem buildCsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        var warehouseDomain = IntRangeDomain.of(0, NUM_WAREHOUSES - 1);
        W.forEach(w -> builder.variableDomain(w, warehouseDomain));
        builder.variableDomain(COUNT, IntRangeDomain.of(1, NUM_WAREHOUSES));

        for (int j = 0; j < NUM_WAREHOUSES; j++) {
            builder.countConstraint(new HashSet<>(W), j, Operator.LEQ, CAPACITY[j]);
        }
        builder.nValueConstraint(new HashSet<>(W), COUNT);
        return builder.build();
    }

    static double totalCost(Assignment a) {
        double supply = 0;
        for (int store = 0; store < NUM_STORES; store++) {
            var w = a.getValue(W.get(store));
            if (w.isPresent()) supply += SUPPLY_COST[store][w.get()];
        }
        double fixedPart = a.getValue(COUNT).map(c -> c * (double) FIXED_COST).orElse(0.0);
        return supply + fixedPart;
    }

    @Test
    void optimize_consolidatesOntoFewerWarehousesThanGreedyAssignment() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob034WarehouseLocationTest::totalCost).getSolution();
        assertThat(result).isPresent();
        assertThat(totalCost(result.get())).isCloseTo(OPTIMAL_COST, offset(1e-9));
    }

    @Test
    void optimalSolutionUsesExactlyTwoDistinctWarehouses() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob034WarehouseLocationTest::totalCost).getSolution().orElseThrow();
        Set<Integer> distinctWarehouses = new HashSet<>();
        for (Variable<Integer> w : W) distinctWarehouses.add(result.getValue(w).orElseThrow());
        assertThat(distinctWarehouses).hasSize(OPTIMAL_DISTINCT_WAREHOUSES);
        assertThat(result.getValue(COUNT)).hasValue(OPTIMAL_DISTINCT_WAREHOUSES);
    }

    @Test
    void optimalSolutionRespectsWarehouseCapacities() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP, Prob034WarehouseLocationTest::totalCost).getSolution().orElseThrow();
        int[] load = new int[NUM_WAREHOUSES];
        for (Variable<Integer> w : W) load[result.getValue(w).orElseThrow()]++;
        for (int j = 0; j < NUM_WAREHOUSES; j++) {
            assertThat(load[j]).isLessThanOrEqualTo(CAPACITY[j]);
        }
    }

    @Test
    void getSolutions_returnsImprovingAssignments() {
        val improving = Solver.Factory.INSTANCE.createSolver(CSP, Prob034WarehouseLocationTest::totalCost).getSolutions().toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(totalCost(improving.get(i))).isLessThan(totalCost(improving.get(i - 1)));
        }
        assertThat(totalCost(improving.getLast())).isCloseTo(OPTIMAL_COST, offset(1e-9));
    }
}
