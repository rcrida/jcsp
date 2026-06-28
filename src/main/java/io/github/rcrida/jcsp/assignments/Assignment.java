package io.github.rcrida.jcsp.assignments;

import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Assignment extractPartialAssignment(@NonNull Set<? extends Variable<?>> variables) {
        return Assignment.builder()
                .values(values.entrySet().stream()
                        .filter(a -> variables.contains(a.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
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
