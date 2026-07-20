package io.github.rcrida.jcsp.constraints.nary;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryReifiedUnaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Full reification: {@code indicator <-> body}.
 * The indicator variable is {@code true} exactly when the body constraint is satisfied.
 *
 * <p>For partial assignments the constraint is satisfied optimistically — a definitive
 * check only applies once all body variables are assigned.
 *
 * <p>When the body is a {@link UnaryConstraint}, a binary decomposition is available
 * for AC3 arc propagation between the indicator and the body's variable. {@link Propagatable}
 * (below) additionally covers the n-ary case that decomposition can't reach: without it, an
 * n-ary body (e.g. {@code b <-> AllDiff(...)}) got zero propagation at all — not even AC3 — and
 * was only ever caught by {@link io.github.rcrida.jcsp.assignments.Assignment#isConsistent}'s
 * direct {@code isSatisfiedBy} check, the same class of gap fixed for
 * {@link AtMostOneConstraint}/{@link ExactlyOneConstraint} (see that class's Javadoc).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ReifiedConstraint extends NaryConstraint implements BinaryDecomposable, Propagatable {
    @NonNull private final Variable<Boolean> indicator;
    @Getter @NonNull private final Constraint body;

    public static ReifiedConstraint of(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
        Set<Variable<?>> vars = new HashSet<>(body.getVariables());
        vars.add(indicator);
        return ReifiedConstraint.builder().variables(vars).indicator(indicator).body(body).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment a) {
        Optional<Boolean> indValue = a.getValue(indicator);
        if (indValue.isEmpty()) return true;

        boolean allBodyVarsAssigned = body.getVariables().stream().allMatch(v -> a.getValue(v).isPresent());
        if (!allBodyVarsAssigned) return true;

        return indValue.get() == body.isSatisfiedBy(a);
    }

    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        if (body instanceof UnaryConstraint<?> unary) {
            return Set.of(asBinary(unary));
        }
        return Set.of();
    }

    private <T> BinaryReifiedUnaryConstraint<T> asBinary(UnaryConstraint<T> unary) {
        return BinaryReifiedUnaryConstraint.<T>builder()
                .left(indicator)
                .right(unary.getVariable())
                .body(unary)
                .build();
    }

    @Override
    public String getRelation() {
        return indicator + " <-> (" + body.getRelation() + ")";
    }

    /**
     * Propagates both directions of {@code indicator <-> body}:
     * <ul>
     *   <li><b>indicator forced true</b>: body must hold, so if {@code body} is itself
     *       {@link Propagatable} its own {@code propagate} is delegated to directly (infeasible
     *       there means infeasible here too); otherwise, if every body variable is already
     *       singleton, the fully-determined assignment is checked directly via
     *       {@link Constraint#isSatisfiedBy}.</li>
     *   <li><b>indicator forced false</b>: body must not hold. There is no generic way to
     *       propagate the negation of an arbitrary constraint, so the only case handled is a
     *       fully-determined body found satisfied — a direct contradiction.</li>
     *   <li><b>indicator still open</b>: forced to {@code body}'s value once every body variable
     *       is singleton (the reverse link the unary-only {@link BinaryDecomposable} decomposition
     *       can't express for n-ary bodies), or forced {@code false} if a {@link Propagatable} body
     *       already reports itself infeasible under the current (possibly partial) domains — sound
     *       because that means no completion of the current domains satisfies {@code body}, so
     *       {@code indicator} can't be {@code true}.</li>
     * </ul>
     * The "body proven necessary" case (indicator open, body not yet singleton but provably always
     * satisfied) has no generic detection and is left untouched, same principle as the "indicator
     * forced false" case above.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<Boolean> indicatorDomain = (DiscreteDomain<Boolean>) domains.get(indicator);
        boolean bodyFullyDetermined = bodyFullyDetermined(domains);

        if (indicatorDomain.isSingleton()) {
            boolean indicatorTrue = indicatorDomain.singleValue().orElseThrow();
            if (indicatorTrue) {
                if (body instanceof Propagatable propagatableBody) {
                    return propagatableBody.propagate(domains);
                }
                if (bodyFullyDetermined && !bodySatisfied(domains)) return Optional.empty();
            } else if (bodyFullyDetermined && bodySatisfied(domains)) {
                return Optional.empty();
            }
            return Optional.of(Map.of());
        }

        if (bodyFullyDetermined) {
            return Optional.of(Map.of(indicator, forceIndicator(indicatorDomain, bodySatisfied(domains))));
        }
        if (body instanceof Propagatable propagatableBody && propagatableBody.propagate(domains).isEmpty()) {
            return Optional.of(Map.of(indicator, forceIndicator(indicatorDomain, false)));
        }
        return Optional.of(Map.of());
    }

    /**
     * Only the two fully-determined-body infeasibility cases are explained precisely (indicator
     * plus every body variable's singleton value — collectively sufficient, mirroring
     * {@link Propagatable#allSingletonReason}'s reasoning). The case where a {@link Propagatable}
     * body reports infeasible while still partially open is left unexplained ({@link Optional#empty()}):
     * combining that reason with {@code indicator}'s own forced value would require reaching into
     * an arbitrary {@link NogoodConstraint} shape the body might return, which isn't sound to do
     * generically; the caller falls back to the full assignment as the nogood in that case.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<Boolean> indicatorDomain = (DiscreteDomain<Boolean>) domains.get(indicator);
        if (!indicatorDomain.isSingleton() || !bodyFullyDetermined(domains)) return Optional.empty();

        Map<Variable<?>, Object> reason = new HashMap<>(Propagatable.allSingletonReason(body.getVariables(), domains));
        reason.put(indicator, indicatorDomain.singleValue().orElseThrow());
        return GroundNogoodConstraint.fromReason(reason);
    }

    private boolean bodyFullyDetermined(Map<Variable<?>, Domain<?>> domains) {
        return body.getVariables().stream().allMatch(v -> domains.get(v).isSingleton());
    }

    private boolean bodySatisfied(Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> values = new HashMap<>();
        for (Variable<?> v : body.getVariables()) {
            values.put(v, domains.get(v).singleValue().orElseThrow());
        }
        return body.isSatisfiedBy(Assignment.of(values));
    }

    private static Domain<Boolean> forceIndicator(DiscreteDomain<Boolean> domain, boolean value) {
        return domain.toBuilder().delete(!value).build();
    }
}
