package io.github.rcrida.jcsp.constraints.nary;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryReifiedUnaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
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
 * for AC3 arc propagation between the indicator and the body's variable.
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ReifiedConstraint extends NaryConstraint {
    @NonNull Variable<Boolean> indicator;
    @NonNull Constraint body;

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
    public Optional<Set<BinaryConstraint<?, ?>>> getAsBinaryConstraints() {
        if (body instanceof UnaryConstraint<?> unary) {
            return Optional.of(Set.of(asBinary(unary)));
        }
        return Optional.empty();
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
        return indicator + " <-> (" + body + ")";
    }
}
