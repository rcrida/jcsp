package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code OR(v1 == target1, v2 == target2, ..., vn == targetn)}: satisfied when at least one
 * variable equals its own designated target value. Generalizes {@link AtLeastNConstraint}'s
 * {@code n=1} case from a uniform "is true" check over {@code Variable<Boolean>} to a per-variable
 * target value over any {@code T} -- built specifically so an XCSP3 SAT clause's positive and
 * negative literals over the <em>same</em> underlying variable ({@code x[i] == 1} / {@code x[i] ==
 * 0}) can cite that variable directly, with no bridging boolean indicator variable or reification
 * constraint per literal. See {@link
 * io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CallbackHandler#buildCtrClause}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ValueDisjunctionConstraint<T> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Map<Variable<T>, T> literals;

    public static <T> ValueDisjunctionConstraint<T> of(@NonNull Map<Variable<T>, T> literals) {
        assert !literals.isEmpty() : "ValueDisjunctionConstraint requires at least one literal";
        return ValueDisjunctionConstraint.<T>builder()
                .variables(Set.copyOf(literals.keySet()))
                .literals(Map.copyOf(literals))
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        boolean allAssigned = true;
        for (Map.Entry<Variable<T>, T> literal : literals.entrySet()) {
            Optional<T> value = assignment.getValue(literal.getKey());
            if (value.isEmpty()) {
                allAssigned = false;
                continue;
            }
            if (value.get().equals(literal.getValue())) return true;
        }
        return !allAssigned;
    }

    /**
     * Unit propagation over {@code OR(v1 == target1, ..., vn == targetn)}: a literal whose
     * variable's domain no longer contains its own target can never be satisfied and is dropped
     * from consideration; if every literal is dropped, the constraint is infeasible; if exactly one
     * remains possible and none is already definitely satisfied, it's forced -- that variable's
     * domain is narrowed to the singleton {@code {target}}. Mirrors {@link
     * AtLeastNConstraint#propagate}'s {@code n=1} shape, generalized off a uniform {@code TRUE}
     * check to a per-literal target value.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Map.Entry<Variable<T>, T>> possible = new ArrayList<>();
        for (Map.Entry<Variable<T>, T> literal : literals.entrySet()) {
            Domain<T> domain = (Domain<T>) domains.get(literal.getKey());
            if (!domain.contains(literal.getValue())) continue;
            if (domain.isSingleton()) return Optional.of(Map.of()); // this literal is guaranteed true
            possible.add(literal);
        }
        if (possible.isEmpty()) return Optional.empty();
        if (possible.size() > 1) return Optional.of(Map.of());

        Map.Entry<Variable<T>, T> only = possible.get(0);
        DiscreteDomain<T> domain = (DiscreteDomain<T>) domains.get(only.getKey());
        DiscreteDomain.Builder<T> narrowed = domain.toBuilder();
        for (T value : domain.toList()) {
            if (!value.equals(only.getValue())) narrowed.delete(value);
        }
        return Optional.of(Map.of(only.getKey(), narrowed.build()));
    }

    /**
     * Reached only when every literal's domain currently excludes its own target -- rather than
     * re-deriving a per-literal forced value the way {@link AtLeastNConstraint#explainInfeasible}
     * safely can (a {@code Variable<Boolean>} domain excluding {@code TRUE} is always singleton
     * {@code FALSE}; an arbitrary {@code T} domain excluding one target value need not be
     * singleton), this delegates to {@link ValueSetNogoodConstraint#fromCurrentState}'s already-
     * established generic, always-sound "cite this constraint's exact current domain state" pattern.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains);
    }

    @Override
    public String getRelation() {
        return literals.entrySet().stream()
                .map(e -> e.getKey() + " == " + e.getValue())
                .sorted()
                .collect(Collectors.joining(" OR "));
    }
}
