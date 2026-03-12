package org.jcsp.examples;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.AllDiffConstraint;
import org.jcsp.constraints.ExpressionConstraint;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.search.BacktrackingSearch;
import org.jcsp.search.order.LeastConstrainingValueOrderer;
import org.jcsp.search.selector.MinimumRemainingValuesSelector;
import org.jcsp.solver.SolverImpl;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class CryptarithmeticTest {
    @Test
    void twoPlusTwoEqualsFour() {
        val builder = ConstraintSatisfactionProblem.builder();
        val digitDomain = new IntRangeDomain(0, 9);
        val carryDomain = new IntRangeDomain(0, 1);
        val t = builder.createVariable("T", digitDomain);
        val w = builder.createVariable("W", digitDomain);
        val o = builder.createVariable("O", digitDomain);
        val f = builder.createVariable("F", digitDomain);
        val u = builder.createVariable("U", digitDomain);
        val r = builder.createVariable("R", digitDomain);
        val c1 = builder.createVariable("C1", carryDomain);
        val c2 = builder.createVariable("C2", carryDomain);
        val c3 = builder.createVariable("C3", carryDomain);

        builder
                .constraint(new AllDiffConstraint(Set.of(t, w, o, f, u, r)))
                .constraint(new ExpressionConstraint(Set.of(o, r, c1), assignment -> {
                    val O = (int) assignment.getValue(o).get();
                    val R = (int) assignment.getValue(r).get();
                    val C1 = (int) assignment.getValue(c1).get();
                    return O + O == R + 10 * C1;
                }))
                .constraint(new ExpressionConstraint(Set.of(c1, w, u, c2), assignment -> {
                    val C1 = (int) assignment.getValue(c1).get();
                    val W = (int) assignment.getValue(w).get();
                    val U = (int) assignment.getValue(u).get();
                    val C2 = (int) assignment.getValue(c2).get();
                    return C1 + W + W == U + 10 * C2;
                }))
                .constraint(new ExpressionConstraint(Set.of(c2, t, o, c3), assignment -> {
                    val C2 = (int) assignment.getValue(c2).get();
                    val T = (int) assignment.getValue(t).get();
                    val O = (int) assignment.getValue(o).get();
                    val C3 = (int) assignment.getValue(c3).get();
                    return C2 + T + T == O + 10 * C3;
                }))
                .constraint(new ExpressionConstraint(Set.of(c3, f), assignment -> {
                    val C3 = (int) assignment.getValue(c3).get();
                    val F = (int) assignment.getValue(f).get();
                    return C3 == F;
                }));
        val csp = builder.build();
        val solver = new SolverImpl(new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer()));
        System.out.println(solver.getSolution(csp));
    }
}
