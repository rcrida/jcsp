package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Represents the "at most N" constraint for boolean variables in a CSP.
 * This constraint ensures that at most {@code n} of the involved variables are {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 *
 * <p>For the specific case of N=1, prefer {@link AtMostOneConstraint}, which provides an
 * AC3-compatible binary decomposition into pairwise not-both-true constraints.
 */
@SuperBuilder
public class AtMostNConstraint extends UniformNaryConstraint<Boolean> {
    private final int n;

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        return values.stream().filter(b -> b).count() <= n;
    }

    @Override
    public String getRelation() {
        return "AtMost" + n;
    }
}
