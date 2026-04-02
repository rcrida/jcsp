package org.jcsp.assignments;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an immutable mapping of variables to their assigned values. This class ensures
 * that all assigned values comply with the constraints set by the variables' respective domains.
 * <p>
 * The {@code Assignment} class performs validation at construction time to ensure that
 * every assigned value is consistent with the variable's domain. If an invalid value is
 * detected, an {@code AssertionError} is thrown.
 * <p>
 * Example scenarios where this class may be used include:
 * - Representing a partial or complete assignment of values for a set of variables in a
 *   constraint satisfaction problem.
 * - Validating whether a value assignment satisfies specific constraints.
 */
@Slf4j
@Value
@Builder(toBuilder = true)
public class Assignment {
    @Singular
    Map<Variable, Object> values;

    public Assignment(Map<Variable, Object> values) {
        for (Map.Entry<Variable, Object> entry : values.entrySet()) {
            assert entry.getKey().isAllowedValue(entry.getValue()) : String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
        }
        this.values = values;
    }

    public Optional<Object> getValue(@NonNull Variable variable) {
        return Optional.ofNullable(values.get(variable));
    }

    public Assignment extractPartialAssignment(@NonNull Set<Variable> variables) {
        return new Assignment(values.entrySet().stream()
                .filter(a -> variables.contains(a.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public Assignment withValue(@NonNull Variable variable, @NonNull Object value) {
        return toBuilder()
                .value(variable, value)
                .build();
    }

    public boolean isConsistent(ConstraintSatisfactionProblem csp) {
        return csp.getConstraints().stream()
                .allMatch(constraint -> constraint.isSatisfiedBy(this));
    }

    public boolean isComplete(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().keySet().stream().allMatch(values::containsKey);
    }

    public boolean isSolution(ConstraintSatisfactionProblem csp) {
        return isConsistent(csp) && isComplete(csp);
    }
}
