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
     * When the definite count reaches {@code n} (EQ/LEQ), removes all {@code S} values from
     * possible domains. When the max reachable count equals {@code n} (EQ/GEQ), removes all
     * non-{@code S} values from possible domains.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(getVariables(), values::contains, domains);
        int definiteCount = c.definite().size();
        int maxCount = definiteCount + c.possible().size();

        boolean applyUpper = operator == Operator.EQ || operator == Operator.LEQ;
        boolean applyLower = operator == Operator.EQ || operator == Operator.GEQ;

        if (applyUpper && definiteCount > n) return Optional.empty();
        if (applyLower && maxCount < n)      return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Upper quota filled: remove S values from possible domains
        if (applyUpper && definiteCount == n) {
            for (Variable<T> var : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
                DiscreteDomain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (values.contains(v)) builder.delete(v);
                }
                updated.put(var, builder.build());
            }
        }

        // Lower quota requires all possibles: remove non-S values from possible domains
        if (applyLower && maxCount == n) {
            for (Variable<T> var : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
                DiscreteDomain.Builder<T> builder = dom.toBuilder();
                for (T v : dom.toList()) {
                    if (!values.contains(v)) builder.delete(v);
                }
                updated.put(var, builder.build());
            }
        }

        return Optional.of(updated);
    }

    /**
     * On infeasibility, tries two independent, always-sound explanations built from the same
     * {@link ClassificationSupport#classify} used by {@link #propagate} — mirrors
     * {@link CountConstraint#explainInfeasible} generalised from a single value to a set:
     * <ul>
     *   <li><b>Definite-count violation</b> (EQ/LEQ): {@code definiteCount > n}. Unlike
     *       {@link CountConstraint}, a <em>definite</em> variable here only needs its domain
     *       entirely inside {@code S} — it need not be singleton (e.g. domain {@code {a,b}} with
     *       {@code S = {a,b,c}}) — so citing it requires the same singleton gating as the
     *       collective case: only attributable when every definite variable is singleton, via
     *       {@link Propagatable#allSingletonReason}.</li>
     *   <li><b>Max-reachable-count violation</b> (EQ/GEQ): {@code maxCount < n}. Depends on every
     *       <em>impossible</em> variable (domain disjoint from {@code S}) categorically excluding
     *       {@code S}, regardless of its actual value — only attributable when every impossible
     *       variable is singleton, for the same reason as {@link CountConstraint}.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        boolean applyUpper = operator == Operator.EQ || operator == Operator.LEQ;
        boolean applyLower = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(getVariables(), values::contains, domains);

        if (applyUpper && c.definite().size() > n) {
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(c.definite(), domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        if (applyLower && c.definite().size() + c.possible().size() < n) {
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(c.impossible(), domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "among(" + values.stream().map(Objects::toString).sorted().collect(Collectors.joining(", ")) + ") " + operator.symbol + " " + n;
    }
}
