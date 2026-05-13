package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
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
public class AtMostOneConstraint extends NaryConstraint {

    public static final @NonNull BiPredicate<Object, Object> AT_MOST_ONE_TRUE = (a, b) -> !(a.equals(true) && b.equals(true));

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return assignment.extractPartialAssignment(getVariables()).getValues().values().stream()
                .filter(Boolean.TRUE::equals)
                .count() <= 1;
    }

    @Override
    public String getRelation() {
        return "AtMostOne";
    }

    @Override
    public Optional<Set<BinaryConstraint>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint>();

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryPredicateConstraint.builder()
                        .left(variables.get(i))
                        .right(variables.get(j))
                        .biPredicate(AT_MOST_ONE_TRUE)
                        .build());
            }
        }

        return Optional.of(binaryConstraints);
    }
}
