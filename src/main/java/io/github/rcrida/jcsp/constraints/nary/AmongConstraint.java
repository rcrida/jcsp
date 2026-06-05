package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that counts how many variables take a value from a given set {@code S},
 * and compares that count to a bound: {@code among(vars, S) <op> n}.
 * <p>
 * Generalises {@link CountConstraint} from a single target value to a set of values.
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned. Equivalent to MiniZinc's {@code among(n, vars, S)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AmongConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull private final Set<T> values;
    @NonNull private final Operator operator;
    private final int n;

    public static <T> AmongConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                            @NonNull Set<T> values,
                                            @NonNull Operator operator,
                                            int n) {
        return AmongConstraint.<T>builder()
                .variables(variables)
                .values(Set.copyOf(values))
                .operator(operator)
                .n(n)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> vals) {
        if (vals.size() < getVariables().size()) return true;
        int count = (int) vals.stream().filter(values::contains).count();
        return operator.compare(count, n);
    }

    /**
     * Classifies each variable as:
     * <ul>
     *   <li><em>definite</em>: entire domain is within {@code S} (always contributes to count)</li>
     *   <li><em>possible</em>: domain intersects {@code S} but also has values outside it</li>
     *   <li><em>impossible</em>: domain is disjoint from {@code S} (never contributes)</li>
     * </ul>
     * When the definite count reaches {@code n} (EQ/LEQ), removes all {@code S} values from
     * possible domains. When the max reachable count equals {@code n} (EQ/GEQ), removes all
     * non-{@code S} values from possible domains.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        List<Variable<T>> possibleVars = new ArrayList<>();
        int definiteCount = 0;
        for (Variable<?> var : getVariables()) {
            Domain<T> dom = (Domain<T>) domains.get(var);
            boolean hasInS = dom.stream().anyMatch(values::contains);
            if (!hasInS) continue;
            if (dom.stream().allMatch(values::contains)) definiteCount++;
            else possibleVars.add((Variable<T>) var);
        }
        int maxCount = definiteCount + possibleVars.size();

        boolean applyUpper = operator == Operator.EQ || operator == Operator.LEQ;
        boolean applyLower = operator == Operator.EQ || operator == Operator.GEQ;

        if (applyUpper && definiteCount > n) return Optional.empty();
        if (applyLower && maxCount < n)      return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Upper quota filled: remove S values from possible domains
        if (applyUpper && definiteCount == n) {
            for (Variable<T> var : possibleVars) {
                Domain<T> dom = (Domain<T>) domains.get(var);
                Domain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (values.contains(v)) builder.delete(v);
                }
                updated.put(var, builder.build());
            }
        }

        // Lower quota requires all possibles: remove non-S values from possible domains
        if (applyLower && maxCount == n) {
            for (Variable<T> var : possibleVars) {
                Domain<T> dom = (Domain<T>) domains.get(var);
                Domain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (!values.contains(v)) builder.delete(v);
                }
                updated.put(var, builder.build());
            }
        }

        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "among(" + values.stream().map(Objects::toString).sorted().collect(Collectors.joining(", ")) + ") " + operator.symbol + " " + n;
    }
}
