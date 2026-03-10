package org.jcsp.examples;

import org.jcsp.constraints.AllDiffConstraint;
import org.jcsp.constraints.ExpressionConstraint;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.impl.ConstraintSatisfactionProblemImpl;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class CryptarithmeticTest {
    @Test
    void twoPlusTwoEqualsFour() {
        final var factory = new ConstraintSatisfactionProblemImpl.Factory();
        final var digitDomain = new IntRangeDomain(0, 9);
        final var carryDomain = new IntRangeDomain(0, 1);
        final var t = factory.createVariable("T", digitDomain);
        final var w = factory.createVariable("W", digitDomain);
        final var o = factory.createVariable("O", digitDomain);
        final var f = factory.createVariable("F", digitDomain);
        final var u = factory.createVariable("U", digitDomain);
        final var r = factory.createVariable("R", digitDomain);
        final var c1 = factory.createVariable("C1", carryDomain);
        final var c2 = factory.createVariable("C2", carryDomain);
        final var c3 = factory.createVariable("C3", carryDomain);

        factory.addConstraint(new AllDiffConstraint(Set.of(t, w, o, f, u, r)));
        factory.addConstraint(new ExpressionConstraint(Set.of(o, r, c1), assignment -> {
            final var O = (int) assignment.getValue(o);
            final var R = (int) assignment.getValue(r);
            final var C1 = (int) assignment.getValue(c1);
            return O + O == R + 10 * C1;
        }));
        factory.addConstraint(new ExpressionConstraint(Set.of(c1, w, u, c2), assignment -> {
            final var C1 = (int) assignment.getValue(c1);
            final var W = (int) assignment.getValue(w);
            final var U = (int) assignment.getValue(u);
            final var C2 = (int) assignment.getValue(c2);
            return C1 + W + W == U + 10 * C2;
        }));
        factory.addConstraint(new ExpressionConstraint(Set.of(c2, t, o, c3), assignment ->  {
            final var C2 = (int) assignment.getValue(c2);
            final var T = (int) assignment.getValue(t);
            final var O = (int) assignment.getValue(o);
            final var C3 = (int) assignment.getValue(c3);
            return C2 + T + T == O + 10 * C3;
        }));
        factory.addConstraint(new ExpressionConstraint(Set.of(c3, f), assignment ->  {
            final var C3 = (int) assignment.getValue(c3);
            final var F = (int) assignment.getValue(f);
            return C3 == F;
        }));
        final var csp = factory.create();
        System.out.println(csp.getSolution());
    }
}
