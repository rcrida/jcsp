package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unary constraint enforcing membership in (or exclusion from, when {@link #positive} is {@code
 * false}) a fixed set of values -- XCSP3's {@code in(var, set(...))}/{@code notin(var, set(...))}
 * shape, restricted to the case where every {@code set(...)} member is itself a constant (see
 * {@code Xcsp3CallbackHandler}'s recognizer for the general case, where a member can be an
 * arbitrary sub-expression re-evaluated per assignment -- that shape isn't unary at all once a
 * member references another variable, and stays on the generic {@code PredicateConstraint} path).
 * <p>
 * Unlike {@code buildCtrExtension}'s own unary form (a structurally identical {@code
 * value ∈ fixedSet} check, built as a bare {@code UnaryPredicateConstraint} lambda), this
 * implements {@link Propagatable} directly: a plain {@code UnaryConstraint} already gets full,
 * exact domain pruning from {@link io.github.rcrida.jcsp.consistency.node.NodeConsistency}'s
 * one-time preprocessing pass regardless of whether it implements {@link Propagatable} -- but that
 * pass only ever scans top-level unary constraints, never one nested inside a {@link
 * io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint}/{@link
 * io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint}'s body, so a {@code reifiedBy}-
 * attributed occurrence of this shape would otherwise get zero incremental propagation from its
 * reification wrapper -- the identical gap {@code GroundRelationRecognizer}'s own Javadoc
 * documents for {@code le(N,VAR)}, closed here the same way.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UnaryInSetConstraint<T> extends UnaryConstraint<T> implements Propagatable {
    @NonNull Set<T> values;
    boolean positive;

    public static <T> UnaryInSetConstraint<T> of(
            @NonNull Variable<T> variable, @NonNull Set<T> values, boolean positive) {
        return UnaryInSetConstraint.<T>builder().variable(variable).values(values).positive(positive).build();
    }

    @Override
    protected boolean checkValue(@NonNull T v) {
        return values.contains(v) == positive;
    }

    @Override
    public String getRelation() {
        return getVariable() + (positive ? " in " : " notin ") + values;
    }

    /**
     * Casts straight to {@link DiscreteDomain} with no defensive {@code instanceof} check: unlike
     * {@link UnaryComparatorConstraint} (whitelisted for both discrete and {@link
     * io.github.rcrida.jcsp.domains.BoundedDomain} variables), this class is deliberately absent
     * from {@code ConstraintSatisfactionProblem}'s {@code CONTINUOUS_COMPATIBLE_CONSTRAINTS}
     * whitelist -- membership in a literal integer value set has no continuous analogue -- so {@code
     * validateCompatibility} rejects attaching it to a {@link
     * io.github.rcrida.jcsp.domains.BoundedDomain} variable at CSP-build time, making a non-discrete
     * domain here genuinely unreachable for any validly-built {@link
     * io.github.rcrida.jcsp.ConstraintSatisfactionProblem} (per ADR-0006).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<T> discrete = (DiscreteDomain<T>) domains.get(getVariable());
        DiscreteDomain.Builder<T> narrowing = null;
        for (T v : discrete.toList()) {
            if (!checkValue(v)) {
                if (narrowing == null) narrowing = discrete.toBuilder();
                narrowing.delete(v);
            }
        }
        if (narrowing == null) return Optional.of(Map.of());
        DiscreteDomain<T> narrowed = narrowing.build();
        return narrowed.isEmpty() ? Optional.empty() : Optional.of(Map.of(getVariable(), narrowed));
    }

    /**
     * A value-set membership infeasibility is generally scattered, not a contiguous range, so
     * {@link ValueSetNogoodConstraint#fromCurrentState} (citing the exact current value set) is the
     * natural explanation; {@link RangeNogoodConstraint#fromCurrentBounds} is tried first only
     * because it's strictly cheaper and still sound whenever the current domain happens to be
     * gapless, the same two-tier fallback {@link UnaryComparatorConstraint#explainInfeasible} uses.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }
}
