package io.github.rcrida.jcsp.assignments;

import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Builder(toBuilder = true)
public record Assignment(@Singular Map<Variable<?>, Object> values, Statistics statistics) {

    public Assignment {
        if (statistics == null) statistics = new Statistics();
    }

    public static Assignment empty() {
        return Assignment.builder().build();
    }

    public Map<Variable<?>, Object> getValues() {
        return values;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public static Assignment of(Map<? extends Variable<?>, ?> values) {
        return Assignment.builder().values(values).build();
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(@NonNull Variable<T> variable) {
        return Optional.ofNullable((T) values.get(variable));
    }

    /**
     * Iterates {@code variables} and looks each one up in {@link #values} directly, rather than
     * scanning every entry in {@link #values} and filtering by membership in {@code variables} —
     * {@code variables} is typically a single constraint's own handful of variables, far smaller
     * than the full assignment (profiling {@code UniformNaryConstraint#isSatisfiedBy}, this
     * method's dominant caller, found the original O(|values|)-per-call scan to be the runaway
     * cost in {@code LargeNeighborhoodSolver}'s per-combo, per-constraint violation checking).
     * {@code values} never intentionally maps a variable to {@code null} (same assumption {@link
     * #getValue} already relies on via {@code Optional.ofNullable}), so a {@code null} lookup
     * result is only ever "not yet assigned", matching the original's membership-filter semantics.
     */
    public Assignment extractPartialAssignment(@NonNull Set<? extends Variable<?>> variables) {
        Map<Variable<?>, Object> partial = new HashMap<>();
        for (Variable<?> variable : variables) {
            Object value = values.get(variable);
            if (value != null) partial.put(variable, value);
        }
        return Assignment.builder().values(partial).build();
    }

    public Assignment withValue(@NonNull Variable<?> variable, @NonNull Object value) {
        val next = toBuilder().value(variable, value).build();
        next.statistics.incrementNodesExplored();
        return next;
    }

    public Assignment merge(@NonNull Assignment another) {
        val builder = toBuilder();
        builder.values(another.getValues());
        val merged = builder.build();
        merged.statistics.add(another.statistics);
        return merged;
    }

    public boolean isConsistent(ConstraintSatisfactionProblem csp) {
        validateAssignment(csp);
        return csp.getConstraints().stream()
                .filter(constraint -> constraint.getVariables().stream().anyMatch(values::containsKey))
                .allMatch(constraint -> {
                    statistics.incrementConstraintChecks();
                    return constraint.isSatisfiedBy(this);
                });
    }

    public boolean isComplete(ConstraintSatisfactionProblem csp) {
        validateAssignment(csp);
        return csp.getVariableDomains().keySet().stream().allMatch(values::containsKey);
    }

    public boolean isSolution(ConstraintSatisfactionProblem csp) {
        return isComplete(csp) && isConsistent(csp);
    }

    private void validateAssignment(ConstraintSatisfactionProblem csp) {
        for (Map.Entry<Variable<?>, Object> entry : values.entrySet()) {
            assert csp.isAllowedValue(entry.getKey(), entry.getValue()) : String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Assignment a)) return false;
        return Objects.equals(values, a.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return String.valueOf(values);
    }
}
