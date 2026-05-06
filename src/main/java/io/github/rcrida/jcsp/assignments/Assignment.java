package io.github.rcrida.jcsp.assignments;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Value
@Builder(toBuilder = true)
public class Assignment {
    public static Assignment empty() {
        return Assignment.builder().build();
    }

    @Singular
    Map<Variable, Object> values;

    @EqualsAndHashCode.Exclude
    @Builder.Default
    Statistics statistics = new Statistics();

    public static Assignment of(Map<Variable, Object> values) {
        return Assignment.builder().values(values).build();
    }

    public Optional<Object> getValue(@NonNull Variable variable) {
        return Optional.ofNullable(values.get(variable));
    }

    public Assignment extractPartialAssignment(@NonNull Set<Variable> variables) {
        return Assignment.builder()
                .values(values.entrySet().stream()
                        .filter(a -> variables.contains(a.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }

    public Assignment withValue(@NonNull Variable variable, @NonNull Object value) {
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
        return isConsistent(csp) && isComplete(csp);
    }

    private void validateAssignment(ConstraintSatisfactionProblem csp) {
        for (Map.Entry<Variable, Object> entry : values.entrySet()) {
            assert csp.isAllowedValue(entry.getKey(), entry.getValue()) : String.format("Invalid assigned value for variable '%s': %s", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String toString() {
        return String.valueOf(values);
    }
}
