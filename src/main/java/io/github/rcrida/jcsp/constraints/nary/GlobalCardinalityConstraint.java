package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that requires each tracked value to appear a specific number of times
 * across a set of variables: {@code count(vars, v) == cardinality.get(v)} for each entry.
 * <p>
 * Values not present in the cardinality map are unconstrained (open GCC). For partial
 * assignments, early failure is detected when any value already exceeds its required count;
 * otherwise the constraint is optimistically satisfied until all variables are assigned.
 * <p>
 * Equivalent to MiniZinc's {@code global_cardinality(vars, values, counts)} constraint.
 * Generalises {@link CountConstraint} (single value) and {@link AllDiffConstraint}
 * (all counts == 1).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GlobalCardinalityConstraint<T> extends UniformNaryConstraint<T> {
    @Singular private final Map<T, Integer> cardinalities;

    public static <T> GlobalCardinalityConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                                        @NonNull Map<T, Integer> cardinalities) {
        return GlobalCardinalityConstraint.<T>builder()
                .variables(variables)
                .cardinalities(cardinalities)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        Map<T, Integer> counts = new HashMap<>();
        for (T v : values) counts.merge(v, 1, Integer::sum);

        for (var entry : cardinalities.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) > entry.getValue()) return false;
        }
        if (values.size() < getVariables().size()) return true;
        for (var entry : cardinalities.entrySet()) {
            if (!Objects.equals(counts.getOrDefault(entry.getKey(), 0), entry.getValue())) return false;
        }
        return true;
    }

    @Override
    public String getRelation() {
        return "GlobalCardinality(" + cardinalities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}
