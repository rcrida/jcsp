package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Represents the "exactly one" constraint for boolean variables in a CSP.
 * This constraint ensures that exactly one of the involved variables is {@code true}.
 *
 * <p>Extends {@link AtMostOneConstraint}: for partial assignments it behaves identically
 * (at most one true), and only when all variables are assigned does it additionally require
 * that exactly one is {@code true}. The inherited pairwise-NAND binary decomposition
 * provides AC3 propagation for the "at most one" half.
 */
@SuperBuilder
public class ExactlyOneConstraint extends AtMostOneConstraint {

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        long trueCount = values.stream().filter(b -> b).count();
        if (trueCount > 1) return false;
        if (values.size() < getVariables().size()) return true;
        return trueCount == 1;
    }

    @Override
    public String getRelation() {
        return "ExactlyOne";
    }
}
