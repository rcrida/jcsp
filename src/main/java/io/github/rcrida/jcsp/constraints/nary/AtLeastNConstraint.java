package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Represents the "at least N" constraint for boolean variables in a CSP.
 * This constraint ensures that at least {@code n} of the involved variables are {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 *
 * <p>For partial assignments, the constraint is satisfied as long as it is still possible
 * to reach {@code n} true values — it only fails when all variables are assigned and fewer
 * than {@code n} are {@code true}.
 */
@SuperBuilder
public class AtLeastNConstraint extends UniformNaryConstraint<Boolean> {
    private final int n;

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        long trueCount = values.stream().filter(b -> b).count();
        if (trueCount >= n) return true;
        return values.size() < getVariables().size();
    }

    @Override
    public String getRelation() {
        return "AtLeast" + n;
    }
}
