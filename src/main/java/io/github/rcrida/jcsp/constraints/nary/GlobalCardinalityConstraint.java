package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
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
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        for (var entry : cardinalities.entrySet()) {
            T value = entry.getKey();
            int n = entry.getValue();

            Classification<T> c = classify(value, current);
            int definiteCount = c.definite().size();
            int maxCount = definiteCount + c.possible().size();

            if (definiteCount > n) return Optional.empty();
            if (maxCount < n) return Optional.empty();

            if (definiteCount == n) narrow(value, c.possible(), true, current, updated);
            else if (maxCount == n) narrow(value, c.possible(), false, current, updated);
        }

        return Optional.of(updated);
    }

    /**
     * On infeasibility, replays the same per-value classification (and, where a value's quota is
     * already met, the same domain narrowing) as {@link #propagate} so that later tracked values
     * are judged against the domains as they stood at the point of failure — narrowing from one
     * value can change another's classification, so the explanation must track {@code current}
     * exactly like propagation does rather than reasoning from the original {@code domains} alone.
     * For the value whose quota is violated, mirrors {@link CountConstraint#explainInfeasible}:
     * <ul>
     *   <li><b>Too many definites</b> ({@code definiteCount > n}): every definite variable is, by
     *       construction, already a singleton {@code {value}} domain, so citing all of them is
     *       directly sound.</li>
     *   <li><b>Too few reachable</b> ({@code maxCount < n}): depends on every impossible variable
     *       categorically excluding {@code value} — only attributable when every impossible
     *       variable is singleton, via {@link Propagatable#allSingletonReason}.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);

        for (var entry : cardinalities.entrySet()) {
            T value = entry.getKey();
            int n = entry.getValue();

            Classification<T> c = classify(value, current);
            int definiteCount = c.definite().size();
            int maxCount = definiteCount + c.possible().size();

            if (definiteCount > n) {
                Map<Variable<?>, Object> reason = new HashMap<>();
                for (Variable<?> var : c.definite()) reason.put(var, value);
                return GroundNogoodConstraint.fromReason(reason);
            }
            if (maxCount < n) {
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(c.impossible(), current));
            }

            if (definiteCount == n) narrow(value, c.possible(), true, current, new HashMap<>());
            else if (maxCount == n) narrow(value, c.possible(), false, current, new HashMap<>());
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Classification<T> classify(T value, @NonNull Map<Variable<?>, Domain<?>> current) {
        List<Variable<T>> possible = new ArrayList<>();
        List<Variable<?>> definite = new ArrayList<>();
        List<Variable<?>> impossible = new ArrayList<>();
        for (Variable<?> var : getVariables()) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) current.get(var);
            if (dom.stream().anyMatch(value::equals)) {
                if (dom.isSingleton()) definite.add(var);
                else possible.add((Variable<T>) var);
            } else {
                impossible.add(var);
            }
        }
        return new Classification<>(possible, definite, impossible);
    }

    @SuppressWarnings("unchecked")
    private void narrow(T value, @NonNull List<Variable<T>> possibleVars, boolean removeValue,
                        @NonNull Map<Variable<?>, Domain<?>> current, @NonNull Map<Variable<?>, Domain<?>> updated) {
        for (Variable<T> var : possibleVars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) current.get(var);
            Domain<T> newDom;
            if (removeValue) {
                newDom = dom.toBuilder().delete(value).build();
            } else {
                DiscreteDomain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (!value.equals(v)) builder.delete(v);
                }
                newDom = builder.build();
            }
            current.put(var, newDom);
            updated.put(var, newDom);
        }
    }

    private record Classification<T>(List<Variable<T>> possible, List<Variable<?>> definite,
                                     List<Variable<?>> impossible) {}

    @Override
    public String getRelation() {
        return "GlobalCardinality(" + cardinalities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}
