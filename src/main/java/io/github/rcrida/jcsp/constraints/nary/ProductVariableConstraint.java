package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The variable-target sibling of {@link ProductConstraint}: {@code v1 * v2 * ... * vn <op> target},
 * where {@link #target} is itself a variable rather than a fixed bound. Mirrors {@link
 * CountVariableConstraint}/{@link MaxVariableConstraint}'s own shape (a real {@link #operator}
 * field, {@link #target} narrowed alongside the factors) and reuses {@link ProductConstraint}'s
 * exact interval-arithmetic narrowing, just with {@code target}'s current bounds standing in for
 * the fixed {@code bound}. Extends {@link NaryConstraint} directly rather than {@link
 * UniformNaryConstraint}, whose {@code isSatisfiedBy} is {@code final} -- same reason every other
 * variable-target sibling in this package does.
 * <p>
 * Added specifically for XCSP3's {@code sum} with variable {@code coeffVars} ({@code
 * Σ list[i] * coeffVars[i] <op> condition}), which decomposes into one {@link
 * #ProductVariableConstraint} per position (a fresh auxiliary variable holding {@code
 * list[i] * coeffVars[i]}) plus a plain {@link SumVariableConstraint}/{@code sumConstraint} over
 * the auxiliaries -- see {@code Xcsp3CallbackHandler#buildCtrSum(String, org.xcsp.parser.entries.XVariables.XVarInteger[],
 * org.xcsp.parser.entries.XVariables.XVarInteger[], org.xcsp.common.Condition)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ProductVariableConstraint<N extends Number> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @Getter @NonNull private final Set<Variable<N>> factors;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final Variable<N> target;

    public static <N extends Number> ProductVariableConstraint<N> of(
            @NonNull Set<Variable<N>> factors, @NonNull Operator operator, @NonNull Variable<N> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(factors);
        allVars.add(target);
        return ProductVariableConstraint.<N>builder()
                .variables(allVars)
                .factors(Set.copyOf(factors))
                .operator(operator)
                .target(target)
                .build();
    }

    /** Optimistically satisfied for a partial assignment -- only evaluated once every variable is assigned. */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        double product = factors.stream()
                .mapToDouble(v -> assignment.getValue(v).orElseThrow().doubleValue())
                .reduce(1.0, (a, b) -> a * b);
        double targetValue = assignment.getValue(target).orElseThrow().doubleValue();
        return operator.compare(product, targetValue);
    }

    /**
     * {@link ProductConstraint#propagate}'s exact interval-arithmetic narrowing (only for {@code
     * EQ}/{@code LEQ}/{@code GEQ}, only when every factor's domain has a strictly positive
     * minimum), generalised two ways: {@code target}'s <em>current</em> bounds stand in for the
     * fixed {@code bound} when narrowing the factors, and -- unlike {@link ProductConstraint},
     * which has no target to narrow -- {@link #target} itself is also narrowed to {@code
     * [productMin, productMax]}, the same "{@code leqLike} raises the lower bound, {@code geqLike}
     * lowers the upper bound" shape {@link CountVariableConstraint#propagate}/{@link
     * MaxVariableConstraint#propagate} already use. That range is never inverted (by the same
     * cross-bound algebra those two classes rely on: the early infeasibility checks below already
     * guarantee {@code productMin <= tHi} and {@code tLo <= productMax}), but -- learned the hard
     * way on {@link GlobalCardinalityVariableConstraint} -- a non-inverted <em>range</em> doesn't
     * guarantee {@code target}'s own (possibly gappy) discrete domain contains a value inside it,
     * so the narrowed result's emptiness is checked explicitly rather than assumed away.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) return Optional.of(Map.of());

        List<Variable<N>> vars = new ArrayList<>(factors);
        int n = vars.size();
        double[] mins = new double[n];
        double[] maxs = new double[n];
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));
            mins[i] = NumericBounds.min(dom);
            maxs[i] = NumericBounds.max(dom);
            if (mins[i] <= 0) return Optional.of(Map.of());
        }

        double productMin = 1.0, productMax = 1.0;
        for (int i = 0; i < n; i++) { productMin *= mins[i]; productMax *= maxs[i]; }

        Domain<N> targetDomain = (Domain<N>) domains.get(target);
        double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        if (leqLike && productMin > tHi) return Optional.empty();
        if (geqLike && productMax < tLo) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));

            // Upper-bound pass: product <= target's max -- clip each factor's max to tHi / othersMinProduct.
            if (leqLike) {
                double newMax = tHi * mins[i] / productMin;
                if (newMax < maxs[i]) {
                    // mins[i] > 0 and tHi >= productMin guarantee newMax >= mins[i]; narrow returns present.
                    dom = (Domain<N>) NumericBounds.narrow(dom, mins[i], newMax).orElseThrow();
                    updated.put(vars.get(i), dom);
                }
            }

            // Lower-bound pass: product >= target's min -- raise each factor's min to tLo / othersMaxProduct.
            if (geqLike) {
                double newMin = tLo * maxs[i] / productMax;
                if (newMin > mins[i]) {
                    // newMin > mins[i] guarantees narrow returns present (same argument as
                    // ProductConstraint#propagate); may be empty for discrete gap domains.
                    Domain<N> raised = NumericBounds.narrow(dom, newMin, maxs[i]).orElseThrow();
                    if (raised.isEmpty()) return Optional.empty();
                    updated.put(vars.get(i), raised);
                }
            }
        }

        double newTLo = leqLike ? Math.max(tLo, productMin) : tLo;
        double newTHi = geqLike ? Math.min(tHi, productMax) : tHi;
        Optional<Domain<N>> narrowedTarget = NumericBounds.narrow(targetDomain, newTLo, newTHi);
        if (narrowedTarget.isPresent()) {
            if (narrowedTarget.get().isEmpty()) return Optional.empty();
            updated.put(target, narrowedTarget.get());
        }

        return Optional.of(updated);
    }

    /**
     * Mirrors {@link ProductConstraint#explainInfeasible}: the product's violation depends on the
     * combined product of every factor (plus, here, {@link #target}'s own bounds), not any single
     * variable in isolation, so {@link RangeNogoodConstraint#fromCurrentBounds} is tried first over
     * every variable including {@link #target}, falling back to {@link
     * Propagatable#allSingletonReason}'s fully collective ground reason only when it can't safely
     * cite some variable's domain as a range.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        String varProduct = factors.stream().map(Object::toString).sorted().collect(Collectors.joining(" * "));
        return varProduct + " " + operator.symbol + " " + target;
    }
}
