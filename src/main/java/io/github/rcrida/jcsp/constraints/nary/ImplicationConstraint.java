package io.github.rcrida.jcsp.constraints.nary;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Half-reification (implication): {@code indicator -> body}.
 * When the indicator is {@code true} the body constraint must be satisfied;
 * when the indicator is {@code false} the body is unconstrained.
 *
 * <p>Useful for soft constraints, activation patterns, and conditional constraints.
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ImplicationConstraint extends NaryConstraint {
    @NonNull Variable<Boolean> indicator;
    @NonNull Constraint body;

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
        return indicator + " -> (" + body + ")";
    }
}
