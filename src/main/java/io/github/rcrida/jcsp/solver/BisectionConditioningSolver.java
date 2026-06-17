package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Handles {@link BoundedDomain} variables that do not collapse to singletons during propagation
 * by recursively bisecting the widest non-singleton interval and re-propagating until all
 * intervals are singleton or within epsilon.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BisectionConditioningSolver extends SolverDecorator {

    private static final List<ConstraintConsistency> REPROPAGATORS = List.of(
            FixpointConsistency.of(SumConstraint.class),
            FixpointConsistency.of(LinearConstraint.class)
    );

    double epsilon;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull ToDoubleFunction<Assignment> objective) {
        double[] incumbent = {Double.MAX_VALUE};
        return getSolutions(csp).filter(candidate -> {
            double cost = objective.applyAsDouble(candidate);
            if (cost < incumbent[0]) {
                incumbent[0] = cost;
                return true;
            }
            return false;
        });
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        val target = findWidestBounded(csp);
        if (target == null) {
            return getInner().getSolutions(csp);
        }
        val bd = (BoundedDomain<?>) csp.getDomain(target);
        double lo = bd.getMin().doubleValue();
        double hi = bd.getMax().doubleValue();
        double mid = (lo + hi) / 2.0;
        if (hi - lo <= epsilon) {
            log.debug("Snapping {} to {}", target, mid);
            return getSolutions(withSnapped(csp, target, mid));
        }
        log.debug("Bisecting {} at {} in [{}, {}]", target, mid, lo, hi);
        return Stream.concat(
                narrow(csp, target, lo, mid).stream().flatMap(this::getSolutions),
                narrow(csp, target, mid, hi).stream().flatMap(this::getSolutions)
        );
    }

    @Nullable
    private static Variable<?> findWidestBounded(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> e.getValue() instanceof BoundedDomain<?> bd && !bd.isSingleton())
                .max(Comparator.comparingDouble(e ->
                        ((BoundedDomain<?>) e.getValue()).getMax().doubleValue()
                        - ((BoundedDomain<?>) e.getValue()).getMin().doubleValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static ConstraintSatisfactionProblem withSnapped(ConstraintSatisfactionProblem csp,
                                                              Variable<?> target, double mid) {
        return csp.toBuilder()
                .variableDomainEntry((Variable) target, IntervalDomain.of(mid, mid))
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<ConstraintSatisfactionProblem> narrow(ConstraintSatisfactionProblem csp,
                                                                   Variable<?> target,
                                                                   double lo, double hi) {
        BoundedDomain raw = (BoundedDomain) csp.getDomain(target);
        var narrowedDomain = raw.withBounds(lo, hi);
        return repropagate(csp.toBuilder()
                .variableDomainEntry((Variable) target, narrowedDomain)
                .build());
    }

    private static Optional<ConstraintSatisfactionProblem> repropagate(ConstraintSatisfactionProblem csp) {
        var current = csp;
        boolean changed = true;
        while (changed) {
            double widthBefore = boundedWidth(current);
            for (var propagator : REPROPAGATORS) {
                var next = propagator.apply(current);
                if (next.isEmpty()) return Optional.empty();
                current = next.get();
            }
            changed = boundedWidth(current) < widthBefore;
        }
        return Optional.of(current);
    }

    private static double boundedWidth(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().values().stream()
                .filter(BoundedDomain.class::isInstance)
                .mapToDouble(d -> {
                    BoundedDomain<?> bd = (BoundedDomain<?>) d;
                    return bd.getMax().doubleValue() - bd.getMin().doubleValue();
                })
                .sum();
    }
}
