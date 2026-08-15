package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint that counts how many variables in a set take a specific value,
 * and compares that count to a bound using a specified {@link Operator}:
 * {@code count(vars, value) <op> n}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CountConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull private final T value;
    @NonNull private final Operator operator;
    private final int n;

    public static <T> CountConstraint<T> of(@NonNull Set<Variable<T>> variables,
                                            @NonNull T value,
                                            @NonNull Operator operator,
                                            int n) {
        return CountConstraint.<T>builder()
                .variables(variables)
                .value(value)
                .operator(operator)
                .n(n)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        if (values.size() < getVariables().size()) return true;
        int count = (int) values.stream().filter(value::equals).count();
        return operator.compare(count, n);
    }

    /**
     * When the definite count reaches {@code n} (for EQ/LEQ), {@link #value} is removed from
     * all possible domains. When the max reachable count equals {@code n} (for EQ/GEQ), all
     * possible domains are forced to {@code {value}}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(getVariables(), value::equals, domains);
        int definiteCount = c.definite().size();
        int maxCount = definiteCount + c.possible().size();

        boolean applyUpper = operator == Operator.EQ || operator == Operator.LEQ;
        boolean applyLower = operator == Operator.EQ || operator == Operator.GEQ;

        if (applyUpper && definiteCount > n) return Optional.empty();
        if (applyLower && maxCount < n)      return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        if (applyUpper && definiteCount == n) {
            for (Variable<T> var : c.possible())
                updated.put(var, ((DiscreteDomain<T>) domains.get(var)).toBuilder().delete(value).build());
        }

        if (applyLower && maxCount == n) {
            for (Variable<T> var : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
                DiscreteDomain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (!value.equals(v)) builder.delete(v);
                }
                updated.put(var, builder.build());
            }
        }

        return Optional.of(updated);
    }

    /**
     * On infeasibility, tries two independent, always-sound explanations built from the same
     * {@link ClassificationSupport#classify} used by {@link #propagate} — neither replicates
     * {@link #propagate}'s internal branch order; each is checked directly against the current
     * domains and is valid regardless of which branch actually detected the conflict:
     * <ul>
     *   <li><b>Definite-count violation</b> (EQ/LEQ): {@code definiteCount > n}. Every
     *       <em>definite</em> variable is, by construction, already a singleton {@code {value}}
     *       domain, so citing all of them with {@link #value} is directly sound — no further
     *       singleton gating needed, unlike the collective case below.</li>
     *   <li><b>Max-reachable-count violation</b> (EQ/GEQ): {@code maxCount < n}. This depends on
     *       every <em>impossible</em> variable (domain excludes {@link #value}) categorically
     *       excluding {@link #value}, regardless of what value each one actually takes — but a
     *       nogood can only cite concrete variable-value pairs, so it's only attributable when
     *       every impossible variable is singleton (via {@link Propagatable#allSingletonReason}):
     *       a non-singleton impossible variable's domain could still shrink differently along
     *       another search path, so omitting it wouldn't be sound.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        boolean applyUpper = operator == Operator.EQ || operator == Operator.LEQ;
        boolean applyLower = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(getVariables(), value::equals, domains);

        if (applyUpper && c.definite().size() > n) {
            Map<Variable<?>, Object> reason = new HashMap<>();
            for (Variable<?> var : c.definite()) reason.put(var, value);
            return GroundNogoodConstraint.fromReason(reason);
        }

        if (applyLower && c.definite().size() + c.possible().size() < n) {
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(c.impossible(), domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "count(" + value + ") " + operator.symbol + " " + n;
    }
}
