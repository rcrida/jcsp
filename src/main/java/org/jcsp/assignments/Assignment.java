package org.jcsp.assignments;

import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
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
public record Assignment(@NonNull Map<Variable, Object> values) {
    public Assignment {
        for (Map.Entry<Variable, Object> entry : values.entrySet()) {
            assert entry.getKey().isAllowedValue(entry.getValue()) : String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
        }
    }

    @Nullable
    public Object getValue(@NonNull Variable variable) {
        return values.get(variable);
    }

    public Assignment extractPartialAssignment(@NonNull Set<Variable> variables) {
        return new Assignment(values.entrySet().stream()
                .filter(a -> variables.contains(a.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
