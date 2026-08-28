package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Unary constraint that compares a number variable to a fixed value using an {@link Operator}.
 * Satisfied when {@code variable <op> value}, e.g. {@code x >= 3} or {@code x != 0}.
 * <p>
 * Implements {@link Propagatable} for both {@link BoundedDomain} (via {@code withBounds}) and
 * discrete domains (via {@link NumericBounds#narrow}, which deletes out-of-range values) --
 * {@link Operator#EQ}/{@link Operator#LEQ}/{@link Operator#LT}/{@link Operator#GEQ}/{@link
 * Operator#GT} all narrow through the same
 * {@link NumericBounds#min}/{@link NumericBounds#max}/{@link NumericBounds#narrow} bounds-clipping
 * pass (matching {@link io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint}'s
 * own dual-domain-kind treatment) since {@code [newMin, newMax]} still captures every one of them
 * as a half-open or closed range; {@link Operator#NEQ} instead deletes {@link #value} directly from a
 * discrete domain (exact, not an approximation -- a single point can't be expressed as a
 * {@code [min,max]} clip) and is a no-op for {@link BoundedDomain}, where excluding one point from
 * a continuum isn't representable as a bound either. This used to rely entirely on {@link
 * io.github.rcrida.jcsp.consistency.node.NodeConsistency} for the discrete case (this class's own
 * {@code propagate} only clipped {@link BoundedDomain}) -- correct whenever this constraint is
 * added unconditionally, but silently inert whenever it's the body of a {@link
 * io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint}/{@link
 * io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint}, since {@code NodeConsistency}'s
 * own preprocessing only scans top-level {@link UnaryConstraint}s, never one nested inside a
 * reification's body. Confirmed via {@code Xcsp3CallbackHandler#recognizeGroundRelation}, which
 * reifies this class as an {@code iff}/{@code or} operand over ordinary discrete XCSP3 variables.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryComparatorConstraint<N extends Number & Comparable<N>> extends UnaryConstraint<N> implements Propagatable {
    @NonNull N value;
    @NonNull Operator operator;

    public static <N extends Number & Comparable<N>> UnaryComparatorConstraint<N> of(
            @NonNull Variable<N> variable, @NonNull Operator operator, @NonNull N value) {
        return UnaryComparatorConstraint.<N>builder()
                .variable(variable).operator(operator).value(value).build();
    }

    @Override
    protected boolean checkValue(@NonNull N v) {
        return operator.compare(v, value);
    }

    @Override
    public String getRelation() {
        return getVariable() + " " + operator.symbol + " " + value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
        Domain<N> domain = (Domain<N>) domains.get(getVariable());
        if (operator == Operator.NEQ) {
            if (!(domain instanceof DiscreteDomain<N> discrete) || !discrete.contains(value)) {
                return Optional.of(Map.of());
            }
            DiscreteDomain<N> narrowed = discrete.toBuilder().delete(value).build();
            return narrowed.isEmpty() ? Optional.empty() : Optional.of(Map.of(getVariable(), narrowed));
        }

        double lo = NumericBounds.min(domain);
        double hi = NumericBounds.max(domain);
        double v = value.doubleValue();
        double newMin = (operator == Operator.GEQ || operator == Operator.GT || operator == Operator.EQ) ? Math.max(lo, v) : lo;
        double newMax = (operator == Operator.LEQ || operator == Operator.LT || operator == Operator.EQ) ? Math.min(hi, v) : hi;
        if (newMin > newMax) return Optional.empty();
        Optional<Domain<N>> pruned = NumericBounds.narrow(domain, newMin, newMax);
        if (pruned.isEmpty()) return Optional.of(Map.of());
        return pruned.get().isEmpty() ? Optional.empty() : Optional.of(Map.of(getVariable(), pruned.get()));
    }

    /**
     * {@link #propagate}'s infeasibility is derived purely from {@code getVariable()}'s own current
     * domain, so {@link RangeNogoodConstraint#fromCurrentBounds} is always a sound explanation when
     * that domain is safely citable as a gapless range -- true unconditionally for a {@link
     * BoundedDomain} (a genuine continuum has no gaps to misrepresent) and for a gapless discrete
     * one -- falling back to {@link ValueSetNogoodConstraint#fromCurrentState} (citing its exact
     * current value set, gaps and all) for a gapped discrete domain, the same two-tier fallback
     * {@link io.github.rcrida.jcsp.constraints.nary.MaxVariableConstraint#explainInfeasible} uses.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }
}
