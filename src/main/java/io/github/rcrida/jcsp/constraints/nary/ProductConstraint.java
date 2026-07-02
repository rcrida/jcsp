package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the product of a set of numeric variables to a fixed bound:
 * {@code v1 * v2 * ... * vn op bound}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated
 * once all variables are assigned.
 * <p>
 * Propagation applies interval-arithmetic bounds narrowing for EQ, LEQ, and GEQ operators,
 * but only when all variable domains have strictly positive minimums. Domains that include zero
 * or negative values receive no narrowing (non-monotone multiplication makes tight bounds
 * propagation unsound without case analysis).
 * <p>
 * Upper-bound pass (EQ/LEQ): clips each variable's maximum to
 * {@code bound * min(var) / productMin} where {@code productMin} is the product of all domain
 * minimums. Lower-bound pass (EQ/GEQ): raises each variable's minimum to
 * {@code bound * max(var) / productMax} where {@code productMax} is the product of all domain
 * maximums.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ProductConstraint<N extends Number> extends UniformNaryConstraint<N> implements Propagatable {

    private static final Set<Operator> PROPAGATING_OPERATORS = EnumSet.of(Operator.EQ, Operator.LEQ, Operator.GEQ);

    @NonNull private final N bound;
    @NonNull private final Operator operator;

    public static <N extends Number> ProductConstraint<N> of(
            @NonNull Set<Variable<N>> variables,
            @NonNull Operator operator,
            @NonNull N bound) {
        return ProductConstraint.<N>builder()
                .variables(variables)
                .operator(operator)
                .bound(bound)
                .build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<N> values) {
        if (values.size() < getVariables().size()) return true;
        double product = values.stream().mapToDouble(Number::doubleValue).reduce(1.0, (a, b) -> a * b);
        return operator.compare(product, bound.doubleValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!PROPAGATING_OPERATORS.contains(operator)) {
            return Optional.of(Map.of());
        }

        List<Variable<N>> vars = new ArrayList<>((Collection<Variable<N>>) (Collection<?>) getVariables());
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
        double k = bound.doubleValue();

        if ((operator == Operator.EQ  && (k < productMin || k > productMax)) ||
            (operator == Operator.LEQ && k < productMin) ||
            (operator == Operator.GEQ && k > productMax)) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Domain<N> dom = (Domain<N>) domains.get(vars.get(i));

            // Upper-bound pass: product ≤ k — clip each variable's max to k / othersMinProduct
            if (operator == Operator.EQ || operator == Operator.LEQ) {
                double newMax = k * mins[i] / productMin;
                if (newMax < maxs[i]) {
                    // mins[i] > 0 and k >= productMin guarantee newMax >= mins[i]; narrow returns present
                    dom = (Domain<N>) NumericBounds.narrow(dom, mins[i], newMax).orElseThrow();
                    updated.put(vars.get(i), dom);
                }
            }

            // Lower-bound pass: product ≥ k — raise each variable's min to k / othersMaxProduct
            if (operator == Operator.EQ || operator == Operator.GEQ) {
                double newMin = k * maxs[i] / productMax;
                if (newMin > mins[i]) {
                    // newMin > mins[i] guarantees narrow returns present; may be empty for discrete gap domains
                    Domain<N> raised = (Domain<N>) NumericBounds.narrow(dom, newMin, maxs[i]).orElseThrow();
                    if (raised.isEmpty()) return Optional.empty();
                    updated.put(vars.get(i), raised);
                }
            }
        }
        return Optional.of(updated);
    }

    /**
     * On infeasibility, the product's violation depends on the combined product of every
     * variable, not any single variable in isolation — like {@link SumConstraint}/
     * {@link LinearConstraint} and unlike {@link MaxConstraint}/{@link MinConstraint}, a product
     * has no monotonic "one value alone already breaks the bound" case (a single large factor
     * says nothing about the bound without knowing the other factors too). See
     * {@link Propagatable#allSingletonReason} for why the fully collective explanation is the
     * only sound, self-contained one — this also covers the discrete-gap corner case in the
     * lower-bound pass ({@code raised.isEmpty()}), which falls back to an empty reason the same
     * way.
     */
    @Override
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return Propagatable.allSingletonReason(getVariables(), domains);
    }

    @Override
    public String getRelation() {
        String varProduct = getVariables().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(" * "));
        return varProduct + " " + operator.symbol + " " + bound;
    }
}
