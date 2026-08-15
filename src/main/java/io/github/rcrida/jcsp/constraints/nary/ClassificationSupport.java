package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The definite/possible/impossible three-way partition shared by {@link CountConstraint}/{@link
 * CountVariableConstraint} (predicate: domain value equals a fixed target) and {@link
 * AmongConstraint}/{@link AmongVariableConstraint} (predicate: domain value is in a target set) —
 * the same classification once phrased generically over a match predicate: a variable is
 * <em>definite</em> when {@code matches} holds for every value still in its domain, <em>possible</em>
 * when it holds for some but not all, and <em>impossible</em> when it holds for none. For a
 * single-value predicate ({@code value::equals}) "holds for every value" coincides exactly with the
 * domain being the singleton {@code {value}}: the {@code anyMatch} guard below already establishes
 * {@code value} is present, so also requiring {@code allMatch} forces the domain to contain nothing
 * else — which is exactly {@link CountConstraint}/{@link CountVariableConstraint}'s own singleton
 * check, just derived rather than tested directly.
 */
final class ClassificationSupport {
    private ClassificationSupport() {}

    record Classification<T>(List<Variable<T>> definite, List<Variable<T>> possible, List<Variable<?>> impossible) {}

    @SuppressWarnings("unchecked")
    static <T> Classification<T> classify(Collection<? extends Variable<?>> variables, Predicate<T> matches,
                                           Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> definite = new ArrayList<>();
        List<Variable<T>> possible = new ArrayList<>();
        List<Variable<?>> impossible = new ArrayList<>();
        for (Variable<?> var : variables) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
            if (!dom.stream().anyMatch(matches)) {
                impossible.add(var);
                continue;
            }
            if (dom.stream().allMatch(matches)) definite.add((Variable<T>) var); else possible.add((Variable<T>) var);
        }
        return new Classification<>(definite, possible, impossible);
    }
}
