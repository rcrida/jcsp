package io.github.rcrida.jcsp.constraints.nary;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Constraint;
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
 * Half-reification (implication): {@code indicator -> body}.
 * When the indicator is {@code true} the body constraint must be satisfied;
 * when the indicator is {@code false} the body is unconstrained.
 *
 * <p>Useful for soft constraints, activation patterns, and conditional constraints.
 *
 * <p>Implements {@link Propagatable} for the same reason as {@link ReifiedConstraint} (see that
 * class's Javadoc): without it, an n-ary body got zero propagation at all — not even AC3, since
 * unlike {@code ReifiedConstraint} this constraint has no {@code BinaryDecomposable} fallback for
 * any body shape, not even a unary one — and was only ever caught by
 * {@link io.github.rcrida.jcsp.assignments.Assignment#isConsistent}'s direct {@code isSatisfiedBy}
 * check. Only one direction is propagated, unlike full reification: {@code indicator -> body}
 * never lets a satisfied/violated body force {@code indicator} true, since {@code indicator}
 * false leaves {@code body} entirely unconstrained.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ImplicationConstraint extends NaryConstraint implements Propagatable {
    @NonNull private final Variable<Boolean> indicator;
    @Getter @NonNull private final Constraint body;

    public static ImplicationConstraint of(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
        Set<Variable<?>> vars = new HashSet<>(body.getVariables());
        vars.add(indicator);
        return ImplicationConstraint.builder().variables(vars).indicator(indicator).body(body).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment a) {
        return a.getValue(indicator)
                .map(b -> !b || body.isSatisfiedBy(a))
                .orElse(true);
    }

    @Override
    public String getRelation() {
        return indicator + " -> (" + body.getRelation() + ")";
    }

    /**
     * Propagates {@code indicator -> body}:
     * <ul>
     *   <li><b>indicator forced true</b>: body must hold, so if {@code body} is itself
     *       {@link Propagatable} its own {@code propagate} is delegated to directly; otherwise, if
     *       every body variable is already singleton, the fully-determined assignment is checked
     *       directly via {@link Constraint#isSatisfiedBy}.</li>
     *   <li><b>indicator forced false</b>: body is entirely unconstrained, so no propagation
     *       happens.</li>
     *   <li><b>indicator still open</b>: forced {@code false} once {@code body} is proven
     *       unsatisfiable under the current (possibly partial) domains — either a fully-determined
     *       body found unsatisfied, or a {@link Propagatable} body reporting itself infeasible —
     *       sound because {@code indicator} being {@code true} would then require an impossible
     *       body. There is no symmetric "force true" case: unlike {@link ReifiedConstraint}, a
     *       satisfied or necessary body never forces {@code indicator}, since {@code indicator}
     *       false is always a valid escape.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<Boolean> indicatorDomain = (DiscreteDomain<Boolean>) domains.get(indicator);

        if (indicatorDomain.isSingleton()) {
            if (!indicatorDomain.singleValue().orElseThrow()) return Optional.of(Map.of());
            if (body instanceof Propagatable propagatableBody) {
                return propagatableBody.propagate(domains);
            }
            if (bodyFullyDetermined(domains) && !bodySatisfied(domains)) return Optional.empty();
            return Optional.of(Map.of());
        }

        boolean bodyProvenUnsatisfiable = bodyFullyDetermined(domains)
                ? !bodySatisfied(domains)
                : body instanceof Propagatable propagatableBody && propagatableBody.propagate(domains).isEmpty();
        if (bodyProvenUnsatisfiable) {
            return Optional.of(Map.of(indicator, forceIndicatorFalse(indicatorDomain)));
        }
        return Optional.of(Map.of());
    }

    /**
     * The only infeasibility {@link #propagate} can report is indicator forced {@code true} with a
     * fully-determined, unsatisfied body — collectively explained by every body variable's
     * singleton value plus {@code indicator = true}, mirroring {@link Propagatable#allSingletonReason}.
     * The {@link Propagatable}-body-infeasible-while-partially-open case is left unexplained
     * ({@link Optional#empty()}) for the same reason as {@link ReifiedConstraint#explainInfeasible}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<Boolean> indicatorDomain = (DiscreteDomain<Boolean>) domains.get(indicator);
        if (!indicatorDomain.isSingleton() || !indicatorDomain.singleValue().orElseThrow()) return Optional.empty();
        if (!bodyFullyDetermined(domains)) return Optional.empty();

        Map<Variable<?>, Object> reason = new HashMap<>(Propagatable.allSingletonReason(body.getVariables(), domains));
        reason.put(indicator, true);
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

    private static Domain<Boolean> forceIndicatorFalse(DiscreteDomain<Boolean> domain) {
        return domain.toBuilder().delete(Boolean.TRUE).build();
    }
}
