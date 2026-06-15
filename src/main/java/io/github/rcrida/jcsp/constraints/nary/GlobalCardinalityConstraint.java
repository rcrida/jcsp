package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
public class GlobalCardinalityConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {
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

    /**
     * For each tracked value, classifies variables as <em>definite</em> (domain is exactly
     * {@code {value}}), <em>possible</em> (value is in the domain alongside other values), or
     * <em>impossible</em> (value absent from the domain).
     * <p>
     * When the definite count reaches the required cardinality, the value is removed from all
     * possible domains. When the definite count plus the number of possible variables equals the
     * required cardinality, every possible domain is forced to {@code {value}}. Domain changes
     * from one tracked value feed into the classification of the next.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        for (var entry : cardinalities.entrySet()) {
            T value = entry.getKey();
            int n = entry.getValue();

            List<Variable<T>> possibleVars = new ArrayList<>();
            int definiteCount = 0;
            for (Variable<?> var : getVariables()) {
                Domain<T> dom = (Domain<T>) current.get(var);
                if (dom.stream().anyMatch(value::equals)) {
                    if (dom.size() == 1) definiteCount++;
                    else possibleVars.add((Variable<T>) var);
                }
            }
            int maxCount = definiteCount + possibleVars.size();

            if (definiteCount > n) return Optional.empty();
            if (maxCount < n) return Optional.empty();

            if (definiteCount == n) {
                for (Variable<T> var : possibleVars) {
                    Domain<T> newDom = ((Domain<T>) current.get(var)).toBuilder().delete(value).build();
                    current.put(var, newDom);
                    updated.put(var, newDom);
                }
            } else if (maxCount == n) {
                for (Variable<T> var : possibleVars) {
                    Domain<T> dom = (Domain<T>) current.get(var);
                    Domain.Builder<T> builder = dom.toBuilder();
                    for (T v : dom.toList()) {
                        if (!value.equals(v)) builder.delete(v);
                    }
                    Domain<T> newDom = builder.build();
                    current.put(var, newDom);
                    updated.put(var, newDom);
                }
            }
        }

        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "GlobalCardinality(" + cardinalities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}
