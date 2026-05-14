package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Represents the "at most one" constraint for boolean variables in a constraint satisfaction problem (CSP).
 * This constraint ensures that at most one of the involved variables is assigned {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 */
@SuperBuilder
public class AtMostOneConstraint extends UniformNaryConstraint<Boolean> {

    public static final @NonNull BiPredicate<Boolean, Boolean> AT_MOST_ONE_TRUE = (a, b) -> !(a && b);

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        return values.stream().filter(b -> b).count() <= 1;
    }

    @Override
    public String getRelation() {
        return "AtMostOne";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Set<BinaryConstraint<?, ?>>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryPredicateConstraint.<Boolean, Boolean>builder()
                        .left((Variable<Boolean>) variables.get(i))
                        .right((Variable<Boolean>) variables.get(j))
                        .biPredicate(AT_MOST_ONE_TRUE)
                        .build());
            }
        }
        return Optional.of(binaryConstraints);
    }
}
