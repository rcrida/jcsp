package org.jcsp.examples;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.AllDiffConstraint;
import org.jcsp.constraints.ExpressionConstraint;
import org.jcsp.domains.IntRangeDomain;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class CryptarithmeticTest {
    @Test
    void twoPlusTwoEqualsFour() {
        final var builder = ConstraintSatisfactionProblem.builder();
        final var digitDomain = new IntRangeDomain(0, 9);
        final var carryDomain = new IntRangeDomain(0, 1);
        final var t = builder.createVariable("T", digitDomain);
        final var w = builder.createVariable("W", digitDomain);
        final var o = builder.createVariable("O", digitDomain);
        final var f = builder.createVariable("F", digitDomain);
        final var u = builder.createVariable("U", digitDomain);
        final var r = builder.createVariable("R", digitDomain);
        final var c1 = builder.createVariable("C1", carryDomain);
        final var c2 = builder.createVariable("C2", carryDomain);
        final var c3 = builder.createVariable("C3", carryDomain);

        builder
                .constraint(new AllDiffConstraint(Set.of(t, w, o, f, u, r)))
                .constraint(new ExpressionConstraint(Set.of(o, r, c1), assignment -> {
                    final var O = (int) assignment.getValue(o).get();
                    final var R = (int) assignment.getValue(r).get();
                    final var C1 = (int) assignment.getValue(c1).get();
                    return O + O == R + 10 * C1;
                }))
                .constraint(new ExpressionConstraint(Set.of(c1, w, u, c2), assignment -> {
                    final var C1 = (int) assignment.getValue(c1).get();
                    final var W = (int) assignment.getValue(w).get();
                    final var U = (int) assignment.getValue(u).get();
                    final var C2 = (int) assignment.getValue(c2).get();
                    return C1 + W + W == U + 10 * C2;
                }))
                .constraint(new ExpressionConstraint(Set.of(c2, t, o, c3), assignment -> {
                    final var C2 = (int) assignment.getValue(c2).get();
                    final var T = (int) assignment.getValue(t).get();
                    final var O = (int) assignment.getValue(o).get();
                    final var C3 = (int) assignment.getValue(c3).get();
                    return C2 + T + T == O + 10 * C3;
                }))
                .constraint(new ExpressionConstraint(Set.of(c3, f), assignment -> {
                    final var C3 = (int) assignment.getValue(c3).get();
                    final var F = (int) assignment.getValue(f).get();
                    return C3 == F;
                }));
        final var csp = builder.build();
        System.out.println(csp.getSolution());
    }
}
